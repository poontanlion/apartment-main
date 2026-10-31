import { test, expect, APIRequestContext } from '@playwright/test';
import { API, createResident, signInAs, useEnglish } from './helpers/api';

async function createBooking(request: APIRequestContext, email: string, status = 'Pending') {
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const roomResponse = await request.post(`${API}/rooms`, {
    data: { roomNumber: `LEASE-${suffix}`, floor: 1, roomName: 'Lease Test Room', roomType: 'Standard Studio',
      status: 'Available', price: 5500, capacity: 2, sizeSqm: 28, amenities: '[]' },
  });
  expect(roomResponse.ok()).toBeTruthy();
  const room = await roomResponse.json();
  const response = await request.post(`${API}/bookings`, {
    data: { roomId: room.id, guestName: `Lease Resident ${suffix}`, guestPhone: '0812345678', guestEmail: email,
      checkIn: '2026-12-01', checkOut: '2027-12-01', totalPrice: 5500, guestCount: 1 },
  });
  expect(response.ok()).toBeTruthy();
  const booking = await response.json();
  if (status !== 'Pending') {
    const approval = await request.put(`${API}/bookings/${booking.id}/status`, { data: { status } });
    expect(approval.ok()).toBeTruthy();
  }
  return { booking, room };
}

test('admin approval unlocks resident lease form and connects it to Tenants @mobile', async ({ page, request, browser }) => {
  const resident = await createResident(request);
  const { booking, room } = await createBooking(request, resident.email);
  const login = await page.request.post('/api/auth/login', { data: { email: resident.email, password: resident.password } });
  expect(login.ok()).toBeTruthy();
  await signInAs(page, resident);
  await page.goto('/check-booking');
  await expect(page.getByText(booking.bookingNo, { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create New Lease Agreement', exact: true })).toHaveCount(0);

  const adminContext = await browser.newContext();
  try {
    const admin = await adminContext.newPage();
    await signInAs(admin, { id: 'usr-admin-01', fullname: 'Admin', email: 'admin@victoryapartment.com', role: 'admin' });
    await admin.goto(`${test.info().project.use.baseURL}/admin/bookings`);
    const row = admin.getByRole('row').filter({ hasText: booking.bookingNo });
    await row.getByRole('combobox').selectOption('Approved');
    await expect(row.getByRole('combobox')).toHaveValue('Approved');
    // Wait for the admin's server update before the resident refreshes.
    await expect.poll(async () => {
      const response = await request.get(`${API}/bookings/user/${encodeURIComponent(resident.email)}`);
      const list = await response.json();
      return list.find((item: { id: string }) => item.id === booking.id)?.status;
    }).toBe('Approved');

    await page.reload();
    await page.getByRole('button', { name: 'Create New Lease Agreement', exact: true }).click();
    const form = page.getByRole('form', { name: 'Lease agreement form' });
    await expect(form.getByLabel('Full Name *', { exact: true })).toHaveValue(booking.guestName);
    await expect(form.getByLabel('Phone Number *', { exact: true })).toHaveValue('0812345678');
    await expect(form.getByLabel('Email Address', { exact: false })).toHaveValue(resident.email);
    const leaseName = `Signed Resident ${booking.id}`;
    await form.getByLabel('Full Name *', { exact: true }).fill(leaseName);
    await form.getByLabel('Phone Number *', { exact: true }).fill('0899999999');
    await form.getByLabel('ID Card / Passport No.', { exact: true }).fill('P123456789');
    await form.getByLabel('Emergency Contact', { exact: true }).fill('Family 0811111111');
    await form.getByRole('button', { name: 'Save Lease Agreement', exact: true }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Lease agreement completed' })).toBeVisible();
    await expect(form).toHaveCount(0);
    await page.reload();
    await expect(page.getByRole('status').filter({ hasText: 'Lease agreement completed' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Create New Lease Agreement', exact: true })).toHaveCount(0);

    await admin.goto(`${test.info().project.use.baseURL}/admin/tenants`);
    await admin.getByPlaceholder('Search name, phone or unit...').fill(leaseName);
    await expect(admin.getByRole('heading', { name: leaseName, exact: true })).toBeVisible();
    await expect(admin.getByText('ID / Passport: ******6789', { exact: true })).toBeVisible();
    await expect(admin.getByText('Emergency Contact: Family xxxxxx1111', { exact: true })).toBeVisible();
    await expect(admin.getByRole('row').filter({ hasText: leaseName }).filter({ hasText: room.roomNumber })).toHaveCount(1);
  } finally {
    await adminContext.close();
  }
});

test('another resident cannot create a lease for a tracked booking', async ({ page, request }) => {
  const owner = await createResident(request);
  const other = await createResident(request);
  const { booking } = await createBooking(request, owner.email, 'Approved');
  await signInAs(page, other);
  await page.goto('/check-booking');
  await page.getByRole('textbox').fill(booking.bookingNo);
  await page.locator('form').getByRole('button').click();
  await expect(page.getByText(booking.bookingNo, { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create New Lease Agreement', exact: true })).toHaveCount(0);
});

test('guest tracking an approved booking is prompted to sign in', async ({ page, request }) => {
  const owner = await createResident(request);
  const { booking } = await createBooking(request, owner.email, 'Approved');
  await useEnglish(page);
  await page.goto('/check-booking');
  await page.getByRole('textbox').fill(booking.bookingNo);
  await page.locator('form').getByRole('button').click();
  await expect(page.getByRole('link', { name: 'Sign in to create your lease', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create New Lease Agreement', exact: true })).toHaveCount(0);
});

test('lease error preserves form values and supports retry', async ({ page, request }) => {
  const owner = await createResident(request);
  const { booking } = await createBooking(request, owner.email, 'Approved');
  await page.request.post('/api/auth/login', { data: { email: owner.email, password: owner.password } });
  await signInAs(page, owner);
  await page.goto('/check-booking');
  await page.getByRole('button', { name: 'Create New Lease Agreement', exact: true }).click();
  const form = page.getByRole('form', { name: 'Lease agreement form' });
  await form.getByLabel('Emergency Contact', { exact: true }).fill('Family 0811111111');
  await page.route(`**/api/bookings/${booking.id}/lease`, route => route.fulfill({
    status: 409, contentType: 'application/json', body: JSON.stringify({ message: 'The room is temporarily unavailable.' }),
  }), { times: 1 });
  await form.getByRole('button', { name: 'Save Lease Agreement', exact: true }).click();
  await expect(form.getByRole('alert')).toHaveText('The room is temporarily unavailable.');
  await expect(form.getByLabel('Emergency Contact', { exact: true })).toHaveValue('Family 0811111111');
  await form.getByRole('button', { name: 'Save Lease Agreement', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Lease agreement completed' })).toBeVisible();
});

test('missing required lease fields show Thai messages beside each field @mobile', async ({ page, request }) => {
  const owner = await createResident(request);
  await createBooking(request, owner.email, 'Approved');
  await signInAs(page, owner);
  await page.goto('/check-booking');
  // Keep the test account but select Thai on subsequent page loads.
  await page.addInitScript(() => localStorage.setItem('vr_lang', 'th'));
  await page.reload();
  await page.getByRole('button', { name: 'ทำสัญญาเช่าใหม่', exact: true }).click();
  const form = page.getByRole('form', { name: 'ฟอร์มสัญญาเช่า' });
  await form.locator('[name="fullname"]').fill('   ');
  await form.locator('[name="phone"]').fill('');
  let submissions = 0;
  page.on('request', req => { if (req.url().endsWith('/lease') && req.method() === 'POST') submissions++; });
  await form.getByRole('button', { name: 'บันทึกสัญญาเช่า', exact: true }).click();
  await expect(form.locator('[name="fullname"]')).toHaveAttribute('aria-invalid', 'true');
  await expect(form.locator('[name="phone"]')).toHaveAttribute('aria-invalid', 'true');
  await expect(form.getByText('กรุณากรอกช่องนี้', { exact: true })).toHaveCount(2);
  await expect(form.getByRole('alert')).toHaveText('กรุณาตรวจสอบและแก้ไขข้อมูลในช่องที่ระบุ');
  expect(submissions).toBe(0);
  await form.locator('[name="fullname"]').fill('Resident');
  await form.locator('[name="phone"]').fill('0812345678');
  await expect(form.getByText('กรุณากรอกช่องนี้', { exact: true })).toHaveCount(0);
});

test('unavailable lease API shows its cause while keeping the completed form', async ({ page, request }) => {
  const owner = await createResident(request);
  const { booking } = await createBooking(request, owner.email, 'Approved');
  await signInAs(page, owner);
  await page.goto('/check-booking');
  await page.getByRole('button', { name: 'Create New Lease Agreement', exact: true }).click();
  const form = page.getByRole('form', { name: 'Lease agreement form' });
  await page.route(`**/api/bookings/${booking.id}/lease`, route => route.fulfill({
    status: 404, contentType: 'application/json', body: JSON.stringify({ error: 'Not Found', status: 404 }),
  }));
  await form.getByRole('button', { name: 'Save Lease Agreement', exact: true }).click();
  await expect(form.getByRole('alert')).toContainText('The lease service is unavailable.');
  await expect(form.locator('[name="fullname"]')).toHaveValue(booking.guestName);
  await expect(form.locator('[aria-invalid="true"]')).toHaveCount(0);
});

test('server validation errors highlight the specific lease field', async ({ page, request }) => {
  const owner = await createResident(request);
  const { booking } = await createBooking(request, owner.email, 'Approved');
  await signInAs(page, owner);
  await page.goto('/check-booking');
  await page.getByRole('button', { name: 'Create New Lease Agreement', exact: true }).click();
  const form = page.getByRole('form', { name: 'Lease agreement form' });
  await page.route(`**/api/bookings/${booking.id}/lease`, route => route.fulfill({
    status: 400, contentType: 'application/json', body: JSON.stringify({
      code: 'VALIDATION_FAILED', fieldErrors: { phone: 'REQUIRED' }, message: 'Please correct the highlighted fields.',
    }),
  }));
  await form.getByRole('button', { name: 'Save Lease Agreement', exact: true }).click();
  await expect(form.locator('[name="phone"]')).toHaveAttribute('aria-invalid', 'true');
  await expect(form.getByText('This field is required.', { exact: true })).toBeVisible();
  await expect(form.locator('[name="fullname"]')).toHaveAttribute('aria-invalid', 'false');
});
