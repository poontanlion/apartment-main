import { describe, expect, it } from 'vitest';
import { hasBankInfo, isValidAccountNumber, qrFileProblem, MAX_QR_BYTES } from './bankInfo';

describe('hasBankInfo', () => {
  it('is false when nothing is set', () => {
    expect(hasBankInfo(null)).toBe(false);
    expect(hasBankInfo(undefined)).toBe(false);
    expect(hasBankInfo({})).toBe(false);
    expect(hasBankInfo({ bankName: '', accountNumber: '' })).toBe(false);
  });

  it('is true with any one field', () => {
    expect(hasBankInfo({ bankName: 'Kasikorn' })).toBe(true);
    expect(hasBankInfo({ accountNumber: '123-4-56789-0' })).toBe(true);
    expect(hasBankInfo({ qrCodeImage: 'data:image/png;base64,AAAA' })).toBe(true);
  });
});

describe('qrFileProblem', () => {
  it('accepts a small PNG, JPEG or WebP', () => {
    for (const type of ['image/png', 'image/jpeg', 'image/webp']) {
      expect(qrFileProblem({ type, size: 1000 }, false)).toBeNull();
    }
  });

  it('rejects other file types', () => {
    expect(qrFileProblem({ type: 'application/pdf', size: 1000 }, false)).toMatch(/PNG, JPEG or WebP/);
    expect(qrFileProblem({ type: 'image/svg+xml', size: 1000 }, true)).toMatch(/PNG/);
  });

  it('rejects a file over 1 MB but not one exactly at the limit', () => {
    expect(qrFileProblem({ type: 'image/png', size: MAX_QR_BYTES }, false)).toBeNull();
    expect(qrFileProblem({ type: 'image/png', size: MAX_QR_BYTES + 1 }, false)).toMatch(/1 MB/);
  });
});

describe('isValidAccountNumber', () => {
  it('accepts digits, spaces and hyphens, and blank', () => {
    expect(isValidAccountNumber('')).toBe(true);
    expect(isValidAccountNumber('123-4-56789-0')).toBe(true);
    expect(isValidAccountNumber('123 456 7890')).toBe(true);
  });

  it('rejects letters, symbols and over-long values', () => {
    expect(isValidAccountNumber('12AB34')).toBe(false);
    expect(isValidAccountNumber('123#456')).toBe(false);
    expect(isValidAccountNumber('1'.repeat(31))).toBe(false);
  });
});
