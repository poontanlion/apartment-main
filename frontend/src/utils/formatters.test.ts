import { describe, it, expect } from 'vitest';
import {
  formatCurrency,
  formatDate,
  calculateNights,
  formatSuppliesSummary,
  getTranslatedRoomType,
  getTranslatedBedType,
  getTranslatedRoomName,
} from './formatters';

describe('formatCurrency', () => {
  it('formats a normal amount with the baht symbol and thousands separator', () => {
    const out = formatCurrency(4500);
    expect(out).toContain('฿');
    expect(out).toContain('4,500');
    expect(out).not.toContain('THB');
  });

  it('drops decimals (maximumFractionDigits = 0)', () => {
    expect(formatCurrency(1234.56)).toContain('1,235');
  });

  it.each([undefined, null, NaN as unknown as number])('treats %s as 0', (value) => {
    expect(formatCurrency(value as number | null | undefined)).toContain('0');
  });
});

describe('formatDate', () => {
  it('returns "-" for empty input', () => {
    expect(formatDate('')).toBe('-');
  });

  it('formats an ISO date as "d Mon yyyy" (en-GB)', () => {
    // Local-time timestamp avoids timezone off-by-one in CI runners.
    expect(formatDate('2026-03-05T12:00:00')).toBe('5 Mar 2026');
  });
});

describe('calculateNights', () => {
  it('returns 1 when either date is missing', () => {
    expect(calculateNights('', '2026-03-05')).toBe(1);
    expect(calculateNights('2026-03-01', '')).toBe(1);
  });

  it('counts the nights between check-in and check-out', () => {
    expect(calculateNights('2026-03-01', '2026-03-04')).toBe(3);
  });

  it('never returns less than 1 (same day or reversed dates)', () => {
    expect(calculateNights('2026-03-05', '2026-03-05')).toBe(1);
    expect(calculateNights('2026-03-10', '2026-03-05')).toBe(1);
  });
});

describe('formatSuppliesSummary', () => {
  it('shows a placeholder when nothing is used', () => {
    expect(formatSuppliesSummary(undefined)).toBe('No supplies used');
    expect(formatSuppliesSummary('')).toBe('No supplies used');
    expect(formatSuppliesSummary('[]')).toBe('No supplies used');
  });

  it('passes plain text through unchanged', () => {
    expect(formatSuppliesSummary('LED bulb x2')).toBe('LED bulb x2');
  });

  it('turns a JSON array into a readable list', () => {
    const raw = JSON.stringify([
      { name: 'LED bulb', quantity: 2 },
      { name: 'Pipe tape' },
    ]);
    expect(formatSuppliesSummary(raw)).toBe('LED bulb x2, Pipe tape x1');
  });

  it('handles a single JSON object', () => {
    expect(formatSuppliesSummary('{"name":"Fuse","quantity":3}')).toBe('Fuse x3');
  });

  it('falls back to supply_id when name is missing', () => {
    expect(formatSuppliesSummary('[{"supply_id":"sup-1","quantity":4}]')).toBe('sup-1 x4');
  });

  it('returns the raw string when the JSON is malformed', () => {
    expect(formatSuppliesSummary('[not valid json')).toBe('[not valid json');
  });
});

describe('Thai translations', () => {
  it('translates room types in Thai but leaves English untouched', () => {
    expect(getTranslatedRoomType('Studio (Single Bed)', 'th')).toBe('สตูดิโอ (เตียงเดี่ยว)');
    expect(getTranslatedRoomType('1-Bedroom Suite', 'th')).toBe('1 ห้องนอน สวีท');
    expect(getTranslatedRoomType('Studio (Single Bed)', 'en')).toBe('Studio (Single Bed)');
    expect(getTranslatedRoomType(undefined, 'th')).toBe('');
  });

  it('translates bed types and keeps the more specific match first', () => {
    expect(getTranslatedBedType('King Bed + Sofa Bed', 'th')).toContain('โซฟาเบด');
    expect(getTranslatedBedType('King Bed', 'th')).toContain('เตียงคิงไซส์');
    expect(getTranslatedBedType('Single Bed', 'en')).toBe('Single Bed');
  });

  it('builds a fallback room name when none is given', () => {
    expect(getTranslatedRoomName(undefined, '101', 'th')).toBe('ห้อง 101');
    expect(getTranslatedRoomName(undefined, '101', 'en')).toBe('Unit 101');
  });

  it('converts "Unit" to "ห้อง" in Thai and back in English', () => {
    expect(getTranslatedRoomName('Unit 101', '101', 'th')).toBe('ห้อง 101');
    expect(getTranslatedRoomName('ห้อง 101 (ชั้น 1)', '101', 'en')).toBe('Unit 101 (Floor 1)');
  });
});