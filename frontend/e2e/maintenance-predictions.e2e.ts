import { test, expect, Page } from '@playwright/test';
import type { MaintenancePredictionRow } from '../src/types';

const statuses = ['overdue', 'due_soon', 'due_soon', 'within_30', 'within_30', 'on_track'] as const;
const days = [-1, 0, 7, 8, 30, 31];
const rows: MaintenancePredictionRow[] = Array.from({ length: 25 }, (_, index) => ({
  roomId: `rm-A${101 + index}`, roomNumber: `A${101 + index}`, buildingId: 'bld-1', buildingName: 'Victory Tower A',
  taskTypeId: index % 2 ? 'mtt-aircon' : 'mtt-light',
  taskTypeNameTh: index % 2 ? 'ล้าง/ซ่อมแอร์' : 'เปลี่ยนหลอดไฟ',
  taskTypeNameEn: index % 2 ? 'Air-con service' : 'Light bulb replacement',
  predictedDate: index === 24 ? null : index === 0 ? '2026-11-07' : '2026-12-09',
  daysRemaining: index === 24 ? null : days[index] ?? 31,
  status: index === 24 ? 'no_data' : statuses[index] ?? 'on_track', hasAppointment: false, appointmentDate: null,
}));

async function setup(page: Page, options: { empty?: boolean; denial?: number; partial?: boolean; appointmentError?: number } = {}) {
  const currentRows = rows.map(row => ({ ...row }));
  const reminderRows: { id: string; title: string; roomId: string; roomNumber: string; category: string; nextDueDate: string; isActive: boolean; frequency: string }[] = [];
  const state = { get: 0, refresh: 0, appointments: 0, queries: [] as string[], cookies: [] as string[] };
  await page.clock.setFixedTime(new Date('2026-11-07T18:00:00Z'));
  await page.context().addCookies([{ name: 'JSESSIONID', value: 'test-admin-session', url: 'http://127.0.0.1:4175' }]);
  await page.addInitScript(() => {
    localStorage.setItem('apt_auth_user', JSON.stringify({ id: 'usr-admin', role: 'admin', fullname: 'Admin', email: 'admin@example.com' }));
    localStorage.setItem('vr_lang', 'en');
  });
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname.includes('/maintenance/predictions')) state.cookies.push((await route.request().allHeaders()).cookie || '');
    if (url.pathname.endsWith('/appointment')) {
      state.appointments++;
      if (options.appointmentError) return route.fulfill({ status: options.appointmentError, json: {} });
      const roomId = url.pathname.split('/').at(-3);
      const row = currentRows.find(row => row.roomId === roomId)!;
      const cancelling = route.request().method() === 'DELETE';
      row.hasAppointment = !cancelling;
      row.appointmentDate = cancelling ? null : route.request().postDataJSON().date;
      row.predictedDate = row.appointmentDate;
      if (!reminderRows.length) reminderRows.push({ id: 'rem-test', title: `${row.taskTypeNameEn} (Unit ${row.roomNumber})`,
        roomId: row.roomId, roomNumber: row.roomNumber, category: 'Light bulb replacement', nextDueDate: row.appointmentDate!, isActive: true, frequency: 'None' });
      else { reminderRows[0].isActive = !cancelling; if (!cancelling) reminderRows[0].nextDueDate = row.appointmentDate!; }
      return route.fulfill({ json: row });
    }
    if (url.pathname === '/api/reminders') return route.fulfill({ json: reminderRows });
    if (url.pathname.endsWith('/maintenance/predictions/refresh')) {
      state.refresh++;
      return route.fulfill({ json: { success: 25, failure: options.partial ? 2 : 0 } });
    }
    if (!url.pathname.endsWith('/maintenance/predictions')) return route.fulfill({ json: [] });
    state.get++; state.queries.push(url.search);
    if (options.denial) return route.fulfill({ status: options.denial, json: {} });
    const params = url.searchParams;
    let filtered = options.empty ? [] : currentRows.filter(row =>
      (!params.get('roomId') || row.roomId === params.get('roomId')) &&
      (!params.get('taskTypeId') || row.taskTypeId === params.get('taskTypeId')) &&
      (!params.get('buildingId') || row.buildingId === params.get('buildingId')) &&
      (!params.get('q') || row.roomNumber.toLowerCase().includes(params.get('q')!.toLowerCase())));
    const count = (status: string) => filtered.filter(row => row.status === status).length;
    const summary = { overdue: count('overdue'), dueSoon: count('due_soon'), within30: count('due_soon') + count('within_30'), total: filtered.length };
    if (params.get('status')) filtered = filtered.filter(row => params.get('status')!.split(',').includes(row.status));
    return route.fulfill({ json: { predictions: filtered, summary } });
  });
  return state;
}

const tab = (page: Page) => page.getByRole('region', { name: 'Maintenance predictions' });

test('summary cards, status URLs, dropdown cache, pagination, and appointment action', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  const state = await setup(page);
  await page.goto('/admin/maintenance?tab=predictions');
  const region = tab(page);
  await expect(region.getByText('Showing 1–20 of 25 items')).toBeVisible();
  await expect(region.getByRole('columnheader')).toHaveCount(4);
  await expect(region.getByRole('button', { name: 'Set appointment' }).first()).toBeEnabled();
  await expect(region.getByRole('button', { name: 'All predictions' })).toContainText('25');
  await region.getByRole('button', { name: 'Next', exact: true }).click();
  await expect(region.getByText('Showing 21–25 of 25 items')).toBeVisible();
  const baselineReads = state.queries.filter(query => !query).length;
  const card = region.getByRole('button', { name: /^Within 30 days/ });
  await card.click();
  await expect(page).toHaveURL(/status=due_soon%2Cwithin_30/);
  await expect(region.getByText('Showing 1–4 of 4 items')).toBeVisible();
  await expect(card).toHaveAttribute('aria-pressed', 'true');
  await expect(region.getByRole('button', { name: 'All predictions' })).toContainText('25');
  await expect(region.getByLabel('Room', { exact: true }).locator('option')).toHaveCount(26);
  expect(state.queries.filter(query => !query).length).toBe(baselineReads);
  await card.click();
  await expect(region.getByText('Showing 1–20 of 25 items')).toBeVisible();
  expect(state.refresh).toBe(0);
  expect(state.cookies.every(cookie => cookie.includes('JSESSIONID=test-admin-session'))).toBe(true);
  expect(errors).toEqual([]);
});

test('deep links and tab switches preserve building and other query parameters', async ({ page }) => {
  await setup(page);
  await page.goto('/admin/maintenance?tab=predictions&roomId=rm-A101&status=due_soon,within_30&buildingId=bld-1&keep=1');
  await expect(tab(page).getByText('No maintenance predictions yet')).toBeVisible();
  await expect(tab(page).getByLabel('Room', { exact: true })).toHaveValue('rm-A101');
  for (const name of ['Active Tasks', 'Supply Stock', 'Per-Unit Logs', 'Scheduled Reminders']) {
    await page.getByRole('button', { name, exact: true }).click();
    await expect(tab(page)).toHaveCount(0);
  }
  await page.getByRole('button', { name: 'Maintenance predictions', exact: true }).click();
  await expect(page).toHaveURL(/buildingId=bld-1/);
  await expect(page).toHaveURL(/keep=1/);
  await expect(page).toHaveURL(/roomId=rm-A101/);
  await page.locator('select').first().selectOption('bld-2');
  await expect(tab(page).getByText('No maintenance predictions yet')).toBeVisible();
  await tab(page).getByRole('button', { name: 'Clear filters' }).click();
  await expect(tab(page).getByText('Showing 1–20 of 25 items')).toBeVisible();
  await expect(page).toHaveURL(/keep=1/);
  expect(new URL(page.url()).searchParams.has('buildingId')).toBe(false);
});

test('generic server error offers retry and recovers', async ({ page }) => {
  await setup(page);
  let fail = true;
  await page.route('**/api/maintenance/predictions*', route => fail
    ? route.fulfill({ status: 500, json: {} }) : route.fallback());
  await page.goto('/admin/maintenance?tab=predictions');
  await expect(tab(page).getByRole('alert')).toContainText('Unable to load maintenance predictions');
  fail = false;
  await tab(page).getByRole('button', { name: 'Try again' }).click();
  await expect(tab(page).getByText('Showing 1–20 of 25 items')).toBeVisible();
});

test('Thai text uses translated keys and language switch updates task names', async ({ page }) => {
  await setup(page);
  await page.goto('/admin/maintenance?tab=predictions&roomId=rm-A101');
  await expect(tab(page).getByText('Showing 1–1 of 1 items')).toBeVisible();
  await page.getByRole('button', { name: /English \(EN\)/ }).click();
  const region = page.getByRole('region', { name: 'คาดการณ์การบำรุงรักษา' });
  await expect(region.getByRole('cell', { name: /เปลี่ยนหลอดไฟ/ })).toBeVisible();
  await expect(region.getByText('เลยกำหนด 1 วัน').first()).toBeVisible();
  await expect(region.getByRole('button', { name: 'ตั้งวันนัด' }).first()).toBeEnabled();
  expect(await region.innerText()).not.toContain('mnt.predictions.');
  await page.screenshot({ path: 'test-results/predictions-desktop-thai.png', fullPage: true });
});

test('refresh automatically once for an empty unfiltered table and never loops', async ({ page }) => {
  const state = await setup(page, { empty: true });
  await page.goto('/admin/maintenance?tab=predictions');
  await expect(tab(page).getByText('No maintenance predictions yet')).toBeVisible();
  await page.waitForTimeout(400);
  expect(state.refresh).toBe(1);
  await tab(page).getByRole('button', { name: /^Overdue/ }).click();
  await expect(tab(page).getByText('No maintenance predictions yet')).toBeVisible();
  expect(state.refresh).toBe(1);
});

test('an empty filtered deep link never triggers automatic refresh', async ({ page }) => {
  const state = await setup(page, { empty: true });
  await page.goto('/admin/maintenance?tab=predictions&roomId=rm-A101');
  await expect(tab(page).getByText('No maintenance predictions yet')).toBeVisible();
  expect(state.refresh).toBe(0);
});

for (const status of [401, 403]) test(`HTTP ${status} shows admin access message with retry`, async ({ page }) => {
  await setup(page, { denial: status });
  await page.goto('/admin/maintenance?tab=predictions');
  await expect(tab(page).getByRole('alert')).toContainText('Please sign in again as an administrator');
  await expect(tab(page).getByRole('button', { name: 'Try again' })).toBeVisible();
});

test('manual refresh displays partial failures', async ({ page }) => {
  const state = await setup(page, { partial: true });
  await page.goto('/admin/maintenance?tab=predictions');
  await expect(tab(page).getByText('Showing 1–20 of 25 items')).toBeVisible();
  await tab(page).getByRole('button', { name: 'Refresh predictions', exact: true }).click();
  await expect(page.getByText('Updated 25 items · 2 items failed')).toBeVisible();
  expect(state.refresh).toBe(1);
});

test('mobile dark layout uses cards and has no horizontal page overflow', async ({ page }) => {
  await setup(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(() => localStorage.setItem('vr_theme', 'dark'));
  await page.goto('/admin/maintenance?tab=predictions&roomId=rm-A101');
  await expect(tab(page).getByRole('article')).toHaveCount(1);
  await expect(tab(page).getByRole('article').getByText('07/11/2026')).toBeVisible();
  await expect(page.locator('html')).toHaveClass(/dark/);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: 'test-results/predictions-mobile-dark.png', fullPage: true });
});

test('search is debounced and stale requests cannot overwrite newer filters', async ({ page }) => {
  await setup(page);
  let releaseOld: (() => void) | undefined;
  const oldGate = new Promise<void>(resolve => { releaseOld = resolve; });
  await page.route('**/api/maintenance/predictions?*', async route => {
    const query = new URL(route.request().url()).searchParams.get('q');
    if (query !== 'old') return route.fallback();
    await oldGate;
    await route.fulfill({ json: { predictions: rows, summary: { overdue: 1, dueSoon: 2, within30: 4, total: 25 } } }).catch(() => {});
  });
  await page.goto('/admin/maintenance?tab=predictions');
  await expect(tab(page).getByText('Showing 1–20 of 25 items')).toBeVisible();
  const search = tab(page).getByRole('searchbox');
  await search.fill('old');
  await page.waitForRequest(request => new URL(request.url()).searchParams.get('q') === 'old');
  await search.fill('A101');
  await expect(tab(page).getByText('Showing 1–1 of 1 items')).toBeVisible();
  releaseOld!();
  await page.waitForTimeout(200);
  await expect(tab(page).getByText('Showing 1–1 of 1 items')).toBeVisible();
});

test('set, edit, confirm cancellation, and see the reminder in the existing tab', async ({ page }) => {
  const state = await setup(page);
  await page.goto('/admin/maintenance?tab=predictions&roomId=rm-A101&keep=1');
  await tab(page).getByRole('button', { name: 'Set appointment' }).first().click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByLabel('Appointment date')).toHaveValue('2026-11-07');
  await expect(dialog.getByLabel('Appointment date')).toHaveAttribute('min', '2026-11-08');
  await dialog.getByLabel('Appointment date').fill('2026-11-20');
  await dialog.getByRole('button', { name: 'Confirm appointment', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(tab(page).getByRole('button', { name: 'Appointment set' }).first()).toBeVisible();
  await expect(tab(page).getByText('20/11/2026').first()).toBeVisible();
  await expect(page).toHaveURL(/roomId=rm-A101.*keep=1/);
  await page.getByRole('button', { name: 'Scheduled Reminders', exact: true }).click();
  await expect(page.getByText('Light bulb replacement (Unit A101)', { exact: true })).toBeVisible();
  await expect(page.getByText('2026-11-20', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Maintenance predictions', exact: true }).click();
  await tab(page).getByRole('button', { name: 'Appointment set' }).first().click();
  await expect(dialog.getByLabel('Appointment date')).toHaveValue('2026-11-20');
  await dialog.getByLabel('Appointment date').fill('2026-11-21');
  await dialog.getByRole('button', { name: 'Confirm appointment', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await tab(page).getByRole('button', { name: 'Appointment set' }).first().click();
  await dialog.getByRole('button', { name: 'Cancel appointment', exact: true }).click();
  await expect(dialog.getByText('Are you sure you want to cancel this appointment?')).toBeVisible();
  expect(state.appointments).toBe(2);
  await dialog.getByRole('button', { name: 'Confirm cancellation' }).click();
  await expect(dialog).toHaveCount(0);
  await expect(tab(page).getByRole('button', { name: 'Set appointment' }).first()).toBeVisible();
  expect(state.appointments).toBe(3);
});

test('past date is blocked, no-data starts empty, and Escape closes modal', async ({ page }) => {
  const state = await setup(page);
  await page.goto('/admin/maintenance?tab=predictions&roomId=rm-A125');
  await tab(page).getByRole('button', { name: 'Set appointment' }).first().click();
  const dialog = page.getByRole('dialog');
  const input = dialog.getByLabel('Appointment date');
  await expect(input).toHaveValue('');
  await input.fill('2026-11-07');
  await dialog.getByRole('button', { name: 'Confirm appointment', exact: true }).click();
  expect(state.appointments).toBe(0);
  await expect(dialog).toBeVisible();
  await input.press('Escape');
  await expect(dialog).toHaveCount(0);
});

for (const code of [400, 403, 500]) test(`appointment HTTP ${code} preserves the entered date`, async ({ page }) => {
  await setup(page, { appointmentError: code });
  await page.goto('/admin/maintenance?tab=predictions&roomId=rm-A101');
  await tab(page).getByRole('button', { name: 'Set appointment' }).first().click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Appointment date').fill('2026-11-20');
  await dialog.getByRole('button', { name: 'Confirm appointment', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText(code === 400 ? 'Choose a valid date' : code === 403 ? 'Please sign in again' : 'Unable to update');
  await expect(dialog.getByLabel('Appointment date')).toHaveValue('2026-11-20');
  await expect(dialog.getByRole('button', { name: 'Confirm appointment', exact: true })).toBeEnabled();
});

test('saving prevents duplicate clicks and preserves the current page', async ({ page }) => {
  const state = await setup(page);
  let release: (() => void) | undefined;
  const gate = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/appointment', async route => { await gate; await route.fallback(); });
  await page.goto('/admin/maintenance?tab=predictions');
  await tab(page).getByRole('button', { name: 'Next', exact: true }).click();
  await expect(tab(page).getByText('Showing 21–25 of 25 items')).toBeVisible();
  await tab(page).getByRole('button', { name: 'Set appointment' }).first().click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Appointment date').fill('2026-11-20');
  await dialog.getByRole('button', { name: 'Confirm appointment', exact: true }).click();
  await expect(dialog.getByRole('button', { name: 'Saving' })).toBeDisabled();
  release!();
  await expect(dialog).toHaveCount(0);
  await expect(tab(page).getByText('Showing 21–25 of 25 items')).toBeVisible();
  expect(state.appointments).toBe(1);
});

test('mobile dark appointment modal fits the viewport', async ({ page }) => {
  await setup(page);
  await page.setViewportSize({ width: 390, height: 700 });
  await page.addInitScript(() => localStorage.setItem('vr_theme', 'dark'));
  await page.goto('/admin/maintenance?tab=predictions&roomId=rm-A101');
  await tab(page).getByRole('button', { name: 'Set appointment' }).first().click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  const box = await dialog.boundingBox();
  expect(box!.x).toBeGreaterThanOrEqual(0); expect(box!.width).toBeLessThanOrEqual(390); expect(box!.height).toBeLessThanOrEqual(700);
  await page.screenshot({ path: 'test-results/appointment-mobile-dark.png', fullPage: true });
});
