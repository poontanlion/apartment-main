import { readFile } from 'node:fs/promises';
import { test, expect, APIRequestContext, Download, Page } from '@playwright/test';
import * as XLSX from 'xlsx';
import { API, signInAs } from './helpers/api';
import { currentBillingMonth } from '../src/utils/billingMonth';

const ADMIN = { id: 'admin-e2e', fullname: 'E2E Admin', email: 'admin@e2e.test', role: 'admin' };

async function createRoom(request: APIRequestContext, roomNumber: string, price: number) {
  const response = await request.post(`${API}/rooms`, {
    data: { roomNumber, floor: 1, roomName: 'Report Test Room', roomType: 'Standard Studio',
      status: 'Available', price, capacity: 2, sizeSqm: 28, amenities: '[]' },
  });
  expect(response.ok(), 'create room').toBeTruthy();
  return (await response.json()) as { id: string; roomNumber: string };
}

/** Charges 10 water units at 9, 100 electric units at 4 and a 300 common fee: 790 on top of the rent. */
async function createBill(request: APIRequestContext, room: { id: string; roomNumber: string }, tenantName: string) {
  const response = await request.post(`${API}/utility-bills`, {
    data: {
      roomId: room.id, roomNumber: room.roomNumber, tenantName, billingMonth: currentBillingMonth(),
      prevWaterMeter: 100, currWaterMeter: 110, waterRate: 9,
      prevElectricMeter: 400, currElectricMeter: 500, electricRate: 4, commonFee: 300,
    },
  });
  expect(response.ok(), 'create bill').toBeTruthy();
  return (await response.json()) as { id: string; totalAmount: number };
}

async function markPaid(request: APIRequestContext, billId: string) {
  const response = await request.put(`${API}/utility-bills/${billId}/status`, { data: { status: 'Paid' } });
  expect(response.ok(), 'mark paid').toBeTruthy();
}

/** Two rooms and one tenant: 6,790 paid and 7,790 still owed. Only these bills match the returned search term. */
async function seedBills(request: APIRequestContext) {
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const tenant = `E2E Tenant ${suffix}`;
  const roomA = await createRoom(request, `RPT-${suffix}-A`, 6000);
  const roomB = await createRoom(request, `RPT-${suffix}-B`, 7000);
  const paid = await createBill(request, roomA, tenant);
  const owed = await createBill(request, roomB, tenant);
  expect(paid.totalAmount).toBe(6790);
  expect(owed.totalAmount).toBe(7790);
  await markPaid(request, paid.id);
  return { suffix, tenant, roomA, roomB };
}

async function openReports(page: Page, search?: string) {
  await signInAs(page, ADMIN);
  await page.goto('/admin/reports');
  await expect(page.getByRole('heading', { name: 'Summary Reports' })).toBeVisible();
  if (search) {
    await page.getByLabel('Search', { exact: true }).fill(search);
  }
}

const rows = (page: Page) => page.getByTestId('report-row');
const billed = (page: Page) => page.getByTestId('report-total-billed');
const paid = (page: Page) => page.getByTestId('report-total-paid');
const outstanding = (page: Page) => page.getByTestId('report-total-outstanding');

async function download(page: Page, buttonName: string): Promise<Download> {
  const started = page.waitForEvent('download');
  await page.getByRole('button', { name: buttonName }).click();
  return started;
}

test.describe('Summary reports', () => {
  test('summarizes by unit', async ({ page, request }) => {
    const { suffix, roomA, roomB } = await seedBills(request);
    await openReports(page, suffix);

    await expect(rows(page)).toHaveCount(2);
    await expect(rows(page).filter({ hasText: roomA.roomNumber })).toContainText('฿6,790');
    await expect(rows(page).filter({ hasText: roomB.roomNumber })).toContainText('฿7,790');
    await expect(billed(page)).toHaveText('฿14,580');
    await expect(paid(page)).toHaveText('฿6,790');
    await expect(outstanding(page)).toHaveText('฿7,790');
  });

  test('summarizes by tenant and by month or year', async ({ page, request }) => {
    const { suffix, tenant } = await seedBills(request);
    await openReports(page, suffix);
    await expect(rows(page)).toHaveCount(2);

    await page.getByRole('button', { name: 'Tenant', exact: true }).click();
    await expect(rows(page)).toHaveCount(1);
    await expect(rows(page).first()).toContainText(tenant);
    await expect(rows(page).first()).toContainText('฿14,580');

    await page.getByRole('button', { name: 'Month/Year', exact: true }).click();
    await expect(rows(page)).toHaveCount(1);
    await expect(rows(page).first()).toContainText(currentBillingMonth());
    await expect(rows(page).first()).toContainText('฿14,580');

    await page.getByRole('button', { name: 'Yearly', exact: true }).click();
    await expect(rows(page).first()).toContainText(currentBillingMonth().split(' ')[1]);
    await expect(rows(page).first()).not.toContainText(currentBillingMonth());
  });

  test('filters by status', async ({ page, request }) => {
    const { suffix, roomB } = await seedBills(request);
    await openReports(page, suffix);
    await expect(rows(page)).toHaveCount(2);

    await page.getByRole('group', { name: 'Status' }).getByRole('button', { name: 'Pending' }).click();

    await expect(rows(page)).toHaveCount(1);
    await expect(rows(page).first()).toContainText(roomB.roomNumber);
    await expect(billed(page)).toHaveText('฿7,790');
    await expect(paid(page)).toHaveText('฿0');
  });

  test('exports CSV and Excel', async ({ page, request }) => {
    const { suffix, roomA, roomB } = await seedBills(request);
    await openReports(page, suffix);
    await expect(rows(page)).toHaveCount(2);

    const csvFile = await download(page, 'Export CSV');
    expect(csvFile.suggestedFilename()).toBe('summary-report_unit_all.csv');
    const csv = await readFile(await csvFile.path(), 'utf8');
    expect(csv.startsWith('﻿')).toBe(true);
    expect(csv).toContain(`${roomA.roomNumber},1,6000.00,90.00,400.00,300.00,6790.00,6790.00,0.00`);
    expect(csv).toContain(`${roomB.roomNumber},1,7000.00,90.00,400.00,300.00,7790.00,0.00,7790.00`);

    const excelFile = await download(page, 'Export Excel');
    expect(excelFile.suggestedFilename()).toBe('summary-report_unit_all.xlsx');
    const workbook = XLSX.read(await readFile(await excelFile.path()));
    const sheet: unknown[][] = XLSX.utils.sheet_to_json(workbook.Sheets[workbook.SheetNames[0]], { header: 1 });
    expect(sheet).toContainEqual([roomA.roomNumber, 1, 6000, 90, 400, 300, 6790, 6790, 0]);
    expect(sheet).toContainEqual(['Total', 2, 13000, 180, 800, 600, 14580, 6790, 7790]);
    expect(sheet).toContainEqual([`Search: ${suffix}`]);
  });

  test('warns when a room has more than one bill in a month', async ({ page, request }) => {
    const { suffix, tenant, roomA } = await seedBills(request);
    await createBill(request, roomA, tenant);
    await openReports(page, suffix);

    await expect(page.getByTestId('report-warning')).toContainText('1 room-months have more than one bill');
    await expect(billed(page)).toHaveText('฿21,370');
  });

  test('shows an error instead of zero totals', async ({ page }) => {
    await page.route('**/api/reports/summary**', route => route.fulfill({
      status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'Report failed' }),
    }));
    await openReports(page);

    await expect(page.getByTestId('report-error')).toContainText('Report failed');
    await expect(billed(page)).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Export CSV' })).toBeDisabled();
  });

  test('shows an empty state', async ({ page }) => {
    await openReports(page, 'no-such-room-or-tenant');

    await expect(page.getByTestId('report-empty')).toBeVisible();
    await expect(billed(page)).toHaveText('฿0');
    await expect(page.getByRole('button', { name: 'Export CSV' })).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Export Excel' })).toBeDisabled();
  });

  test('stops asking the server once the report has loaded', async ({ page }) => {
    let calls = 0;
    page.on('request', r => { if (r.url().includes('/api/reports/summary')) calls++; });
    await openReports(page);
    await expect(billed(page)).toBeVisible();
    await page.waitForTimeout(500);
    const callsAfterLoad = calls;

    await page.waitForTimeout(2000);

    expect(calls).toBe(callsAfterLoad);
  });
});
