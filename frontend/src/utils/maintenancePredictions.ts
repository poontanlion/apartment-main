import { MaintenancePredictionStatus } from '../types';

export const predictionStatuses: MaintenancePredictionStatus[] = ['overdue', 'due_soon', 'within_30', 'on_track', 'no_data'];
export type PredictionSummaryCard = 'overdue' | 'dueSoon' | 'within30' | 'total';

export const predictionStatusPresentation = (status: MaintenancePredictionStatus) => ({
  overdue: { key: 'mnt.predictions.status.overdue', color: 'bg-red-50 text-red-700 dark:bg-red-950/50 dark:text-red-300' },
  due_soon: { key: 'mnt.predictions.status.due_soon', color: 'bg-amber-50 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300' },
  within_30: { key: 'mnt.predictions.status.within_30', color: 'bg-blue-50 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300' },
  on_track: { key: 'mnt.predictions.status.on_track', color: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300' },
  no_data: { key: 'mnt.predictions.status.no_data', color: 'bg-nike-soft-cloud text-nike-mute dark:bg-nike-dark-surface dark:text-nike-stone' },
})[status];

export const cardToStatusQuery = (card: PredictionSummaryCard): string => ({
  overdue: 'overdue', dueSoon: 'due_soon', within30: 'due_soon,within_30', total: '',
})[card];

export const statusQueryToCard = (query: string): PredictionSummaryCard | null => {
  const normalized = [...new Set(query.split(',').map(value => value.trim().toLowerCase()).filter(Boolean))].sort().join(',');
  if (!normalized) return 'total';
  if (normalized === 'overdue') return 'overdue';
  if (normalized === 'due_soon') return 'dueSoon';
  if (normalized === 'due_soon,within_30') return 'within30';
  return null;
};

export const predictionDaysText = (days: number | null, t: (key: string) => string): string => {
  if (days === null) return '—';
  const key = days === 0 ? 'today' : days < 0 ? 'daysOverdue' : 'daysRemaining';
  return t(`mnt.predictions.${key}`).replace('{count}', String(Math.abs(days)));
};

export const predictionText = (text: string, values: Record<string, number>): string =>
  Object.entries(values).reduce((result, [key, value]) => result.split(`{${key}}`).join(String(value)), text);
