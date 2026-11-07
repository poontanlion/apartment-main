import type { Room, UtilityBill } from '../types';

export type UsageMetric = 'water' | 'electric';
export type UsageGroupBy = 'room' | 'floor';
export type UsagePeriod = 'month' | 'year';

export interface UsagePoint {
  key: string;
  label: string;
  value: number;
}

export const MONTH_NAMES_EN = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

export const MONTH_SHORT_EN = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

export interface ParsedBillingMonth {
  year: number;
  monthIndex: number; // 0 = January
}

/** Reads a billing month in the server's format, such as "August 2026". */
export const parseBillingMonth = (value?: string | null): ParsedBillingMonth | null => {
  if (!value) return null;
  const [name, yearText] = value.trim().split(/\s+/);
  const monthIndex = MONTH_NAMES_EN.findIndex(m => m.toLowerCase() === (name || '').toLowerCase());
  const year = Number(yearText);
  if (monthIndex < 0 || !Number.isInteger(year)) return null;
  return { year, monthIndex };
};

/** Units used on one bill: the current meter reading minus the previous one, never below zero. */
export const billUnits = (bill: UtilityBill, metric: UsageMetric): number => {
  const curr = metric === 'water' ? bill.currWaterMeter : bill.currElectricMeter;
  const prev = metric === 'water' ? bill.prevWaterMeter : bill.prevElectricMeter;
  return Math.max(0, (Number(curr) || 0) - (Number(prev) || 0));
};

/** The years that have at least one bill, oldest first. */
export const billingYears = (bills: UtilityBill[]): number[] => {
  const years = new Set<number>();
  for (const bill of bills) {
    const parsed = parseBillingMonth(bill.billingMonth);
    if (parsed) years.add(parsed.year);
  }
  return [...years].sort((a, b) => a - b);
};

const round = (value: number) => Math.round(value * 100) / 100;

const findRoom = (bill: UtilityBill, rooms: Room[]): Room | undefined =>
  rooms.find(r => r.id === bill.roomId) || rooms.find(r => r.roomNumber === bill.roomNumber);

const inBuilding = (room: Room | undefined, buildingId: string): boolean =>
  buildingId === 'All' || room?.buildingId === buildingId;

export interface UsageByGroupOptions {
  metric: UsageMetric;
  groupBy: UsageGroupBy;
  year: number;
  /** 0 to 11 for one month, or 'all' for the whole year. */
  monthIndex: number | 'all';
  buildingId: string;
}

/** Total units per room or per floor within the chosen month or year. */
export const usageByGroup = (bills: UtilityBill[], rooms: Room[], options: UsageByGroupOptions): UsagePoint[] => {
  const totals = new Map<string, { label: string; sort: number | string; value: number }>();

  for (const bill of bills) {
    const parsed = parseBillingMonth(bill.billingMonth);
    if (!parsed || parsed.year !== options.year) continue;
    if (options.monthIndex !== 'all' && parsed.monthIndex !== options.monthIndex) continue;

    const room = findRoom(bill, rooms);
    if (!inBuilding(room, options.buildingId)) continue;

    let key: string;
    let label: string;
    let sort: number | string;
    if (options.groupBy === 'floor') {
      if (room?.floor === undefined || room.floor === null) continue; // the floor is unknown
      key = `floor-${room.floor}`;
      label = String(room.floor);
      sort = room.floor;
    } else {
      key = bill.roomNumber || bill.roomId;
      label = bill.roomNumber || bill.roomId;
      sort = label;
    }

    const entry = totals.get(key) || { label, sort, value: 0 };
    entry.value += billUnits(bill, options.metric);
    totals.set(key, entry);
  }

  return [...totals.entries()]
    .sort(([, a], [, b]) =>
      typeof a.sort === 'number' && typeof b.sort === 'number'
        ? a.sort - b.sort
        : String(a.sort).localeCompare(String(b.sort), undefined, { numeric: true }))
    .map(([key, entry]) => ({ key, label: entry.label, value: round(entry.value) }));
};

export interface UsageByPeriodOptions {
  metric: UsageMetric;
  period: UsagePeriod;
  year: number;
  buildingId: string;
}

/** Total units for every month of one year (twelve points), or for every year that has bills. */
export const usageByPeriod = (bills: UtilityBill[], rooms: Room[], options: UsageByPeriodOptions): UsagePoint[] => {
  const byMonth = new Array<number>(12).fill(0);
  const byYear = new Map<number, number>();

  for (const bill of bills) {
    const parsed = parseBillingMonth(bill.billingMonth);
    if (!parsed) continue;
    if (!inBuilding(findRoom(bill, rooms), options.buildingId)) continue;

    const units = billUnits(bill, options.metric);
    byYear.set(parsed.year, (byYear.get(parsed.year) || 0) + units);
    if (parsed.year === options.year) byMonth[parsed.monthIndex] += units;
  }

  if (options.period === 'month') {
    return byMonth.map((value, i) => ({ key: String(i), label: MONTH_SHORT_EN[i], value: round(value) }));
  }
  return [...byYear.entries()]
    .sort(([a], [b]) => a - b)
    .map(([year, value]) => ({ key: String(year), label: String(year), value: round(value) }));
};
