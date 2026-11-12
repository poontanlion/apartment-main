import { describe, expect, it } from 'vitest';
import { maintenanceDashboardState, maintenancePredictionLink, maintenanceSummaryLinks } from './maintenanceDashboard';
import { MaintenancePredictionApiError } from '../services/api';
import { getNotificationTargetUrl } from '../pages/admin/NotificationCenter';

const summary = { overdue: 3, dueSoon: 4, within30: 9, total: 12 };
describe('maintenance dashboard summary links', () => {
  it('uses backend counts and includes due soon in the within-30 filter', () => {
    const links = maintenanceSummaryLinks(summary);
    expect(links.map(link => link.count)).toEqual([3, 4, 9]);
    expect(links.map(link => new URL(link.to, 'http://localhost').searchParams.get('status')))
      .toEqual(['overdue', 'due_soon', 'due_soon,within_30']);
    for (const link of links) expect(new URL(link.to, 'http://localhost').searchParams.get('tab')).toBe('predictions');
  });
  it('preserves a selected building in every summary link', () => {
    for (const link of maintenanceSummaryLinks(summary, 'bld-1'))
      expect(new URL(link.to, 'http://localhost').searchParams.get('buildingId')).toBe('bld-1');
  });
  it('does not send an All-building sentinel and supports an unfiltered link', () => {
    expect(maintenancePredictionLink('', 'All')).toBe('/admin/maintenance?tab=predictions');
  });
});
describe('isolated dashboard state', () => {
  it('distinguishes loading, an empty table, and no urgent items with data', () => {
    expect(maintenanceDashboardState(null, null)).toBe('loading');
    expect(maintenanceDashboardState({ overdue: 0, dueSoon: 0, within30: 0, total: 0 }, null)).toBe('empty');
    expect(maintenanceDashboardState({ overdue: 0, dueSoon: 0, within30: 0, total: 5 }, null)).toBe('ready');
  });
  it.each([401, 403])('maps HTTP %s to the shared access state', code => {
    expect(maintenanceDashboardState(null, new MaintenancePredictionApiError(code))).toBe('accessError');
  });
  it('maps HTTP 500 and network failure to a recoverable card error', () => {
    expect(maintenanceDashboardState(summary, new MaintenancePredictionApiError(500))).toBe('error');
    expect(maintenanceDashboardState(null, new Error('network'))).toBe('error');
  });
});
describe('digest link in the existing notification center', () => {
  it('opens the predictions tab for the backend digest message', () => {
    expect(getNotificationTargetUrl('Maintenance Predictions Daily Digest', 'View maintenance predictions: /admin/maintenance?tab=predictions', 'warning'))
      .toBe('/admin/maintenance?tab=predictions');
  });
  it('preserves legacy warning/reminder and booking destinations', () => {
    expect(getNotificationTargetUrl('Scheduled Maintenance Reminder', 'Due soon', 'warning')).toBe('/admin/maintenance');
    expect(getNotificationTargetUrl('New Rental Application', '', 'booking')).toBe('/admin/bookings');
  });
});
