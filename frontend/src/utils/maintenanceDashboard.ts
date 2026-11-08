import { MaintenancePredictionSummary } from '../types';
import { MaintenancePredictionApiError } from '../services/api';
import { cardToStatusQuery } from './maintenancePredictions';

export const maintenancePredictionLink = (status: string = '', buildingId?: string): string => {
  const params = new URLSearchParams({ tab: 'predictions' });
  if (status) params.set('status', status);
  if (buildingId && buildingId !== 'All') params.set('buildingId', buildingId);
  return `/admin/maintenance?${params}`;
};

export const maintenanceSummaryLinks = (summary: MaintenancePredictionSummary, buildingId?: string) =>
  (['overdue', 'dueSoon', 'within30'] as const).map(key => ({
    key, count: summary[key], to: maintenancePredictionLink(cardToStatusQuery(key), buildingId),
  }));

export const maintenanceDashboardState = (summary: MaintenancePredictionSummary | null, error: unknown):
  'loading' | 'ready' | 'empty' | 'accessError' | 'error' => {
  if (error) return error instanceof MaintenancePredictionApiError && [401, 403].includes(error.status) ? 'accessError' : 'error';
  if (summary === null) return 'loading';
  return summary.total === 0 ? 'empty' : 'ready';
};
