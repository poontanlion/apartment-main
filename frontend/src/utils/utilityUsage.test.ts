import { describe, it, expect } from 'vitest';
import type { Room, UtilityBill } from '../types';
import { billUnits, billingYears, parseBillingMonth, usageByGroup, usageByPeriod } from './utilityUsage';

const room = (id: string, roomNumber: string, floor: number, buildingId = 'bld-1') =>
  ({ id, roomNumber, floor, buildingId } as Room);

const bill = (roomId: string, roomNumber: string, billingMonth: string, water: [number, number], electric: [number, number]) =>
  ({
    id: `${roomId}-${billingMonth}`,
    roomId,
    roomNumber,
    billingMonth,
    prevWaterMeter: water[0],
    currWaterMeter: water[1],
    prevElectricMeter: electric[0],
    currElectricMeter: electric[1],
  } as UtilityBill);

const rooms = [room('r1', 'A101', 1), room('r2', 'A102', 1), room('r3', 'A201', 2), room('r4', 'B101', 1, 'bld-2')];
const bills = [
  bill('r1', 'A101', 'August 2026', [100, 108], [400, 480]),
  bill('r2', 'A102', 'August 2026', [100, 110], [400, 490]),
  bill('r3', 'A201', 'August 2026', [100, 112], [400, 510]),
  bill('r4', 'B101', 'August 2026', [100, 105], [400, 450]),
  bill('r1', 'A101', 'September 2026', [108, 116], [480, 560]),
  bill('r1', 'A101', 'December 2025', [90, 100], [300, 390]),
];

describe('parseBillingMonth', () => {
  it('reads the server format', () => {
    expect(parseBillingMonth('August 2026')).toEqual({ year: 2026, monthIndex: 7 });
  });
  it('returns null for text it cannot read', () => {
    expect(parseBillingMonth('2026-08')).toBeNull();
    expect(parseBillingMonth('')).toBeNull();
    expect(parseBillingMonth(undefined)).toBeNull();
  });
});

describe('billUnits', () => {
  it('subtracts the previous reading', () => {
    expect(billUnits(bills[0], 'water')).toBe(8);
    expect(billUnits(bills[0], 'electric')).toBe(80);
  });
  it('never goes below zero', () => {
    expect(billUnits(bill('r1', 'A101', 'May 2026', [50, 40], [10, 5]), 'water')).toBe(0);
  });
});

describe('billingYears', () => {
  it('lists years oldest first', () => {
    expect(billingYears(bills)).toEqual([2025, 2026]);
  });
});

describe('usageByGroup', () => {
  const base = { metric: 'water' as const, year: 2026, buildingId: 'All' };

  it('groups by room for one month', () => {
    const points = usageByGroup(bills, rooms, { ...base, groupBy: 'room', monthIndex: 7 });
    expect(points.map(p => [p.label, p.value])).toEqual([['A101', 8], ['A102', 10], ['A201', 12], ['B101', 5]]);
  });

  it('groups by floor and adds rooms on the same floor', () => {
    const points = usageByGroup(bills, rooms, { ...base, groupBy: 'floor', monthIndex: 7 });
    expect(points.map(p => [p.label, p.value])).toEqual([['1', 23], ['2', 12]]);
  });

  it('adds up the whole year when no month is chosen', () => {
    const points = usageByGroup(bills, rooms, { ...base, groupBy: 'room', monthIndex: 'all' });
    expect(points.find(p => p.label === 'A101')?.value).toBe(16);
  });

  it('keeps to one building', () => {
    const points = usageByGroup(bills, rooms, { ...base, groupBy: 'room', monthIndex: 7, buildingId: 'bld-2' });
    expect(points.map(p => p.label)).toEqual(['B101']);
  });
});

describe('usageByPeriod', () => {
  it('returns twelve months, with zero where there are no bills', () => {
    const points = usageByPeriod(bills, rooms, { metric: 'electric', period: 'month', year: 2026, buildingId: 'All' });
    expect(points).toHaveLength(12);
    expect(points[7].value).toBe(80 + 90 + 110 + 50);
    expect(points[8].value).toBe(80);
    expect(points[0].value).toBe(0);
  });

  it('returns one point per year', () => {
    const points = usageByPeriod(bills, rooms, { metric: 'water', period: 'year', year: 2026, buildingId: 'All' });
    expect(points.map(p => [p.label, p.value])).toEqual([['2025', 10], ['2026', 8 + 10 + 12 + 5 + 8]]);
  });
});
