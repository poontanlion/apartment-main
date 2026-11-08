import { afterEach, describe, expect, it, vi } from 'vitest';
import { getSummaryReport } from './api';

afterEach(() => vi.unstubAllGlobals());

const stubFetch = (status: number, body: unknown) => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status }));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
};

describe('getSummaryReport', () => {
  it('sends every filter that has a value', async () => {
    const fetchMock = stubFetch(200, { rows: [] });

    await getSummaryReport({
      groupBy: 'TENANT', from: '2026-01', to: '2026-12', buildingId: 'bld-1', status: 'Paid', search: 'สมชาย ใจดี',
    });

    const url = new URL(fetchMock.mock.calls[0][0], 'http://localhost');
    expect(url.pathname).toBe('/api/reports/summary');
    expect(Object.fromEntries(url.searchParams)).toEqual({
      groupBy: 'TENANT', from: '2026-01', to: '2026-12', buildingId: 'bld-1', status: 'Paid', search: 'สมชาย ใจดี',
    });
  });

  it('leaves out filters without a value', async () => {
    const fetchMock = stubFetch(200, { rows: [] });

    await getSummaryReport({ groupBy: 'UNIT', from: undefined, search: '' });

    const url = new URL(fetchMock.mock.calls[0][0], 'http://localhost');
    expect(Object.fromEntries(url.searchParams)).toEqual({ groupBy: 'UNIT' });
  });

  it('passes on the abort signal', async () => {
    const fetchMock = stubFetch(200, { rows: [] });
    const controller = new AbortController();

    await getSummaryReport({ groupBy: 'UNIT' }, controller.signal);

    expect(fetchMock.mock.calls[0][1].signal).toBe(controller.signal);
  });

  it('rejects when the server fails, so a failure never reads as zero totals', async () => {
    stubFetch(400, { code: 'INVALID_MONTH', message: 'Months must look like 2026-08.' });

    await expect(getSummaryReport({ groupBy: 'UNIT' })).rejects.toThrow('Months must look like 2026-08.');
  });
});
