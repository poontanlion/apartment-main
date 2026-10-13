import { APIRequestContext, Page, expect } from '@playwright/test';

/**
 * Helpers that talk to the backend directly so each test can set up its data
 * quickly and reliably, and keep the browser steps focused on what is being tested.
 */

export const API = `${process.env.E2E_BACKEND_URL || 'http://localhost:8080'}/api`;

export interface ResidentAccount {
  id: string;
  fullname: string;
  email: string;
  password: string;
  phone: string;
}

export const uniqueEmail = (prefix = 'resident') =>
  `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@e2e.test`;

/** Registers a brand-new resident through the API. */
export async function createResident(request: APIRequestContext, name = 'E2E Resident'): Promise<ResidentAccount> {
  const email = uniqueEmail();
  const password = 'E2e-pass-123';
  const phone = '0812345678';
  const res = await request.post(`${API}/auth/register`, {
    data: { fullname: name, email, password, phone },
  });
  expect(res.ok(), 'register resident via API').toBeTruthy();
  const body = await res.json();
  return { id: body.user.id, fullname: name, email, password, phone };
}

/** Finds a room that can still be booked. */
export async function findAvailableRoom(request: APIRequestContext) {
  const res = await request.get(`${API}/rooms`);
  expect(res.ok()).toBeTruthy();
  const rooms: any[] = await res.json();
  const room = rooms.find((r) => r.status === 'Available');
  expect(room, 'seed data should contain at least one Available room').toBeTruthy();
  return room as { id: string; roomNumber: string };
}

/** Resident books a room and the admin approves it, so the maintenance portal unlocks. */
export async function bookAndApproveRoom(request: APIRequestContext, resident: ResidentAccount) {
  const room = await findAvailableRoom(request);

  const bookingRes = await request.post(`${API}/bookings`, {
    data: {
      roomId: room.id,
      roomNumber: room.roomNumber,
      guestName: resident.fullname,
      guestEmail: resident.email,
      guestPhone: resident.phone,
      checkIn: '2026-12-01',
      checkOut: '2027-12-01',
      guestCount: 1,
    },
  });
  expect(bookingRes.ok(), 'create booking via API').toBeTruthy();
  const booking = await bookingRes.json();

  const approveRes = await request.put(`${API}/bookings/${booking.id}/status`, {
    data: { status: 'Approved' },
  });
  expect(approveRes.ok(), 'admin approves booking via API').toBeTruthy();

  return { room, booking };
}

/** Acts as the admin/technician: moves a repair request to a new status. */
export async function setTaskStatus(
  request: APIRequestContext,
  taskId: string,
  status: 'Pending' | 'In Progress' | 'Completed',
  assignedWorker?: string,
) {
  const res = await request.put(`${API}/maintenance-tasks/${taskId}`, {
    data: { status, ...(assignedWorker ? { assignedWorker } : {}) },
  });
  expect(res.ok(), `set task ${taskId} to ${status}`).toBeTruthy();
}

export async function getResidentTasks(request: APIRequestContext, email: string): Promise<any[]> {
  const res = await request.get(`${API}/maintenance-tasks/user/${encodeURIComponent(email)}`);
  expect(res.ok()).toBeTruthy();
  return res.json();
}

/**
 * Makes the browser behave as if the user had already signed in and picked English.
 * (The app keeps the session in localStorage under "apt_auth_user".)
 * Must be called BEFORE page.goto().
 */
export async function signInAs(page: Page, user: { id: string; fullname: string; email: string; phone?: string; role?: string }) {
  await page.addInitScript((u) => {
    localStorage.setItem('vr_lang', 'en');
    localStorage.setItem(
      'apt_auth_user',
      JSON.stringify({ id: u.id, fullname: u.fullname, email: u.email, phone: u.phone, role: u.role ?? 'customer' }),
    );
  }, user);
}

/** Forces English UI without signing in. */
export async function useEnglish(page: Page) {
  await page.addInitScript(() => localStorage.setItem('vr_lang', 'en'));
}
