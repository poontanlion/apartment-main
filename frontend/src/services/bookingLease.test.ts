import { afterEach, describe, expect, it, vi } from 'vitest';
import { createBookingLease } from './api';
import { BookingLeaseError, bookingLeaseErrorMessage } from '../utils/bookingLeaseErrors';
import { validateBookingLease } from '../utils/bookingLeaseValidation';

const application = {
  fullname: 'Resident', phone: '0812345678', email: 'resident@example.com',
  idCardPassport: '', emergencyContact: '',
};

afterEach(() => vi.unstubAllGlobals());

describe('lease submission errors', () => {
  it('explains when the lease endpoint is unavailable instead of blaming missing form fields', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 404, error: 'Not Found', path: '/api/bookings/test-booking/lease',
    }), { status: 404 })));
    await expect(createBookingLease('test-booking', application)).rejects.toMatchObject({
      code: 'LEASE_API_UNAVAILABLE',
      status: 404,
    });
  });

  it('preserves field errors from the server so the form can identify missing fields', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'VALIDATION_FAILED', message: 'Please complete the required fields.',
      fieldErrors: { fullname: 'REQUIRED', phone: 'REQUIRED' },
    }), { status: 400 })));
    await expect(createBookingLease('test-booking', application)).rejects.toMatchObject({
      code: 'VALIDATION_FAILED', status: 400,
      fieldErrors: { fullname: 'REQUIRED', phone: 'REQUIRED' },
    });
  });

  it('explains session expiration even without a response body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 401 })));
    await expect(createBookingLease('test-booking', application)).rejects.toMatchObject({ code: 'AUTH_REQUIRED', status: 401 });
  });

  it('explains connection failures', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(createBookingLease('test-booking', application)).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
  });

  it('does not display a raw server failure as a missing-field error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: 'Internal Server Error' }), { status: 500 })));
    await expect(createBookingLease('test-booking', application)).rejects.toMatchObject({ code: 'SERVER_ERROR', status: 500 });
  });

  it('uses the specific booking-not-found code instead of the unavailable endpoint explanation', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'BOOKING_NOT_FOUND', message: 'Booking not found.' }), { status: 404 })));
    await expect(createBookingLease('test-booking', application)).rejects.toMatchObject({ code: 'BOOKING_NOT_FOUND' });
  });
});

describe('lease form validation and explanations', () => {
  it('identifies both missing required fields, including whitespace-only values', () => {
    expect(validateBookingLease({ ...application, fullname: ' ', phone: '' })).toEqual({ fullname: 'REQUIRED', phone: 'REQUIRED' });
  });
  it('allows omitted optional ID and emergency contact details', () => {
    expect(validateBookingLease(application)).toEqual({});
  });
  it('identifies invalid email and fields exceeding database limits', () => {
    expect(validateBookingLease({ ...application, email: 'invalid', emergencyContact: 'a'.repeat(256) })).toEqual({
      email: 'INVALID_EMAIL', emergencyContact: 'TOO_LONG',
    });
  });
  it('localizes the actual cause in Thai and English', () => {
    const error = new BookingLeaseError('LEASE_API_UNAVAILABLE', 404);
    expect(bookingLeaseErrorMessage(error, true)).toContain('ระบบสัญญาเช่ายังไม่พร้อมใช้งาน');
    expect(bookingLeaseErrorMessage(error, false)).toContain('lease service is unavailable');
    expect(bookingLeaseErrorMessage(new BookingLeaseError('ROOM_OCCUPIED', 409), true)).toContain('มีผู้เช่าอยู่แล้ว');
  });
});
