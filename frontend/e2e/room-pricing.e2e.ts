import { test, expect, APIRequestContext, Page } from '@playwright/test';
import { API, createResident, signInAs } from './helpers/api';

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

/** Shifts a YYYY-MM month. */
const addMonths = (month: string, delta: number) => {
  const [year, number] = month.split('-').map(Number);
  const shifted = new Date(Date.UTC(year, number - 1 + delta, 1));
  return shifted.toISOString().slice(0, 7);
};

/** "2026-12" becomes "December 2026", the format bills use. */
const billingMonth = (month: string) => {
  const [year, number] = month.split('-').map(Number);
  return `${MONTHS[number - 1]} ${year}`;
};

async function createRoom(request: APIRequestContext, price = 6000) {
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const response = await request.post(`${API}/rooms`, {
    data: { roomNumber: `PRICE-${suffix}`, floor: 1, roomName: 'Pricing Test Room', roomType: 'Standard Studio',
      status: 'Available', price, capacity: 2, sizeSqm: 28, amenities: '[]' },
  });
  expect(response.ok(), 'create room').toBeTruthy();
  return (await response.json()) as { id: string; roomNumber: string };
}

/** The month the server considers current, so tests agree with it near a month boundary. */
async function currentMonth(request: APIRequestContext, roomId: string): Promise<string> {
  const response = await request.get(`${API}/rooms/${roomId}/prices`);
  expect(response.ok()).toBeTruthy();
  return (await response.json()).currentMonth;
}

async function setPrice(request: APIRequestContext, roomId: string, price: number, effectiveMonth: string) {
  const response = await request.post(`${API}/rooms/${roomId}/prices`, { data: { price, effectiveMonth } });
  expect(response.ok(), 'set price').toBeTruthy();
}

async function createLease(request: APIRequestContext, room: { id: string; roomNumber: string }, start: string, end: string, rentAmount = 6000) {
  const response = await request.post(`${API}/leases`, {
    data: { tenantId: `t-${room.id}`, tenantName: 'Pricing Tenant', roomId: room.id, roomNumber: room.roomNumber,
      checkInDate: start, checkOutDate: end, rentAmount, status: 'Active' },
  });
  expect(response.ok(), 'create lease').toBeTruthy();
  return (await response.json()).lease as { id: string };
}

const rentQuote = async (request: APIRequestContext, roomId: string, month: string) => {
  const response = await request.get(`${API}/utility-bills/rent-quote`, { params: { roomId, month: billingMonth(month) } });
  expect(response.ok()).toBeTruthy();
  return response.json();
};

const signInAsAdmin = (page: Page) =>
  signInAs(page, { id: 'usr-admin-01', fullname: 'Admin', email: 'admin@victoryapartment.com', role: 'admin' });

async function openRoomEditor(page: Page, roomNumber: string) {
  await page.goto('/admin/rooms');
  await page.getByRole('row').filter({ hasText: roomNumber }).getByTitle('Edit Unit').click();
}

test('price change does not touch the past', async ({ request }) => {
  const room = await createRoom(request);
  const month = await currentMonth(request, room.id);
  const billResponse = await request.post(`${API}/utility-bills`, {
    data: { roomId: room.id, roomNumber: room.roomNumber, invoiceNo: `INV-${room.id}`, billingMonth: billingMonth(month) },
  });
  expect(billResponse.ok()).toBeTruthy();
  const bill = await billResponse.json();
  expect(bill.rentAmount).toBe(6000);

  await setPrice(request, room.id, 6500, addMonths(month, 1));

  const bills: { id: string; rentAmount: number }[] = await (await request.get(`${API}/utility-bills`)).json();
  expect(bills.find(item => item.id === bill.id)?.rentAmount).toBe(6000);
  expect((await (await request.get(`${API}/rooms/${room.id}`)).json()).price).toBe(6000);
  const priceFor = async (m: string) =>
    (await (await request.get(`${API}/rooms/${room.id}/price`, { params: { month: m } })).json()).price;
  expect(await priceFor(addMonths(month, -1))).toBe(6000);
  expect(await priceFor(month)).toBe(6000);
  expect(await priceFor(addMonths(month, 1))).toBe(6500);
  expect(await priceFor(addMonths(month, 2))).toBe(6500);
});

test('booking uses check-in month price and ignores the client price', async ({ request }) => {
  const room = await createRoom(request);
  const month = await currentMonth(request, room.id);
  await setPrice(request, room.id, 6500, addMonths(month, 1));

  const book = async (checkIn: string, checkOut: string) => {
    const response = await request.post(`${API}/bookings`, {
      data: { roomId: room.id, guestName: 'Pricing Guest', guestPhone: '0812345678', guestEmail: 'pricing@e2e.test',
        checkIn, checkOut, totalPrice: 1, guestCount: 1 },
    });
    expect(response.ok()).toBeTruthy();
    return (await response.json()).totalPrice;
  };

  expect(await book(`${addMonths(month, 1)}-15`, `${addMonths(month, 7)}-15`)).toBe(6500);
  expect(await book(`${month}-28`, `${addMonths(month, 6)}-28`)).toBe(6000);
});

test('running lease keeps contract rent after a price change', async ({ request }) => {
  const room = await createRoom(request);
  const month = await currentMonth(request, room.id);
  const lease = await createLease(request, room, `${month}-01`, `${addMonths(month, 6)}-01`);

  await setPrice(request, room.id, 6500, addMonths(month, 1));

  expect(await rentQuote(request, room.id, addMonths(month, 1))).toEqual({ rentAmount: 6000, source: 'LEASE', leaseId: lease.id });
  expect((await (await request.get(`${API}/rooms/${room.id}`)).json()).price).toBe(6000);
});

test('final bill after termination keeps contract rent for that month only', async ({ request }) => {
  const room = await createRoom(request);
  const month = await currentMonth(request, room.id);
  const lease = await createLease(request, room, `${addMonths(month, -2)}-01`, `${addMonths(month, 6)}-01`);
  await setPrice(request, room.id, 6500, addMonths(month, 1));

  const terminated = await request.put(`${API}/leases/${lease.id}/terminate`);
  expect(terminated.ok()).toBeTruthy();

  expect((await rentQuote(request, room.id, month)).rentAmount).toBe(6000);
  expect(await rentQuote(request, room.id, addMonths(month, 1))).toEqual({ rentAmount: 6500, source: 'ROOM_PRICE', leaseId: null });
});

test('past month is rejected', async ({ request }) => {
  const room = await createRoom(request);
  const month = await currentMonth(request, room.id);

  const response = await request.post(`${API}/rooms/${room.id}/prices`, { data: { price: 6500, effectiveMonth: addMonths(month, -1) } });

  expect(response.status()).toBe(400);
  expect((await response.json()).code).toBe('PRICE_MONTH_IN_PAST');
});

test('admin sees the selected room when editing it', async ({ page, request }) => {
  const first = await createRoom(request, 6100);
  const second = await createRoom(request, 6200);
  await signInAsAdmin(page);

  await openRoomEditor(page, first.roomNumber);
  await expect(page.getByPlaceholder('e.g. 101, 202, A301')).toHaveValue(first.roomNumber);
  await expect(page.getByLabel('Monthly Rent (THB)')).toHaveValue(/6,100/);
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();

  await page.getByRole('row').filter({ hasText: second.roomNumber }).getByTitle('Edit Unit').click();
  await expect(page.getByPlaceholder('e.g. 101, 202, A301')).toHaveValue(second.roomNumber);
  await expect(page.getByLabel('Monthly Rent (THB)')).toHaveValue(/6,200/);
});

test('editing other room details keeps the price and history', async ({ page, request }) => {
  const room = await createRoom(request);
  await signInAsAdmin(page);
  await openRoomEditor(page, room.roomNumber);

  await page.getByPlaceholder('e.g. King Bed').fill('Queen Bed');
  await page.getByRole('button', { name: 'Save Unit', exact: true }).click();
  await expect.poll(async () => (await (await request.get(`${API}/rooms/${room.id}`)).json()).bedType).toBe('Queen Bed');

  const saved = await (await request.get(`${API}/rooms/${room.id}`)).json();
  expect(saved.price).toBe(6000);
  expect(saved.roomNumber).toBe(room.roomNumber);
  const history = await (await request.get(`${API}/rooms/${room.id}/prices`)).json();
  expect(history.prices).toHaveLength(1);
  expect(history.prices[0].id).toBeNull();
});

test('admin schedules and removes a price in the room editor', async ({ page, request }) => {
  const room = await createRoom(request);
  const month = await currentMonth(request, room.id);
  await signInAsAdmin(page);
  await openRoomEditor(page, room.roomNumber);
  const panel = page.getByRole('region', { name: 'Room price history' });
  await expect(panel.getByText('Starting price')).toBeVisible();

  await panel.getByLabel('Effective month').fill(addMonths(month, 1));
  await panel.getByLabel('New price (THB)').fill('6500');
  await panel.getByRole('button', { name: 'Set price', exact: true }).click();

  const scheduledRow = panel.getByRole('listitem').filter({ hasText: 'Scheduled' });
  await expect(scheduledRow).toContainText('6,500');
  await expect(page.getByLabel('Monthly Rent (THB)')).toHaveValue(/6,000/);

  await scheduledRow.getByRole('button', { name: /Remove price/ }).click();
  await expect(panel.getByRole('listitem').filter({ hasText: 'Scheduled' })).toHaveCount(0);
  await expect(panel.getByRole('listitem').filter({ hasText: 'Current' })).toContainText('6,000');
});

test('a price for this month updates the room straight away', async ({ page, request }) => {
  const room = await createRoom(request);
  await signInAsAdmin(page);
  await openRoomEditor(page, room.roomNumber);
  const panel = page.getByRole('region', { name: 'Room price history' });

  await panel.getByLabel('New price (THB)').fill('6700');
  await panel.getByRole('button', { name: 'Set price', exact: true }).click();

  await expect(page.getByLabel('Monthly Rent (THB)')).toHaveValue(/6,700/);
  await expect(panel.getByRole('listitem').filter({ hasText: 'Current' })).toContainText('6,700');
  await expect(panel.getByRole('listitem').filter({ hasText: 'Past' })).toContainText('6,000');
  expect((await (await request.get(`${API}/rooms/${room.id}`)).json()).price).toBe(6700);
});

const openBillForm = async (page: Page) => {
  await page.goto('/admin/utility-bills');
  await page.getByRole('button', { name: /New Bill|Create Bill|Bill/ }).first().click();
};

test('bill form shows the rent the server will charge', async ({ page, request }) => {
  const room = await createRoom(request);
  const month = await currentMonth(request, room.id);
  await setPrice(request, room.id, 6500, addMonths(month, 1));
  await signInAsAdmin(page);
  await openBillForm(page);
  await page.locator(`select:has(option[value="${room.id}"])`).selectOption(room.id);

  await expect(page.getByText('Room Rent (room price):').locator('..')).toContainText('6,000');

  const [nextYear, nextMonth] = addMonths(month, 1).split('-').map(Number);
  await page.locator('select:has(option[value="December"])').selectOption(MONTHS[nextMonth - 1]);
  await page.locator('select:has(option[value="2026"])').selectOption(String(nextYear));
  await expect(page.getByText('Room Rent (room price):').locator('..')).toContainText('6,500');

  await page.getByRole('button', { name: 'Save & Generate Bill', exact: true }).click();
  await expect.poll(async () => {
    const bills: { roomId: string; rentAmount: number; billingMonth: string }[] = await (await request.get(`${API}/utility-bills`)).json();
    return bills.find(bill => bill.roomId === room.id);
  }).toMatchObject({ rentAmount: 6500, billingMonth: billingMonth(addMonths(month, 1)) });
});

test('bill form does not keep a past tenant on a month their lease does not cover', async ({ page, request }) => {
  const room = await createRoom(request);
  const month = await currentMonth(request, room.id);
  const lease = await createLease(request, room, `${addMonths(month, -2)}-01`, `${addMonths(month, 6)}-01`);
  expect((await request.put(`${API}/leases/${lease.id}/terminate`)).ok()).toBeTruthy();
  await signInAsAdmin(page);
  await openBillForm(page);
  await page.locator(`select:has(option[value="${room.id}"])`).selectOption(room.id);
  const tenantName = page.locator('label:has-text("Tenant Name") + input');

  await expect(tenantName).toHaveValue('Pricing Tenant');

  const [nextYear, nextMonth] = addMonths(month, 1).split('-').map(Number);
  await page.locator('select:has(option[value="December"])').selectOption(MONTHS[nextMonth - 1]);
  await page.locator('select:has(option[value="2026"])').selectOption(String(nextYear));

  await expect(page.getByText('Room Rent (room price):')).toBeVisible();
  await expect(tenantName).toHaveValue('Tenant');
});

test('bill form blocks saving when the rent cannot be fetched', async ({ page, request }) => {
  await createRoom(request);
  await page.route('**/api/utility-bills/rent-quote**', route =>
    route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ code: 'RENT_UNAVAILABLE', message: 'No price' }) }));
  await signInAsAdmin(page);
  await openBillForm(page);

  await expect(page.getByRole('alert').filter({ hasText: 'No price' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Save & Generate Bill', exact: true })).toBeDisabled();
});

test('booking page shows the price of the chosen move-in month', async ({ page, request }) => {
  const resident = await createResident(request);
  const room = await createRoom(request);
  const month = await currentMonth(request, room.id);
  await setPrice(request, room.id, 6500, addMonths(month, 3));
  await signInAs(page, resident);
  await page.goto(`/booking/${room.id}`);

  await expect(page.getByText(/6,000/).first()).toBeVisible();

  await page.locator('input[type="date"]').first().fill(`${addMonths(month, 3)}-15`);

  await expect(page.getByText('Price for your move-in month')).toBeVisible();
  await expect(page.getByText(/6,500/).first()).toBeVisible();
});
