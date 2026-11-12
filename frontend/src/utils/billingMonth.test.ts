import { describe, expect, it } from 'vitest';
import { currentBillingMonth, formatReportMonth, priceRowStatus, recentBillingMonths, reportRange } from './billingMonth';

describe('currentBillingMonth', () => {
  it('uses the format the server parses', () => {
    expect(currentBillingMonth(new Date('2026-11-06T05:00:00Z'))).toBe('November 2026');
  });

  it('follows Bangkok when the device is still in the previous month', () => {
    // 02:00 on 1 November in Bangkok.
    expect(currentBillingMonth(new Date('2026-10-31T19:00:00Z'))).toBe('November 2026');
  });

  it('stays in the month until Bangkok midnight', () => {
    // 23:30 on 31 October in Bangkok.
    expect(currentBillingMonth(new Date('2026-10-31T16:30:00Z'))).toBe('October 2026');
  });
});

describe('recentBillingMonths', () => {
  it('goes back across a year boundary', () => {
    expect(recentBillingMonths(3, new Date('2027-01-15T05:00:00Z'))).toEqual(['January 2027', 'December 2026', 'November 2026']);
  });

  it('does not skip a short month when today is the 31st', () => {
    expect(recentBillingMonths(2, new Date('2027-03-31T05:00:00Z'))).toEqual(['March 2027', 'February 2027']);
  });

  it('starts from the Bangkok month', () => {
    expect(recentBillingMonths(2, new Date('2026-10-31T19:00:00Z'))).toEqual(['November 2026', 'October 2026']);
  });
});

describe('priceRowStatus', () => {
  it('compares by month whichever date form the row uses', () => {
    expect(priceRowStatus('2000-01-01', '2026-11')).toBe('past');
    expect(priceRowStatus('2026-11-01', '2026-11')).toBe('current');
    expect(priceRowStatus('2026-12-01', '2026-11')).toBe('scheduled');
  });
});

describe('reportRange', () => {
  const now = new Date('2026-11-06T05:00:00Z');

  it('leaves the range open for all time', () => {
    expect(reportRange('all', now)).toEqual({});
  });

  it('covers the whole of this year', () => {
    expect(reportRange('thisYear', now)).toEqual({ from: '2026-01', to: '2026-12' });
  });

  it('covers twelve months ending this month', () => {
    expect(reportRange('last12', now)).toEqual({ from: '2025-12', to: '2026-11' });
  });

  it('pads single digit months', () => {
    expect(reportRange('last12', new Date('2026-09-15T05:00:00Z'))).toEqual({ from: '2025-10', to: '2026-09' });
  });

  it('follows Bangkok across a new year', () => {
    // 02:00 on 1 January 2027 in Bangkok.
    expect(reportRange('thisYear', new Date('2026-12-31T19:00:00Z'))).toEqual({ from: '2027-01', to: '2027-12' });
  });
});

describe('formatReportMonth', () => {
  it('names a month in Thai', () => {
    expect(formatReportMonth('2026-08', 'th')).toBe('สิงหาคม 2026');
  });

  it('names a month in English', () => {
    expect(formatReportMonth('2026-08', 'en')).toBe('August 2026');
  });

  it('names the first and last months', () => {
    expect(formatReportMonth('2026-01', 'th')).toBe('มกราคม 2026');
    expect(formatReportMonth('2026-12', 'en')).toBe('December 2026');
  });

  it('leaves a year as it is', () => {
    expect(formatReportMonth('2026', 'th')).toBe('2026');
  });
});
