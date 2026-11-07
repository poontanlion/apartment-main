const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

const format = (year: number, monthIndex: number) => {
  const date = new Date(Date.UTC(year, monthIndex, 1));
  return `${MONTHS[date.getUTCMonth()]} ${date.getUTCFullYear()}`;
};

/** Billing months follow Bangkok, as the server does, whatever zone the device is set to. */
const bangkokMonth = (now: Date) => {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Bangkok', year: 'numeric', month: 'numeric' }).formatToParts(now);
  const value = (type: string) => Number(parts.find(part => part.type === type)?.value);
  return { year: value('year'), monthIndex: value('month') - 1 };
};

/** A billing month in the server's format, such as "August 2026". */
export const currentBillingMonth = (now: Date = new Date()): string => {
  const { year, monthIndex } = bangkokMonth(now);
  return format(year, monthIndex);
};

export const recentBillingMonths = (count: number, now: Date = new Date()): string[] => {
  const { year, monthIndex } = bangkokMonth(now);
  return Array.from({ length: count }, (_, i) => format(year, monthIndex - i));
};

/** Both arguments may be YYYY-MM or a full date; ISO strings of the same length sort by date. */
export const priceRowStatus = (effectiveMonth: string, activeMonth: string): 'past' | 'current' | 'scheduled' => {
  const row = effectiveMonth.slice(0, 7);
  const active = activeMonth.slice(0, 7);
  if (row === active) return 'current';
  return row < active ? 'past' : 'scheduled';
};

const THAI_MONTHS = [
  'มกราคม', 'กุมภาพันธ์', 'มีนาคม', 'เมษายน', 'พฤษภาคม', 'มิถุนายน',
  'กรกฎาคม', 'สิงหาคม', 'กันยายน', 'ตุลาคม', 'พฤศจิกายน', 'ธันวาคม',
];

/** A month as the report API takes it, such as "2026-08". */
const monthKey = (year: number, monthIndex: number) => {
  const date = new Date(Date.UTC(year, monthIndex, 1));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;
};

export type ReportRangePreset = 'all' | 'thisYear' | 'last12';

export const reportRange = (preset: ReportRangePreset, now: Date = new Date()): { from?: string; to?: string } => {
  const { year, monthIndex } = bangkokMonth(now);
  if (preset === 'thisYear') return { from: monthKey(year, 0), to: monthKey(year, 11) };
  if (preset === 'last12') return { from: monthKey(year, monthIndex - 11), to: monthKey(year, monthIndex) };
  return {};
};

/** Names a report's month key. A year key, such as "2026", is already its own name. */
export const formatReportMonth = (key: string, language: 'th' | 'en'): string => {
  const [year, month] = key.split('-');
  if (!month) return key;
  const names = language === 'th' ? THAI_MONTHS : MONTHS;
  return `${names[Number(month) - 1]} ${year}`;
};
