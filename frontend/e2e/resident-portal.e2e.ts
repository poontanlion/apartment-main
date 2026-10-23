import { test, expect } from '@playwright/test';
import {
  createResident,
  bookAndApproveRoom,
  setTaskStatus,
  getResidentTasks,
  signInAs,
  useEnglish,
  uniqueEmail,
} from './helpers/api';

/**
 * ============================================================================
 *  RESIDENT-PORTAL "TENANT JOURNEY" E2E SUITE
 *  Feature branch: feature/resident-portal-qa
 * ----------------------------------------------------------------------------
 *  E2E-RP-01..04  Access & authentication
 *  E2E-RP-05..08  Reporting a repair (main happy path + validation)
 *  E2E-RP-09..10  Tracking the repair through its lifecycle
 *  E2E-RP-11..12  Data isolation & route protection
 * ============================================================================
 */

// ---------------------------------------------------------------------------
// Access & authentication
// ---------------------------------------------------------------------------
test.describe('Access & authentication', () => {
  test('E2E-RP-01 new resident can register through the UI', async ({ page }) => {
    await useEnglish(page);
    const email = uniqueEmail('signup');

    await page.goto('/register');
    await page.getByPlaceholder('Full Name').fill('Somchai Jaidee');
    await page.getByPlaceholder('Email Address').fill(email);
    await page.getByPlaceholder('Phone Number').fill('0899999999');
    await page.getByPlaceholder('Password', { exact: true }).fill('E2e-pass-123');
    await page.getByPlaceholder('Confirm Password').fill('E2e-pass-123');
    await page.locator('form button[type="submit"]').click();

    await expect(page).toHaveURL(/\/rooms/);
    const stored = await page.evaluate(() => localStorage.getItem('apt_auth_user'));
    expect(JSON.parse(stored!)).toMatchObject({ email, role: 'customer' });
  });

  test('E2E-RP-02 resident can sign in with correct credentials and stays signed in after reload', async ({ page, request }) => {
    const resident = await createResident(request);
    await useEnglish(page);

    await page.goto('/login');
    await page.getByPlaceholder('Email Address').fill(resident.email);
    await page.getByPlaceholder('Password').fill(resident.password);
    await page.locator('form button[type="submit"]').click();

    await expect(page).toHaveURL(/\/rooms/);

    await page.reload();
    const stored = await page.evaluate(() => localStorage.getItem('apt_auth_user'));
    expect(JSON.parse(stored!).email).toBe(resident.email);
  });

  test('E2E-RP-03 wrong password is rejected and the resident stays on the login page', async ({ page, request }) => {
    const resident = await createResident(request);
    await useEnglish(page);

    await page.goto('/login');
    await page.getByPlaceholder('Email Address').fill(resident.email);
    await page.getByPlaceholder('Password').fill('totally-wrong');
    await page.locator('form button[type="submit"]').click();

    await expect(page.getByText('Invalid email or password.')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
    expect(await page.evaluate(() => localStorage.getItem('apt_auth_user'))).toBeNull();
  });

  test('E2E-RP-04 visitor who is not signed in is asked to sign in before using the maintenance portal', async ({ page }) => {
    await useEnglish(page);
    await page.goto('/my-maintenance');

    await expect(page.getByRole('heading', { name: 'Please Sign In' })).toBeVisible();
    await expect(page.locator('main').getByRole('link', { name: 'Sign In' })).toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// Reporting a repair
// ---------------------------------------------------------------------------
test.describe('Reporting a repair', () => {
  test('E2E-RP-05 resident without an approved room cannot report repairs', async ({ page, request }) => {
    const resident = await createResident(request);
    await signInAs(page, resident);

    await page.goto('/my-maintenance');

    await expect(page.getByRole('heading', { name: 'Maintenance Reporting Not Available Yet' })).toBeVisible();
    await expect(page.getByRole('button', { name: /Submit Maintenance Request/ })).toHaveCount(0);
  });

  test('E2E-RP-06 resident with an approved room submits a repair request and sees it in history @mobile', async ({ page, request }) => {
    const resident = await createResident(request);
    const { room } = await bookAndApproveRoom(request, resident);
    await signInAs(page, resident);

    await page.goto('/my-maintenance');
    await expect(page.getByRole('heading', { name: 'In-Room Repair & Maintenance' })).toBeVisible();

    // The unit dropdown only offers the resident's own approved room.
    const unitSelect = page.locator('form select').first();
    await expect(unitSelect.locator('option')).toHaveCount(1);
    await expect(unitSelect).toContainText(room.roomNumber);

    await page.locator('form select').nth(1).selectOption('Plumbing');
    await page.locator('form select').nth(2).selectOption('High');
    await page.locator('textarea').fill('Kitchen sink is leaking under the cabinet');
    await page.getByRole('button', { name: /Submit Maintenance Request/ }).click();

    // Success feedback with a reference number, then the app switches to the history tab.
    await expect(page.getByText(/submitted! Ref: MNT-/)).toBeVisible();
    await expect(page.getByText('Kitchen sink is leaking under the cabinet')).toBeVisible();
    await expect(page.getByText('Pending', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('High', { exact: true }).first()).toBeVisible();

    // The request really reached the backend, linked to this resident and room.
    const tasks = await getResidentTasks(request, resident.email);
    expect(tasks).toHaveLength(1);
    expect(tasks[0]).toMatchObject({
      roomNumber: room.roomNumber,
      category: 'Plumbing',
      priority: 'High',
      status: 'Pending',
    });
    expect(tasks[0].reporterEmail).toBe(`${resident.email[0]}***@e2e.test`);
    expect(tasks[0].reporterEmail).not.toBe(resident.email);
  });

  test('E2E-RP-07 form cannot be submitted without an issue description', async ({ page, request }) => {
    const resident = await createResident(request);
    await bookAndApproveRoom(request, resident);
    await signInAs(page, resident);

    await page.goto('/my-maintenance');
    await page.getByRole('button', { name: /Submit Maintenance Request/ }).click();

    // Still on the form, nothing was sent.
    await expect(page.locator('textarea')).toBeVisible();
    await expect(page.getByText(/submitted! Ref:/)).toHaveCount(0);
    expect(await getResidentTasks(request, resident.email)).toHaveLength(0);
  });

  test('E2E-RP-08 resident can file several requests and sees the newest first', async ({ page, request }) => {
    const resident = await createResident(request);
    await bookAndApproveRoom(request, resident);
    await signInAs(page, resident);
    await page.goto('/my-maintenance');

    for (const text of ['First problem: bulb broken', 'Second problem: door will not lock']) {
      await page.getByRole('button', { name: 'New Request Form' }).click();
      await page.locator('textarea').fill(text);
      await page.getByRole('button', { name: /Submit Maintenance Request/ }).click();
      await expect(page.getByText(text)).toBeVisible();
    }

    const cards = page.locator('p', { hasText: 'problem:' });
    await expect(cards).toHaveCount(2);
    await expect(cards.first()).toContainText('Second problem');
    await expect(cards.last()).toContainText('First problem');
  });
});

// ---------------------------------------------------------------------------
// Tracking a repair through its lifecycle
// ---------------------------------------------------------------------------
test.describe('Tracking a repair', () => {
  test('E2E-RP-09 resident sees the technician and "In Progress" once the admin assigns the job', async ({ page, request }) => {
    const resident = await createResident(request);
    const { room } = await bookAndApproveRoom(request, resident);

    // Resident files a request through the API, then the admin picks it up.
    const created = await (
      await request.post('http://localhost:8080/api/maintenance-tasks', {
        data: {
          roomId: room.id,
          roomNumber: room.roomNumber,
          category: 'Air-con servicing',
          priority: 'Medium',
          description: 'Air conditioner is dripping water',
          reporterName: resident.fullname,
          reporterPhone: resident.phone,
          reporterEmail: resident.email,
        },
      })
    ).json();
    await setTaskStatus(request, created.id, 'In Progress', 'Somsak (Technician)');

    await signInAs(page, resident);
    await page.goto('/my-maintenance');
    await page.getByRole('button', { name: 'Maintenance History' }).click();

    await expect(page.getByText('In Progress', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Somsak (Technician)').first()).toBeVisible();
    await expect(page.getByText(created.taskNo)).toBeVisible();
  });

  test('E2E-RP-10 resident sees the job as resolved after the admin completes it', async ({ page, request }) => {
    const resident = await createResident(request);
    const { room } = await bookAndApproveRoom(request, resident);
    const created = await (
      await request.post('http://localhost:8080/api/maintenance-tasks', {
        data: {
          roomNumber: room.roomNumber,
          category: 'General Repair',
          priority: 'Low',
          description: 'Wardrobe door hinge is loose',
          reporterName: resident.fullname,
          reporterPhone: resident.phone,
          reporterEmail: resident.email,
        },
      })
    ).json();
    await setTaskStatus(request, created.id, 'Completed', 'Somsak (Technician)');

    await signInAs(page, resident);
    await page.goto('/my-maintenance');
    await page.getByRole('button', { name: 'Maintenance History' }).click();

    await expect(page.getByText('Completed', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Done', { exact: true })).toBeVisible();
    await expect(page.getByText('✓')).toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// Data isolation & route protection
// ---------------------------------------------------------------------------
test.describe('Data isolation & route protection', () => {
  test("E2E-RP-11 a resident never sees another resident's repair requests", async ({ page, request }) => {
    const alice = await createResident(request, 'Alice');
    const bob = await createResident(request, 'Bob');
    const { room: aliceRoom } = await bookAndApproveRoom(request, alice);
    await bookAndApproveRoom(request, bob);

    await request.post('http://localhost:8080/api/maintenance-tasks', {
      data: {
        roomNumber: aliceRoom.roomNumber,
        category: 'Plumbing',
        priority: 'High',
        description: 'ALICE-ONLY secret issue',
        reporterName: alice.fullname,
        reporterPhone: alice.phone,
        reporterEmail: alice.email,
      },
    });

    await signInAs(page, bob);
    await page.goto('/my-maintenance');
    await page.getByRole('button', { name: 'Maintenance History' }).click();

    await expect(page.getByText('No Maintenance History')).toBeVisible();
    await expect(page.getByText('ALICE-ONLY secret issue')).toHaveCount(0);
  });

  test('E2E-RP-12 a resident cannot open admin pages, while the admin can sign in and reach the dashboard', async ({ page, request }) => {
    const resident = await createResident(request);
    await signInAs(page, resident);

    await page.goto('/admin/dashboard');
    await expect(page).toHaveURL(/\/profile/);

    // Admin signs in through the real login form (seeded account).
    await page.goto('/login');
    await page.getByPlaceholder('Email Address').fill('admin');
    await page.getByPlaceholder('Password').fill('admin');
    await page.locator('form button[type="submit"]').click();
    await expect(page).toHaveURL(/\/admin\/dashboard/);
  });
});