import type { Room, UtilityBill } from '../types';
import { MONTH_NAMES_EN, parseBillingMonth } from './utilityUsage';

export type CsvErrorCode =
  | 'MISSING_ROOM'
  | 'ROOM_NOT_FOUND'
  | 'BAD_MONTH'
  | 'BAD_NUMBER'
  | 'METER_BACKWARDS'
  | 'NO_PREVIOUS'
  | 'DUPLICATE_BILL'
  | 'DUPLICATE_IN_FILE';

export interface PlannedRow {
  line: number;
  roomNumber: string;
  /** The month in the server's format when it could be read, otherwise the text as typed. */
  billingMonth: string;
  roomId?: string;
  prevWater?: number;
  currWater?: number;
  prevElectric?: number;
  currElectric?: number;
  error?: { code: CsvErrorCode; detail?: string };
}

export type CsvFileError = 'EMPTY' | 'MISSING_COLUMNS';

export interface CsvPlan {
  rows: PlannedRow[];
  fileError?: CsvFileError;
  missingColumns: string[];
}

export const REQUIRED_COLUMNS = ['roomNumber', 'billingMonth', 'currWater', 'currElectric'] as const;

type Field = typeof REQUIRED_COLUMNS[number] | 'prevWater' | 'prevElectric';

const HEADER_ALIASES: Record<string, Field> = {
  roomnumber: 'roomNumber', room: 'roomNumber', unit: 'roomNumber', 'ห้อง': 'roomNumber', 'เลขห้อง': 'roomNumber',
  billingmonth: 'billingMonth', month: 'billingMonth', period: 'billingMonth', 'เดือน': 'billingMonth',
  currwater: 'currWater', currwatermeter: 'currWater', currentwater: 'currWater', water: 'currWater', 'น้ำ': 'currWater',
  currelectric: 'currElectric', currelectricmeter: 'currElectric', currentelectric: 'currElectric',
  electric: 'currElectric', electricity: 'currElectric', 'ไฟ': 'currElectric',
  prevwater: 'prevWater', prevwatermeter: 'prevWater', previouswater: 'prevWater',
  prevelectric: 'prevElectric', prevelectricmeter: 'prevElectric', previouselectric: 'prevElectric',
};

const normalizeHeader = (text: string) => text.trim().toLowerCase().replace(/[\s_\-]/g, '');

/** Splits CSV text into rows of cells. Handles quotes, a BOM, CRLF, and comma, semicolon or tab separators. */
export const parseCsv = (input: string): string[][] => {
  const text = input.replace(/^\uFEFF/, '');
  const firstLine = text.split(/\r?\n/, 1)[0] || '';
  const delimiter = [',', ';', '\t']
    .map(d => ({ d, count: firstLine.split(d).length - 1 }))
    .sort((a, b) => b.count - a.count)[0];
  const sep = delimiter.count > 0 ? delimiter.d : ',';

  const rows: string[][] = [];
  let row: string[] = [];
  let cell = '';
  let quoted = false;

  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (quoted) {
      if (ch === '"' && text[i + 1] === '"') { cell += '"'; i++; }
      else if (ch === '"') quoted = false;
      else cell += ch;
    } else if (ch === '"') {
      quoted = true;
    } else if (ch === sep) {
      row.push(cell.trim()); cell = '';
    } else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && text[i + 1] === '\n') i++;
      row.push(cell.trim()); rows.push(row); row = []; cell = '';
    } else {
      cell += ch;
    }
  }
  if (cell !== '' || row.length > 0) { row.push(cell.trim()); rows.push(row); }
  return rows;
};

const monthKey = (year: number, monthIndex: number) => year * 12 + monthIndex;

/** Turns "Aug 2026", "2026-08", "8/2026" or "August 2026" into the server's "August 2026". */
export const normalizeBillingMonth = (raw: string): string | null => {
  const text = raw.trim();
  if (!text) return null;

  const full = parseBillingMonth(text);
  let year: number | null = full?.year ?? null;
  let monthIndex: number | null = full?.monthIndex ?? null;

  if (year === null) {
    let m = text.match(/^([A-Za-z]{3,9})\.?\s+(\d{4})$/);
    if (m) {
      const idx = MONTH_NAMES_EN.findIndex(name => name.toLowerCase().startsWith(m![1].slice(0, 3).toLowerCase()));
      if (idx >= 0) { monthIndex = idx; year = Number(m[2]); }
    }
    m = text.match(/^(\d{4})-(\d{1,2})$/);
    if (m) { year = Number(m[1]); monthIndex = Number(m[2]) - 1; }
    m = text.match(/^(\d{1,2})\/(\d{4})$/);
    if (m) { year = Number(m[2]); monthIndex = Number(m[1]) - 1; }
  }

  if (year === null || monthIndex === null || monthIndex < 0 || monthIndex > 11 || year < 2000 || year > 2100) return null;
  return `${MONTH_NAMES_EN[monthIndex]} ${year}`;
};

/** An invoice number in the style of the sample data, such as INV-202608-A101. */
export const invoiceNoFor = (billingMonth: string, roomNumber: string): string => {
  const parsed = parseBillingMonth(billingMonth);
  if (!parsed) return '';
  return `INV-${parsed.year}${String(parsed.monthIndex + 1).padStart(2, '0')}-${roomNumber.toUpperCase()}`;
};

interface MeterRead { monthKey: number; water: number; electric: number }

/** Empty text is "not given". Anything that is not a number of zero or more is a bad number. */
const readMeter = (text: string): { value?: number; bad?: true } => {
  const cleaned = text.replace(/[\s,]/g, '');
  if (cleaned === '') return {};
  const value = Number(cleaned);
  return Number.isFinite(value) && value >= 0 ? { value } : { bad: true };
};

/**
 * Checks every row of a meter-reading CSV against the rooms and the bills that already exist.
 * A missing previous reading is filled from the room's latest earlier bill, in the file or already saved.
 */
export const buildImportPlan = (csvText: string, rooms: Room[], existingBills: UtilityBill[]): CsvPlan => {
  const table = parseCsv(csvText);
  if (table.length < 2) return { rows: [], fileError: 'EMPTY', missingColumns: [] };

  const header = table[0].map(h => HEADER_ALIASES[normalizeHeader(h)]);
  const col = (field: Field) => header.indexOf(field);
  const missingColumns = REQUIRED_COLUMNS.filter(f => col(f) < 0);
  if (missingColumns.length > 0) return { rows: [], fileError: 'MISSING_COLUMNS', missingColumns };

  const cell = (cells: string[], field: Field) => (col(field) >= 0 ? cells[col(field)] || '' : '');

  // Bills already saved, by room.
  const reads = new Map<string, MeterRead[]>();
  const saved = new Set<string>(); // room and month of bills that already exist
  const accepted = new Set<string>(); // room and month of rows in this file that passed
  const addRead = (roomId: string, read: MeterRead) => {
    reads.set(roomId, [...(reads.get(roomId) || []), read]);
  };
  for (const bill of existingBills) {
    const parsed = parseBillingMonth(bill.billingMonth);
    if (!parsed) continue;
    const room = rooms.find(r => r.id === bill.roomId) || rooms.find(r => r.roomNumber === bill.roomNumber);
    const roomId = room?.id || bill.roomId;
    const key = monthKey(parsed.year, parsed.monthIndex);
    saved.add(`${roomId}|${key}`);
    addRead(roomId, { monthKey: key, water: Number(bill.currWaterMeter) || 0, electric: Number(bill.currElectricMeter) || 0 });
  }

  interface Draft { row: PlannedRow; key?: number; given: { prevWater?: number; prevElectric?: number } }
  const drafts: Draft[] = [];

  // First pass: read and check each row on its own.
  table.slice(1).forEach((cells, i) => {
    if (cells.every(c => c === '')) return;
    const roomNumber = cell(cells, 'roomNumber');
    const monthText = cell(cells, 'billingMonth');
    const row: PlannedRow = { line: i + 2, roomNumber, billingMonth: monthText };
    const draft: Draft = { row, given: {} };
    drafts.push(draft);

    if (!roomNumber) { row.error = { code: 'MISSING_ROOM' }; return; }
    const room = rooms.find(r => r.roomNumber.toLowerCase() === roomNumber.toLowerCase());
    if (!room) { row.error = { code: 'ROOM_NOT_FOUND' }; return; }
    row.roomId = room.id;
    row.roomNumber = room.roomNumber;

    const month = normalizeBillingMonth(monthText);
    if (!month) { row.error = { code: 'BAD_MONTH' }; return; }
    row.billingMonth = month;
    const parsed = parseBillingMonth(month)!;
    draft.key = monthKey(parsed.year, parsed.monthIndex);

    const fields: [Field, string][] = [
      ['currWater', 'currWater'], ['currElectric', 'currElectric'], ['prevWater', 'prevWater'], ['prevElectric', 'prevElectric'],
    ];
    for (const [field, name] of fields) {
      const { value, bad } = readMeter(cell(cells, field));
      if (bad) { row.error = { code: 'BAD_NUMBER', detail: name }; return; }
      if (value === undefined && (field === 'currWater' || field === 'currElectric')) {
        row.error = { code: 'BAD_NUMBER', detail: name }; return;
      }
      if (field === 'currWater') row.currWater = value;
      else if (field === 'currElectric') row.currElectric = value;
      else draft.given[field === 'prevWater' ? 'prevWater' : 'prevElectric'] = value;
    }
  });

  // Second pass, oldest month first, so an earlier row can supply the next row's previous reading.
  const ordered = drafts
    .filter(d => !d.row.error && d.key !== undefined)
    .sort((a, b) => (a.row.roomId! < b.row.roomId! ? -1 : a.row.roomId! > b.row.roomId! ? 1 : 0) || a.key! - b.key! || a.row.line - b.row.line);

  for (const { row, key, given } of ordered) {
    const slot = `${row.roomId}|${key}`;
    if (saved.has(slot)) { row.error = { code: 'DUPLICATE_BILL' }; continue; }
    if (accepted.has(slot)) { row.error = { code: 'DUPLICATE_IN_FILE' }; continue; }

    const earlier = (reads.get(row.roomId!) || []).filter(r => r.monthKey < key!);
    const latest = earlier.sort((a, b) => b.monthKey - a.monthKey)[0];

    const prevWater = given.prevWater ?? latest?.water;
    const prevElectric = given.prevElectric ?? latest?.electric;
    if (prevWater === undefined) { row.error = { code: 'NO_PREVIOUS', detail: 'water' }; continue; }
    if (prevElectric === undefined) { row.error = { code: 'NO_PREVIOUS', detail: 'electric' }; continue; }
    if (row.currWater! < prevWater) { row.error = { code: 'METER_BACKWARDS', detail: 'water' }; continue; }
    if (row.currElectric! < prevElectric) { row.error = { code: 'METER_BACKWARDS', detail: 'electric' }; continue; }

    row.prevWater = prevWater;
    row.prevElectric = prevElectric;
    accepted.add(slot);
    addRead(row.roomId!, { monthKey: key!, water: row.currWater!, electric: row.currElectric! });
  }

  return { rows: drafts.map(d => d.row), missingColumns: [] };
};
