import { afterEach, describe, expect, it, vi } from 'vitest';
import { createBooking } from './api';
import { BookingRequestError, bookingErrorMessage } from '../utils/bookingErrors';

afterEach(() => vi.unstubAllGlobals());

describe('booking conflict explanations', () => {
  it('explains a legacy Conflict response that contains no reason', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: 'Conflict' }), { status: 409 })));
    let error: unknown;
    try { await createBooking({ roomId: 'room-1' }); } catch (caught) { error = caught; }
    expect(error).toMatchObject({ code: 'BOOKING_CONFLICT', status: 409 });
    expect(bookingErrorMessage(error, true)).toContain('กรุณาตรวจการจองเดิม');
    expect(bookingErrorMessage(error, false)).toContain('Check your existing booking');
  });

  it.each(['ROOM_OCCUPIED', 'ROOM_MAINTENANCE', 'BOOKING_DATE_CONFLICT'])(
    'preserves the specific %s cause returned by the backend', async code => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code, message: 'Reason' }), { status: 409 })));
      await expect(createBooking({ roomId: 'room-1' })).rejects.toMatchObject({ code, status: 409 });
    },
  );

  it('tells the resident how to proceed for each conflict', () => {
    expect(bookingErrorMessage(new BookingRequestError('ROOM_OCCUPIED', 409), true)).toContain('ห้องนี้มีผู้เช่าอยู่แล้ว');
    expect(bookingErrorMessage(new BookingRequestError('ROOM_MAINTENANCE', 409), true)).toContain('ซ่อมบำรุง');
    expect(bookingErrorMessage(new BookingRequestError('BOOKING_DATE_CONFLICT', 409), true)).toContain('ทำสัญญาจากการจองเดิม');
  });
});
