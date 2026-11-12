import { BookingLeaseApplication } from '../types';

export type LeaseField = keyof BookingLeaseApplication;
export type LeaseFieldErrors = Partial<Record<LeaseField, string>>;

export function validateBookingLease(form: BookingLeaseApplication): LeaseFieldErrors {
  const errors: LeaseFieldErrors = {};
  for (const field of ['fullname', 'phone', 'idCardPassport'] as const) {
    if (!form[field].trim()) errors[field] = 'REQUIRED';
  }
  for (const field of Object.keys(form) as LeaseField[]) {
    if (form[field].trim().length > 255) errors[field] = 'TOO_LONG';
  }
  if (form.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    errors.email = 'INVALID_EMAIL';
  }
  if (form.phone.trim() && !/^[0-9]+$/.test(form.phone)) errors.phone = 'INVALID_PHONE';
  if (form.idCardPassport.trim() && !/^[0-9]{13}$/.test(form.idCardPassport)) errors.idCardPassport = 'INVALID_ID';
  return errors;
}

export function leaseFieldErrorMessage(code: string, isThai: boolean): string {
  switch (code) {
    case 'DIGITS_ONLY': return isThai ? 'กรุณากรอกเฉพาะตัวเลข' : 'Please enter numbers only.';
    case 'INVALID_PHONE': return isThai ? 'กรุณากรอกเบอร์โทรเป็นตัวเลขเท่านั้น' : 'Use digits only for the phone number.';
    case 'INVALID_ID': return isThai ? 'กรุณากรอกตัวเลขให้ครบ 13 หลัก' : 'Enter exactly 13 digits.';
    case 'REQUIRED': return isThai ? 'กรุณากรอกช่องนี้' : 'This field is required.';
    case 'INVALID_EMAIL': return isThai ? 'รูปแบบอีเมลไม่ถูกต้อง' : 'Enter a valid email address.';
    case 'EMAIL_MISMATCH': return isThai ? 'กรุณาใช้อีเมลเดียวกับการจอง' : 'Use the email address associated with your booking.';
    case 'TOO_LONG': return isThai ? 'กรุณากรอกไม่เกิน 255 ตัวอักษร' : 'Use no more than 255 characters.';
    default: return code;
  }
}
