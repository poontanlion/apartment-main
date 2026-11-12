import { BankInfo } from '../types';

export const MAX_QR_BYTES = 1024 * 1024;
export const QR_MIME_TYPES = ['image/png', 'image/jpeg', 'image/webp'];

/** True when there is anything worth printing on an invoice. */
export function hasBankInfo(info?: BankInfo | null): info is BankInfo {
  return Boolean(info && (info.bankName || info.accountName || info.accountNumber || info.qrCodeImage));
}

/** Why a chosen QR file can't be used, or null when it is fine. */
export function qrFileProblem(file: { type: string; size: number }, th: boolean): string | null {
  if (!QR_MIME_TYPES.includes(file.type)) {
    return th ? 'ใช้ได้เฉพาะไฟล์ PNG, JPEG หรือ WebP' : 'Use a PNG, JPEG or WebP image.';
  }
  if (file.size > MAX_QR_BYTES) {
    return th ? 'รูป QR ต้องมีขนาดไม่เกิน 1 MB' : 'The QR image must be 1 MB or smaller.';
  }
  return null;
}

/** Account numbers are digits with optional spaces and hyphens. */
export function isValidAccountNumber(value: string): boolean {
  const trimmed = value.trim();
  return trimmed === '' || (trimmed.length <= 30 && /^[0-9 -]+$/.test(trimmed));
}
