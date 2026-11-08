import { afterEach, describe, expect, it, vi } from 'vitest';
import { getMaintenancePredictions, MaintenancePredictionApiError, refreshMaintenancePredictions, setMaintenanceAppointment, cancelMaintenanceAppointment } from './api';

afterEach(() => vi.unstubAllGlobals());
describe('maintenance prediction API contract', () => {
  it('sends appointment PUT and DELETE with same-origin cookies and only date in the body', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ hasAppointment: true }) });
    vi.stubGlobal('fetch', fetchMock);
    await setMaintenanceAppointment('rm-A101', 'mtt-light', '2026-11-20');
    expect(fetchMock).toHaveBeenLastCalledWith('/api/maintenance/predictions/rm-A101/mtt-light/appointment',
      expect.objectContaining({ method: 'PUT', credentials: 'same-origin', body: '{"date":"2026-11-20"}' }));
    await cancelMaintenanceAppointment('rm-A101', 'mtt-light');
    expect(fetchMock).toHaveBeenLastCalledWith('/api/maintenance/predictions/rm-A101/mtt-light/appointment',
      expect.objectContaining({ method: 'DELETE', credentials: 'same-origin' }));
  });
  it('encodes filters, forwards abort signal, and explicitly sends session cookies', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ predictions: [], summary: { total: 0 } }) });
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    await getMaintenancePredictions({ roomId: 'rm-A101', taskTypeId: 'mtt-light', buildingId: 'bld-1', status: 'due_soon,within_30', q: 'หลอดไฟ A' }, controller.signal);
    const [url, options] = fetchMock.mock.calls[0];
    const query = new URL(url, 'http://localhost').searchParams;
    expect(query.get('q')).toBe('หลอดไฟ A');
    expect(query.get('status')).toBe('due_soon,within_30');
    expect(query.get('roomId')).toBe('rm-A101');
    expect(query.get('taskTypeId')).toBe('mtt-light');
    expect(query.get('buildingId')).toBe('bld-1');
    expect(options.credentials).toBe('same-origin');
    expect(options.signal).toBe(controller.signal);
  });
  it.each([401, 403, 500])('retains HTTP %s for the UI error state', async status => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status }));
    const result = getMaintenancePredictions();
    await expect(result).rejects.toBeInstanceOf(MaintenancePredictionApiError);
    await expect(result).rejects.toMatchObject({ status });
  });
  it('posts refresh and returns backend success/failure counts', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ success: 8, failure: 1 }) });
    vi.stubGlobal('fetch', fetchMock);
    expect(await refreshMaintenancePredictions()).toEqual({ success: 8, failure: 1 });
    expect(fetchMock).toHaveBeenCalledWith('/api/maintenance/predictions/refresh', expect.objectContaining({ method: 'POST', credentials: 'same-origin' }));
  });
});
