export class BookingRequestError extends Error {
  constructor(public readonly code: string, public readonly status: number, message = '') {
    super(message);
    this.name = 'BookingRequestError';
  }
}

const messages: Record<string, [string, string]> = {
  ROOM_OCCUPIED: [
    'ห้องนี้มีผู้เช่าอยู่แล้ว กรุณาเลือกห้องว่างอื่น หากเป็นการจองของคุณให้ตรวจสถานะการจองเดิม',
    'This room is occupied. Choose another available room, or check your existing booking if this is your room.',
  ],
  ROOM_MAINTENANCE: [
    'ห้องนี้อยู่ระหว่างปรับปรุงหรือซ่อมบำรุง กรุณาเลือกห้องว่างอื่นหรือติดต่อผู้ดูแล',
    'This room is under maintenance. Choose another available room or contact management.',
  ],
  BOOKING_DATE_CONFLICT: [
    'วันที่เลือกทับกับการจองที่อนุมัติแล้ว กรุณาเปลี่ยนช่วงวันที่หรือเลือกห้องอื่น หากเป็นการจองของคุณให้ทำสัญญาจากการจองเดิม',
    'These dates overlap an approved booking. Change the dates or choose another room. If you already booked this room, create your lease from that booking.',
  ],
  LEASE_DATE_CONFLICT: [
    'วันที่เลือกทับกับสัญญาเช่าที่มีอยู่ของห้องนี้ กรุณาเปลี่ยนช่วงวันที่หรือเลือกห้องว่างอื่น',
    'These dates overlap an existing lease for this room. Change the dates or choose another available room.',
  ],
  BOOKING_CONFLICT: [
    'จองไม่ได้เนื่องจากสถานะห้องหรือวันที่ขัดกับการจองที่มีอยู่ กรุณาตรวจการจองเดิม เปลี่ยนช่วงวันที่ หรือเลือกห้องว่างอื่น',
    'The room status or dates conflict with an existing booking. Check your existing booking, change the dates, or choose another available room.',
  ],
  AUTH_REQUIRED: ['กรุณาเข้าสู่ระบบใหม่ก่อนส่งคำขอจอง', 'Please sign in again before booking.'],
  NETWORK_ERROR: ['เชื่อมต่อระบบไม่ได้ กรุณาตรวจสอบอินเทอร์เน็ตแล้วลองอีกครั้ง', 'Unable to connect. Check your connection and retry.'],
  SERVER_ERROR: ['ระบบยังบันทึกการจองไม่ได้ กรุณาลองอีกครั้งหรือติดต่อผู้ดูแล', 'The server could not save the booking. Retry or contact management.'],
};

export function bookingErrorMessage(error: unknown, isThai: boolean): string {
  if (error instanceof BookingRequestError && messages[error.code]) return messages[error.code][isThai ? 0 : 1];
  if (error instanceof Error && error.message) return error.message;
  return messages.SERVER_ERROR[isThai ? 0 : 1];
}
