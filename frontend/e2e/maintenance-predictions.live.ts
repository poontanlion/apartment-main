import { test, expect } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import type { MaintenancePredictionRow, MaintenancePredictionResponse } from '../src/types';

test('live PostgreSQL: demo, appointment/reminders, completion, digest, and dashboard agree', async ({ page }) => {
  // No route interception or fabricated session: real login, cookie and API throughout.
  await page.clock.setFixedTime(new Date('2026-11-07T18:00:00Z'));
  await page.addInitScript(() => { if (!localStorage.getItem('vr_lang')) localStorage.setItem('vr_lang', 'en'); });
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/login');
  await page.getByPlaceholder('Email Address').fill('smoke-admin@example.com');
  await page.getByPlaceholder('Password', { exact: true }).fill('prediction-smoke-password');
  await page.locator('form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/admin\/dashboard/);
  expect((await page.context().cookies()).some(cookie => cookie.name === 'JSESSIONID')).toBe(true);

  const read = async (): Promise<MaintenancePredictionResponse> => {
    const response = await page.request.get('/api/maintenance/predictions'); expect(response.status()).toBe(200); return response.json();
  };
  const initial = await read();
  const counts = Object.fromEntries(['overdue', 'due_soon', 'within_30', 'on_track', 'no_data']
    .map(status => [status, initial.predictions.filter(row => row.status === status).length]));
  expect(counts.overdue).toBeGreaterThanOrEqual(1); expect(counts.due_soon).toBeGreaterThanOrEqual(1);
  expect(counts.within_30).toBeGreaterThanOrEqual(2); expect(counts.on_track).toBeGreaterThanOrEqual(2); expect(counts.no_data).toBeGreaterThanOrEqual(1);
  const fields = ['roomId', 'roomNumber', 'buildingId', 'buildingName', 'taskTypeId', 'taskTypeNameTh', 'taskTypeNameEn', 'predictedDate', 'daysRemaining', 'status', 'hasAppointment', 'appointmentDate'].sort();
  const assertPublicRow = (row: MaintenancePredictionRow) => expect(Object.keys(row).sort()).toEqual(fields);
  initial.predictions.forEach(assertPublicRow);
  expect(initial.predictions.some(row => row.taskTypeId === 'mtt-general' || row.taskTypeNameEn === 'General Repair')).toBe(false);
  const leaseBased = initial.predictions.find(row => row.roomId === 'rm-A101' && row.taskTypeId === 'mtt-light')!;
  expect(leaseBased.predictedDate).toBe('2027-01-15'); expect(leaseBased.daysRemaining).not.toBe(365);
  expect(initial.predictions.some(row => row.predictedDate === null && row.daysRemaining === null)).toBe(true);
  const chosen = initial.predictions.find(row => row.status === 'due_soon' && row.taskTypeId === 'mtt-light')!;
  expect(chosen).toBeTruthy();
  await page.goto(`/admin/maintenance?tab=predictions&roomId=${chosen.roomId}&taskTypeId=${chosen.taskTypeId}`);
  const region = page.getByRole('region', { name: 'Maintenance predictions' });
  await expect(region.getByRole('columnheader')).toHaveCount(4);
  await expect(region.getByText('Showing 1–1 of 1 items')).toBeVisible();
  expect(await region.innerText()).not.toMatch(/confidence|weighted|history count|default interval|วิธีคำนวณ|ความมั่นใจ|จำนวนประวัติ/i);
  await region.getByRole('button', { name: 'Set appointment', exact: true }).first().click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Appointment date').fill('2026-11-20');
  const putResponse = page.waitForResponse(response => response.request().method() === 'PUT' && response.url().endsWith('/appointment'));
  await dialog.getByRole('button', { name: 'Confirm appointment', exact: true }).click();
  const put = await (await putResponse).json(); assertPublicRow(put); expect(put.hasAppointment).toBe(true);
  await expect(dialog).toHaveCount(0);
  const reminders = await (await page.request.get('/api/reminders')).json();
  const appointment = reminders.find((row: { roomId: string; category: string; isActive: boolean }) => row.roomId === chosen.roomId && row.category === 'Light bulb replacement' && row.isActive);
  expect(appointment.nextDueDate).toBe('2026-11-20'); expect(appointment.frequency).toBe('None');
  await page.getByRole('button', { name: 'Scheduled Reminders', exact: true }).click();
  await expect(page.getByText(appointment.title, { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Maintenance predictions', exact: true }).click();
  await region.getByRole('button', { name: 'Appointment set', exact: true }).first().click();
  await dialog.getByRole('button', { name: 'Cancel appointment', exact: true }).click();
  const deleteResponse = page.waitForResponse(response => response.request().method() === 'DELETE' && response.url().endsWith('/appointment'));
  await dialog.getByRole('button', { name: 'Confirm cancellation' }).click();
  const cancelled = await (await deleteResponse).json(); assertPublicRow(cancelled); expect(cancelled.hasAppointment).toBe(false);
  await expect(dialog).toHaveCount(0);
  const afterCancel = (await (await page.request.get('/api/reminders')).json()).find((row: { id: string }) => row.id === appointment.id);
  expect(afterCancel.isActive).toBe(false);

  const path = `/api/maintenance/predictions/${chosen.roomId}/${chosen.taskTypeId}/appointment`;
  expect((await page.request.put(path, { data: { date: '2026-11-20' } })).status()).toBe(200);
  const create = await page.request.post('/api/maintenance-tasks', { data: {
    roomId: chosen.roomId, roomNumber: chosen.roomNumber, category: 'Light bulb replacement', status: 'Pending', description: 'Live smoke completion',
  } });
  expect(create.status()).toBe(200); const task = await create.json();
  expect((await page.request.put(`/api/maintenance-tasks/${task.id}`, { data: { status: 'Completed' } })).status()).toBe(200);
  const completed = (await read()).predictions.find(row => row.roomId === chosen.roomId && row.taskTypeId === chosen.taskTypeId)!;
  assertPublicRow(completed); expect(completed.predictedDate).toBe('2027-11-08'); expect(completed.appointmentDate).toBe('2027-11-08');
  const closedReminder = (await (await page.request.get('/api/reminders')).json()).find((row: { id: string }) => row.id === appointment.id);
  expect(closedReminder.lastTriggered).toBe('2026-11-08');
  const logResponse = await page.request.get('/api/maintenance-logs'); expect(logResponse.status()).toBe(200);
  expect((await logResponse.json()).find((row: { taskNo: string }) => row.taskNo === task.taskNo).completedDate).toBe('2026-11-08');
  const supply = await page.request.post('/api/supplies', { data: { name: 'Live smoke supply', unitName: 'pcs', stockQuantity: 5, unitCost: 10 } });
  expect(supply.status()).toBe(200); const supplyRow = await supply.json();
  expect((await page.request.put(`/api/supplies/${supplyRow.id}`, { data: { stockQuantity: 7 } })).status()).toBe(200);
  expect((await page.request.get('/api/supplies')).status()).toBe(200);
  await Promise.all([page.request.post('/api/maintenance/predictions/refresh'), page.request.post('/api/maintenance/predictions/refresh')]);
  const notifications = await (await page.request.get('/api/notifications')).json();
  const digest = notifications.filter((row: { title: string }) => row.title === 'Maintenance Predictions Daily Digest');
  expect(digest).toHaveLength(1); expect(digest[0].userId).toBe('system');
  expect(digest[0].message).not.toMatch(/confidence|weighted|history|interval|override|computed/i);

  const final = await read();
  await page.goto('/admin/dashboard');
  const card = page.getByRole('region', { name: 'Upcoming maintenance' });
  await expect(card.getByRole('link', { name: `Overdue ${final.summary.overdue}`, exact: true })).toBeVisible();
  await expect(card.getByRole('link', { name: `Within 7 days ${final.summary.dueSoon}`, exact: true })).toBeVisible();
  await card.getByRole('link', { name: `Within 30 days ${final.summary.within30}`, exact: true }).click();
  await expect(page).toHaveURL(/status=due_soon%2Cwithin_30/);
  await expect(region.getByRole('button', { name: /^Within 30 days/ })).toContainText(String(final.summary.within30));
  await expect(region.getByText(`${final.summary.within30} items · Sorted by due date`)).toBeVisible();
  await page.screenshot({ path: 'test-results/live/prediction-smoke-desktop.png', fullPage: true });
  await page.evaluate(() => { localStorage.setItem('vr_lang', 'th'); localStorage.setItem('vr_theme', 'dark'); });
  await page.setViewportSize({ width: 390, height: 844 }); await page.reload();
  const thaiRegion = page.getByRole('region', { name: 'คาดการณ์การบำรุงรักษา' });
  await expect(thaiRegion.getByRole('article').first()).toBeVisible(); await expect(page.locator('html')).toHaveClass(/dark/);
  expect(await thaiRegion.innerText()).not.toContain('mnt.predictions.');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: 'test-results/live/prediction-smoke-mobile-thai-dark.png', fullPage: true });
  await thaiRegion.getByRole('button', { name: 'ตั้งวันนัด', exact: true }).first().click();
  await expect(page.getByRole('dialog').getByLabel('วันนัด')).toBeVisible();
  await expect(page.getByRole('dialog').getByRole('button', { name: 'ยืนยัน', exact: true })).toBeVisible();
  await page.screenshot({ path: 'test-results/live/prediction-smoke-modal-thai-dark.png', fullPage: true });
  await page.getByRole('dialog').getByLabel('วันนัด').press('Escape');
  expect(errors).toEqual([]);
  await mkdir('test-results/live', { recursive: true });
  await writeFile('test-results/live/prediction-smoke.json', JSON.stringify({ live: true, initialSummary: initial.summary, initialStatuses: counts,
    leaseBased, put, cancelled, completed, reminder: closedReminder, digest, finalSummary: final.summary, pageErrors: errors }, null, 2));
});
