import { LeaseFieldErrors } from './bookingLeaseValidation';

export class BookingLeaseError extends Error {
  constructor(
    public readonly code: string,
    public readonly status: number,
    message = '',
    public readonly fieldErrors: LeaseFieldErrors = {},
  ) {
    super(message);
    this.name = 'BookingLeaseError';
  }
}

const messages: Record<string, [string, string]> = {
  VALIDATION_FAILED: ['กรุณาตรวจสอบและแก้ไขข้อมูลในช่องที่ระบุ', 'Please correct the highlighted fields.'],
  INVALID_REQUEST: ['ข้อมูลคำขอไม่ถูกต้อง กรุณาตรวจสอบข้อมูลในฟอร์มแล้วลองอีกครั้ง', 'The submitted data is invalid. Check the form and try again.'],
  AUTH_REQUIRED: ['การเข้าสู่ระบบหมดอายุหรือยังไม่ได้เข้าสู่ระบบ กรุณาเข้าสู่ระบบใหม่แล้วลองอีกครั้ง', 'Your session has expired or you are not signed in. Sign in again and retry.'],
  NOT_BOOKING_OWNER: ['บัญชีนี้ไม่ใช่เจ้าของการจอง กรุณาเข้าสู่ระบบด้วยบัญชีที่ใช้จองห้อง', 'Sign in with the account that made this booking.'],
  LEASE_API_UNAVAILABLE: ['ระบบสัญญาเช่ายังไม่พร้อมใช้งาน กรุณาติดต่อผู้ดูแลให้อัปเดตระบบแล้วลองอีกครั้ง', 'The lease service is unavailable. Please ask management to update the system and retry.'],
  BOOKING_NOT_FOUND: ['ไม่พบการจองนี้ กรุณาโหลดหน้าตรวจสอบการจองใหม่', 'Booking not found. Refresh your booking list.'],
  BOOKING_NOT_APPROVED: ['การจองยังไม่ได้รับการอนุมัติ กรุณารอผู้ดูแลอนุมัติก่อนทำสัญญาเช่า', 'This booking is not approved. Wait for management approval before creating a lease.'],
  INVALID_LEASE_DATES: ['วันที่เริ่มหรือสิ้นสุดการเช่าในข้อมูลการจองไม่ถูกต้อง กรุณาติดต่อผู้ดูแลเพื่อแก้ไข', 'The booking has invalid lease dates. Ask management to correct them.'],
  INVALID_LEASE_PERIOD: ['วันสิ้นสุดการเช่าต้องอยู่หลังวันเริ่มเช่า กรุณาติดต่อผู้ดูแลเพื่อแก้ไขข้อมูลการจอง', 'The lease end date must follow the start date. Ask management to correct the booking.'],
  ROOM_NOT_ASSIGNED: ['การจองยังไม่ได้ระบุห้องพัก กรุณาติดต่อผู้ดูแล', 'No room is assigned to this booking. Contact management.'],
  ROOM_NOT_FOUND: ['ไม่พบห้องพักที่จอง กรุณาติดต่อผู้ดูแล', 'The booked room was not found. Contact management.'],
  ROOM_OCCUPIED: ['ห้องที่จองมีผู้เช่าอยู่แล้ว กรุณาติดต่อผู้ดูแลก่อนทำสัญญา', 'The booked room is occupied. Contact management before creating a lease.'],
  ROOM_MAINTENANCE: ['ห้องที่จองอยู่ระหว่างปรับปรุงหรือซ่อมบำรุง กรุณาติดต่อผู้ดูแล', 'The booked room is under maintenance. Contact management.'],
  INVALID_RENT: ['ค่าเช่าในข้อมูลการจองไม่ถูกต้อง กรุณาติดต่อผู้ดูแลเพื่อแก้ไข', 'The booking has an invalid rent amount. Ask management to correct it.'],
  LEASE_CONFLICT: ['มีสัญญาเช่าอื่นทับซ้อนช่วงวันที่จอง กรุณาติดต่อผู้ดูแลเพื่อตรวจสอบ', 'Another lease overlaps this booking period. Contact management.'],
  NETWORK_ERROR: ['เชื่อมต่อระบบไม่ได้ กรุณาตรวจสอบอินเทอร์เน็ตแล้วลองอีกครั้ง ข้อมูลที่กรอกยังอยู่ในฟอร์ม', 'Unable to connect. Check your connection and retry. Your form details have been kept.'],
  SERVER_ERROR: ['ระบบเกิดข้อผิดพลาดขณะบันทึก กรุณาลองอีกครั้ง หากยังไม่สำเร็จให้ติดต่อผู้ดูแล', 'The server could not save your lease. Retry, or contact management if the problem continues.'],
};

export function bookingLeaseErrorMessage(error: unknown, isThai: boolean): string {
  if (error instanceof BookingLeaseError && messages[error.code]) {
    return messages[error.code][isThai ? 0 : 1];
  }
  if (error instanceof Error && error.message) return error.message;
  return messages.SERVER_ERROR[isThai ? 0 : 1];
}
