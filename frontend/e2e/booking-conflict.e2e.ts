import { test, expect } from '@playwright/test';
import { API, createResident, signInAs } from './helpers/api';

for (const scenario of [
  { status: 'Occupied', reason: 'This room is occupied.' },
  { status: 'Maintenance', reason: 'This room is under maintenance.' },
  { status: 'Reserved', reason: 'These dates overlap an approved booking.' },
]) {
  test(`booking submit explains ${scenario.status} conflict and provides next steps`, async ({ page, request }) => {
    const resident = await createResident(request);
    const roomResponse = await request.post(`${API}/rooms`, {
      data: { roomNumber: `CF-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        floor: 1, roomName: 'Booking Conflict Test Room', roomType: 'Standard Studio',
        status: scenario.status === 'Reserved' ? 'Available' : scenario.status,
        price: 5500, capacity: 2, sizeSqm: 28, amenities: '[]' },
    });
    expect(roomResponse.ok()).toBeTruthy();
    const room = await roomResponse.json();
    if (scenario.status === 'Reserved') {
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      const end = new Date();
      end.setDate(end.getDate() + 31);
      const existing = await request.post(`${API}/bookings`, { data: {
        roomId: room.id, guestName: resident.fullname, guestEmail: resident.email, guestPhone: resident.phone,
        checkIn: tomorrow.toISOString().split('T')[0], checkOut: end.toISOString().split('T')[0],
        totalPrice: 5500, guestCount: 1,
      } });
      expect(existing.ok()).toBeTruthy();
      const existingBooking = await existing.json();
      const approval = await request.put(`${API}/bookings/${existingBooking.id}/status`, { data: { status: 'Approved' } });
      expect(approval.ok()).toBeTruthy();
    }
    await signInAs(page, resident);
    await page.goto(`/booking/${room.id}`);
    await expect(page.getByPlaceholder('Full Name', { exact: true })).toHaveValue(resident.fullname);
    await page.locator('form button[type="submit"]').click();
    await expect(page.getByRole('alert')).toContainText(scenario.reason);
    await expect(page.getByRole('link', { name: 'Check Existing Booking / Create Lease', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Choose Another Room', exact: true })).toBeVisible();
    await expect(page.getByPlaceholder('Full Name', { exact: true })).toHaveValue(resident.fullname);
    await expect(page.locator('form button[type="submit"]')).toBeEnabled();
  });
}
