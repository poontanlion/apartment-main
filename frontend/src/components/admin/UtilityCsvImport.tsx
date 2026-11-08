import React, { useRef, useState } from 'react';
import { Download, FileSpreadsheet, Upload, X } from 'lucide-react';
import { toast } from 'sonner';
import { Room, UtilityBill } from '../../types';
import { getLeases, getRentQuote, saveUtilityBill } from '../../services/api';
import { useLanguage } from '../../context/LanguageContext';
import { currentBillingMonth } from '../../utils/billingMonth';
import { CsvPlan, PlannedRow, buildImportPlan, invoiceNoFor } from '../../utils/utilityCsv';

// The same defaults as the New Bill form and the server.
const WATER_RATE = 9;
const ELECTRIC_RATE = 4;
const COMMON_FEE = 300;

const FIELD_LABEL: Record<string, [string, string]> = {
  currWater: ['เลขมิเตอร์น้ำปัจจุบัน', 'current water reading'],
  currElectric: ['เลขมิเตอร์ไฟปัจจุบัน', 'current electric reading'],
  prevWater: ['เลขมิเตอร์น้ำก่อนหน้า', 'previous water reading'],
  prevElectric: ['เลขมิเตอร์ไฟก่อนหน้า', 'previous electric reading'],
  water: ['น้ำ', 'water'],
  electric: ['ไฟ', 'electric'],
};

const describeError = (error: NonNullable<PlannedRow['error']>, isThai: boolean): string => {
  const label = FIELD_LABEL[error.detail || '']?.[isThai ? 0 : 1] || error.detail || '';
  switch (error.code) {
    case 'MISSING_ROOM': return isThai ? 'ไม่ได้ระบุเลขห้อง' : 'Room number is missing.';
    case 'ROOM_NOT_FOUND': return isThai ? 'ไม่พบห้องนี้ในระบบ' : 'Room not found.';
    case 'BAD_MONTH': return isThai ? 'อ่านเดือนไม่ได้ (ใช้เช่น August 2026 หรือ 2026-08)' : 'Cannot read the month (use August 2026 or 2026-08).';
    case 'BAD_NUMBER': return isThai ? `${label} ต้องเป็นตัวเลขตั้งแต่ 0 ขึ้นไป` : `${label}: enter a number of 0 or more.`;
    case 'METER_BACKWARDS': return isThai ? `เลขมิเตอร์${label}ปัจจุบันน้อยกว่าเลขก่อนหน้า` : `The current ${label} reading is below the previous one.`;
    case 'NO_PREVIOUS': return isThai ? `ไม่มีเลขมิเตอร์${label}ก่อนหน้า กรุณาใส่ในไฟล์` : `No previous ${label} reading. Add it to the file.`;
    case 'DUPLICATE_BILL': return isThai ? 'ห้องนี้มีบิลของเดือนนี้อยู่แล้ว (ข้าม)' : 'This room already has a bill for the month (skipped).';
    case 'DUPLICATE_IN_FILE': return isThai ? 'ห้องและเดือนนี้ซ้ำในไฟล์' : 'This room and month appear twice in the file.';
    default: return error.code;
  }
};

interface ImportResult {
  row: PlannedRow;
  ok: boolean;
  message?: string;
}

interface Props {
  rooms: Room[];
  bills: UtilityBill[];
  onClose: () => void;
  onImported: () => void | Promise<void>;
}

export const UtilityCsvImport: React.FC<Props> = ({ rooms, bills, onClose, onImported }) => {
  const { language } = useLanguage();
  const isThai = language === 'th';
  const fileInput = useRef<HTMLInputElement>(null);

  const [fileName, setFileName] = useState('');
  const [plan, setPlan] = useState<CsvPlan | null>(null);
  const [importing, setImporting] = useState(false);
  const [progress, setProgress] = useState({ done: 0, total: 0 });
  const [results, setResults] = useState<ImportResult[] | null>(null);

  const validRows = plan ? plan.rows.filter(row => !row.error) : [];
  const invalidCount = plan ? plan.rows.length - validRows.length : 0;
  const failed = results ? results.filter(r => !r.ok) : [];

  const handleFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // lets the same file be chosen again after it is fixed
    if (!file) return;
    setFileName(file.name);
    setResults(null);
    setPlan(buildImportPlan(await file.text(), rooms, bills));
  };

  const downloadTemplate = () => {
    const month = currentBillingMonth();
    const sample = rooms.length > 0 ? rooms.slice(0, 3).map(r => r.roomNumber) : ['A101', 'A102', 'A201'];
    const lines = [
      'roomNumber,billingMonth,currWater,currElectric,prevWater,prevElectric',
      ...sample.map((roomNumber, i) => `${roomNumber},${month},${108 + i * 2},${480 + i * 10},100,400`),
    ];
    // The BOM keeps Thai text readable when the file is opened in Excel.
    const blob = new Blob([`\uFEFF${lines.join('\r\n')}\r\n`], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'utility-readings-template.csv';
    link.click();
    URL.revokeObjectURL(url);
  };

  const runImport = async () => {
    if (validRows.length === 0) return;
    setImporting(true);
    setResults(null);
    setProgress({ done: 0, total: validRows.length });

    const leases = await getLeases();
    const out: ImportResult[] = [];

    // One at a time: each bill also moves the room's meter readings on the server.
    for (const row of validRows) {
      try {
        // The server decides the rent, as it does for the New Bill form. Ask it first so the tenant is the lease's.
        const quote = await getRentQuote(row.roomId!, row.billingMonth);
        const lease = leases.find(l => l.id === quote.leaseId);
        const room = rooms.find(r => r.id === row.roomId);
        await saveUtilityBill({
          roomId: row.roomId,
          roomNumber: row.roomNumber,
          tenantName: lease?.tenantName || room?.currentTenantName || 'Tenant',
          billingMonth: row.billingMonth,
          invoiceNo: invoiceNoFor(row.billingMonth, row.roomNumber),
          prevWaterMeter: row.prevWater,
          currWaterMeter: row.currWater,
          prevElectricMeter: row.prevElectric,
          currElectricMeter: row.currElectric,
          waterRate: WATER_RATE,
          electricRate: ELECTRIC_RATE,
          commonFee: COMMON_FEE,
        });
        out.push({ row, ok: true });
      } catch (err) {
        out.push({ row, ok: false, message: (err as Error).message });
      }
      setProgress(p => ({ ...p, done: p.done + 1 }));
    }

    setResults(out);
    setImporting(false);
    const okCount = out.filter(r => r.ok).length;
    if (okCount > 0) {
      toast.success(isThai ? `นำเข้าบิลสำเร็จ ${okCount} รายการ` : `Imported ${okCount} bills`);
      await onImported();
    }
    if (okCount < out.length) {
      toast.error(isThai ? `นำเข้าไม่สำเร็จ ${out.length - okCount} รายการ` : `${out.length - okCount} bills failed`);
    }
  };

  const fileErrorText = plan?.fileError === 'EMPTY'
    ? (isThai ? 'ไฟล์ว่าง หรือไม่มีข้อมูลใต้แถวหัวตาราง' : 'The file is empty or has no rows under the header.')
    : plan?.fileError === 'MISSING_COLUMNS'
      ? (isThai ? `ไม่พบคอลัมน์: ${plan.missingColumns.join(', ')}` : `Missing columns: ${plan.missingColumns.join(', ')}`)
      : '';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card rounded-2xl w-full max-w-3xl max-h-[90vh] overflow-y-auto p-6 space-y-4 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-bold text-nike-ink dark:text-white flex items-center gap-2">
              <FileSpreadsheet className="w-5 h-5 text-emerald-600" />
              {isThai ? 'นำเข้าค่าน้ำ / ไฟ จากไฟล์ CSV' : 'Import water / electricity readings (CSV)'}
            </h3>
            <p className="text-xs text-nike-mute dark:text-nike-stone mt-1">
              {isThai
                ? 'คอลัมน์ที่ต้องมี: roomNumber, billingMonth, currWater, currElectric (เสริม: prevWater, prevElectric) ถ้าไม่ใส่เลขก่อนหน้า ระบบใช้เลขมิเตอร์จากบิลล่าสุดของห้องนั้น ค่าเช่าเซิร์ฟเวอร์คำนวณให้'
                : 'Required columns: roomNumber, billingMonth, currWater, currElectric (optional: prevWater, prevElectric). Without a previous reading, the latest earlier bill of the room is used. The server works out the rent.'}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={importing}
            aria-label={isThai ? 'ปิด' : 'Close'}
            className="p-1.5 rounded-lg text-nike-mute hover:text-nike-ink dark:hover:text-white disabled:opacity-40"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <input ref={fileInput} type="file" accept=".csv,text/csv" onChange={handleFile} className="hidden" />
          <button
            type="button"
            onClick={() => fileInput.current?.click()}
            disabled={importing}
            className="px-4 py-2 text-xs font-semibold rounded-xl bg-emerald-600 text-white hover:bg-emerald-700 transition-all flex items-center gap-2 disabled:opacity-50"
          >
            <Upload className="w-4 h-4" /> {isThai ? 'เลือกไฟล์ CSV' : 'Choose CSV file'}
          </button>
          <button
            type="button"
            onClick={downloadTemplate}
            disabled={importing}
            className="px-4 py-2 text-xs font-semibold rounded-xl bg-nike-soft-cloud dark:bg-nike-dark-surface border border-nike-hairline dark:border-nike-dark-card text-nike-ink dark:text-white flex items-center gap-2 disabled:opacity-50"
          >
            <Download className="w-4 h-4" /> {isThai ? 'ดาวน์โหลดไฟล์ตัวอย่าง' : 'Download template'}
          </button>
          {fileName && <span className="text-xs text-nike-mute">{fileName}</span>}
        </div>

        {fileErrorText && <p className="text-xs text-red-600 dark:text-red-400">{fileErrorText}</p>}

        {plan && !plan.fileError && (
          <div className="space-y-3">
            <p className="text-xs text-nike-ink dark:text-white">
              {isThai
                ? `พร้อมนำเข้า ${validRows.length} แถว · มีปัญหา ${invalidCount} แถว (แถวที่มีปัญหาจะถูกข้าม)`
                : `${validRows.length} rows ready · ${invalidCount} with problems (those are skipped)`}
            </p>

            <div className="overflow-x-auto max-h-72 overflow-y-auto border border-nike-hairline dark:border-nike-dark-card rounded-xl">
              <table className="w-full text-left border-collapse text-xs">
                <thead className="bg-nike-soft-cloud dark:bg-nike-dark-surface text-nike-mute sticky top-0">
                  <tr>
                    <th className="px-3 py-2">{isThai ? 'บรรทัด' : 'Line'}</th>
                    <th className="px-3 py-2">{isThai ? 'ห้อง' : 'Room'}</th>
                    <th className="px-3 py-2">{isThai ? 'เดือน' : 'Month'}</th>
                    <th className="px-3 py-2">{isThai ? 'น้ำ (ก่อน→ปัจจุบัน)' : 'Water (prev→curr)'}</th>
                    <th className="px-3 py-2">{isThai ? 'ไฟ (ก่อน→ปัจจุบัน)' : 'Electric (prev→curr)'}</th>
                    <th className="px-3 py-2">{isThai ? 'สถานะ' : 'Status'}</th>
                  </tr>
                </thead>
                <tbody>
                  {plan.rows.map(row => (
                    <tr key={row.line} className="border-t border-nike-hairline dark:border-nike-dark-card text-nike-ink dark:text-white">
                      <td className="px-3 py-2">{row.line}</td>
                      <td className="px-3 py-2 font-semibold">{row.roomNumber}</td>
                      <td className="px-3 py-2">{row.billingMonth}</td>
                      <td className="px-3 py-2">{row.prevWater !== undefined ? `${row.prevWater} → ${row.currWater}` : '-'}</td>
                      <td className="px-3 py-2">{row.prevElectric !== undefined ? `${row.prevElectric} → ${row.currElectric}` : '-'}</td>
                      <td className="px-3 py-2">
                        {row.error ? (
                          <span className="text-red-600 dark:text-red-400">✕ {describeError(row.error, isThai)}</span>
                        ) : (
                          <span className="text-emerald-600 dark:text-emerald-400">✓ {isThai ? 'พร้อม' : 'Ready'}</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="flex items-center justify-between gap-3">
              <p className="text-xs text-nike-mute">
                {importing
                  ? (isThai ? `กำลังนำเข้า ${progress.done}/${progress.total}…` : `Importing ${progress.done}/${progress.total}…`)
                  : (isThai ? `อัตราที่ใช้: น้ำ ${WATER_RATE} / ไฟ ${ELECTRIC_RATE} บาทต่อหน่วย ค่าส่วนกลาง ${COMMON_FEE} บาท` : `Rates: water ${WATER_RATE} / electric ${ELECTRIC_RATE} THB per unit, common fee ${COMMON_FEE} THB`)}
              </p>
              <button
                type="button"
                onClick={runImport}
                disabled={importing || validRows.length === 0 || results !== null}
                className="px-4 py-2 text-xs font-semibold rounded-xl bg-emerald-600 text-white hover:bg-emerald-700 transition-all disabled:opacity-50"
              >
                {isThai ? `นำเข้า ${validRows.length} แถว` : `Import ${validRows.length} rows`}
              </button>
            </div>
          </div>
        )}

        {results && (
          <div className="space-y-2 border-t border-nike-hairline dark:border-nike-dark-card pt-4">
            <p className="text-sm font-semibold text-nike-ink dark:text-white">
              {isThai
                ? `เสร็จแล้ว: สำเร็จ ${results.length - failed.length} · ไม่สำเร็จ ${failed.length}`
                : `Done: ${results.length - failed.length} saved · ${failed.length} failed`}
            </p>
            {failed.length > 0 && (
              <ul className="text-xs text-red-600 dark:text-red-400 space-y-1">
                {failed.map(item => (
                  <li key={item.row.line}>
                    {isThai ? 'บรรทัด' : 'Line'} {item.row.line} · {item.row.roomNumber} {item.row.billingMonth}: {item.message}
                  </li>
                ))}
              </ul>
            )}
            {failed.length > 0 && (
              <p className="text-xs text-nike-mute">
                {isThai
                  ? 'แก้เฉพาะแถวที่ไม่สำเร็จแล้วอัปโหลดใหม่ได้ แถวที่บันทึกแล้วจะถูกข้ามเพราะมีบิลของเดือนนั้นอยู่แล้ว'
                  : 'Fix the failed rows and upload again. Rows already saved are skipped because the bill exists.'}
              </p>
            )}
            <div className="flex justify-end">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 text-xs font-semibold rounded-xl bg-nike-soft-cloud dark:bg-nike-dark-surface border border-nike-hairline dark:border-nike-dark-card text-nike-ink dark:text-white"
              >
                {isThai ? 'ปิด' : 'Close'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
