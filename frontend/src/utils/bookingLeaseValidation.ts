import { BookingLeaseApplication } from '../types';

export type LeaseField = keyof BookingLeaseApplication;
export type LeaseFieldErrors = Partial<Record<LeaseField, string>>;

export function validateBookingLease(form: BookingLeaseApplication): LeaseFieldErrors {
  const errors: LeaseFieldErrors = {};
  for (const field of ['fullname', 'phone'] as const) {
    if (!form[field].trim()) errors[field] = 'REQUIRED';
  }
  for (const field of Object.keys(form) as LeaseField[]) {
    if (form[field].trim().length > 255) errors[field] = 'TOO_LONG';
  }
  if (form.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    errors.email = 'INVALID_EMAIL';
  }
  return errors;
}

export function leaseFieldErrorMessage(code: string, isThai: boolean): string {
  switch (code) {
    case 'REQUIRED': return isThai ? 'กรุณากรอกช่องนี้' : 'This field is required.';
    case 'INVALID_EMAIL': return isThai ? 'รูปแบบอีเมลไม่ถูกต้อง' : 'Enter a valid email address.';
    case 'EMAIL_MISMATCH': return isThai ? 'กรุณาใช้อีเมลเดียวกับการจอง' : 'Use the email address associated with your booking.';
    case 'TOO_LONG': return isThai ? 'กรุณากรอกไม่เกิน 255 ตัวอักษร' : 'Use no more than 255 characters.';
    default: return code;
  }
}
