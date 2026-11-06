import { describe, expect, it } from 'vitest';
import { currentBillingMonth, priceRowStatus, recentBillingMonths } from './billingMonth';

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
