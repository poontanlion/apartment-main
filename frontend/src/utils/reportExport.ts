import { ReportAmounts, ReportGroupBy, SummaryReport, SummaryReportRow } from '../types';
import { formatReportMonth } from './billingMonth';

type Language = 'th' | 'en';
type Cell = string | number;

const GROUP_HEADERS: Record<ReportGroupBy, Record<Language, string>> = {
  UNIT: { th: 'ห้อง', en: 'Room' },
  TENANT: { th: 'ผู้เช่า', en: 'Tenant' },
  MONTH: { th: 'เดือน', en: 'Month' },
  YEAR: { th: 'ปี', en: 'Year' },
};

const AMOUNT_HEADERS: Record<Language, string[]> = {
  th: ['จำนวนบิล', 'ค่าเช่า', 'ค่าน้ำ', 'ค่าไฟ', 'ค่าส่วนกลาง', 'ยอดเรียกเก็บ', 'ชำระแล้ว', 'ค้างชำระ'],
  en: ['Bills', 'Rent', 'Water', 'Electricity', 'Common fee', 'Billed', 'Paid', 'Outstanding'],
};

const UNIDENTIFIED_PREFIX = 'unidentified:';

export const reportColumnHeaders = (groupBy: ReportGroupBy, language: Language): string[] =>
  [GROUP_HEADERS[groupBy][language], ...AMOUNT_HEADERS[language]];

export const reportRowLabel = (row: SummaryReportRow, groupBy: ReportGroupBy, language: Language): string => {
  if (groupBy === 'MONTH') return formatReportMonth(row.key, language);
  if (row.key.startsWith(UNIDENTIFIED_PREFIX)) {
    return language === 'th' ? `ไม่ระบุผู้เช่า (ห้อง ${row.label})` : `Unidentified tenant (Room ${row.label})`;
  }
  return row.label;
};

const moneyColumns = (amounts: ReportAmounts): number[] => [
  amounts.rent, amounts.water, amounts.electric, amounts.commonFee, amounts.billed, amounts.paid, amounts.outstanding,
];

/** Spreadsheets run a cell that starts like a formula, and a tenant name is free text. */
const neutralizeFormula = (text: string): string => (/^[=+\-@\t\r]/.test(text) ? `'${text}` : text);

const quoteCsv = (text: string): string => (/[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text);

const csvText = (text: string): string => quoteCsv(neutralizeFormula(text));

/** Rows only: a totals line would be counted twice when the figures are summed later. */
export const toCsv = (report: SummaryReport, language: Language): string => {
  const header = reportColumnHeaders(report.groupBy, language).map(csvText).join(',');
  const lines = report.rows.map(row => [
    csvText(reportRowLabel(row, report.groupBy, language)),
    String(row.billCount),
    ...moneyColumns(row).map(amount => amount.toFixed(2)),
  ].join(','));
  return `﻿${[header, ...lines].join('\r\n')}`;
};

export const toWorksheetRows = (report: SummaryReport, filterLines: string[], language: Language): Cell[][] => [
  ...filterLines.map(line => [line]),
  [],
  reportColumnHeaders(report.groupBy, language),
  ...report.rows.map(row => [reportRowLabel(row, report.groupBy, language), row.billCount, ...moneyColumns(row)]),
  [language === 'th' ? 'รวม' : 'Total', report.totals.billCount, ...moneyColumns(report.totals)],
];

export const reportFileName = (
  groupBy: ReportGroupBy,
  from: string | undefined,
  to: string | undefined,
  ext: 'csv' | 'xlsx',
): string => {
  const range = from || to ? `${from ?? 'earliest'}_${to ?? 'latest'}` : 'all';
  return `summary-report_${groupBy.toLowerCase()}_${range}.${ext}`;
};

export const downloadCsv = (report: SummaryReport, language: Language, fileName: string): void => {
  const url = URL.createObjectURL(new Blob([toCsv(report, language)], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
};

/** Loads xlsx only when someone exports, which keeps it out of the main bundle. */
export const downloadXlsx = async (
  report: SummaryReport,
  filterLines: string[],
  language: Language,
  fileName: string,
): Promise<void> => {
  const XLSX = await import('xlsx');
  const workbook = XLSX.utils.book_new();
  const worksheet = XLSX.utils.aoa_to_sheet(toWorksheetRows(report, filterLines, language));
  XLSX.utils.book_append_sheet(workbook, worksheet, language === 'th' ? 'สรุป' : 'Summary');
  XLSX.writeFile(workbook, fileName);
};
