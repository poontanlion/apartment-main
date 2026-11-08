import { test, expect, type Page } from '@playwright/test';

// All identities and documents are synthetic; no backend is contacted.
const names = ['Kant Somchai', 'Somchai Jaidee', 'กานต์ สมมติ'];
const tenants = names.map((fullname,i)=>({id:`t${i}`,fullname,phone:`080000020${4+i}`,unitNumber:['204','205','214'][i],email:`test${i}@example.invalid`,createdAt:'2026-01-01'}));
const rooms = tenants.map((t,i)=>({id:`r${i}`,roomNumber:t.unitNumber,currentTenantId:t.id,currentTenantName:t.fullname,price:5000,status:'Occupied'}));
const leases = tenants.map((t,i)=>({id:`lease-${i}`,tenantId:t.id,tenantName:t.fullname,tenantPhone:t.phone,roomId:`r${i}`,roomNumber:t.unitNumber,checkInDate:'2026-01-01',checkOutDate:'2027-01-01',status:'Active',rentAmount:5000,depositAmount:10000,billingCycle:'monthly',scannedDocument:i===0?'data:application/pdf;base64,AA==':undefined}));
const bills = [{id:'bill-0',invoiceNo:'INV-DEMO',leaseId:'lease-0',roomId:'r0',roomNumber:'204',tenantName:names[0],billingMonth:'November 2026',totalAmount:5000,status:'Paid'}];

async function setup(page:Page, path:string) {
  await page.addInitScript(()=>{
    localStorage.setItem('apt_auth_user',JSON.stringify({id:'admin-demo',role:'admin',fullname:'Demo Admin',email:'admin@example.invalid'}));
    localStorage.setItem('vr_lang','en');
  });
  await page.route('**/api/**',route=>{
    const data:Record<string,unknown> = {
      '/api/tenants':tenants,'/api/leases':leases,'/api/rooms':rooms,'/api/utility-bills':bills,
      '/api/maintenance-tasks':[{id:'task0',taskNo:'TASK-DEMO',roomNumber:'204',category:'Plumbing',description:'Faucet leak',createdAt:'2026-01-01',totalCost:0}],
    };
    return route.fulfill({json:data[new URL(route.request().url()).pathname] ?? []});
  });
  await page.goto(path);
}
const input = (page:Page)=>page.getByRole('textbox',{name:'Search tenant names'}).last();

test('tenant search, numeric safety, debounce, clear, Thai input and original order',async({page})=>{
  await setup(page,'/admin/tenants');
  const cards = page.locator('h4');
  await expect(cards).toHaveText(names);
  for(const q of ['krant','kart']) {
    await input(page).fill(q);
    await expect(cards).toHaveText(['Kant Somchai']);
    await expect(cards.locator('mark')).toHaveText('Kant');
    await expect(page.getByRole('status').filter({hasText:'No direct matches'})).toBeVisible();
  }
  for(const q of ['204','0800000204','Kant Somchai']) {
    await input(page).fill(q); await expect(cards).toHaveText(['Kant Somchai']);
  }
  await input(page).fill('zzzzzzzzzz');
  await expect(page.getByRole('status').filter({hasText:'No results for'})).toBeVisible();
  await page.getByRole('button',{name:'Clear search',exact:true}).click();
  await expect(cards).toHaveText(names);
  await input(page).fill('กานต์ สมมติ');
  await expect(input(page)).toHaveValue('กานต์ สมมติ');
  await expect(cards).toHaveText(['กานต์ สมมติ']);
  await input(page).press('Escape'); await expect(input(page)).toHaveValue('');
  await expect(cards).toHaveText(names);
  // Browser clock exercises the debounce without introducing a DOM test library.
  await page.clock.install({time:new Date('2026-01-01T00:00:00Z')});
  await page.clock.pauseAt(new Date('2026-01-01T00:00:01Z'));
  await input(page).fill('kart');
  await page.clock.runFor(249); expect(await cards.count()).toBe(3);
  await page.clock.runFor(1); await expect(cards).toHaveCount(1);
});

test('language, theme and lossless safe highlighting',async({page})=>{
  await setup(page,'/admin/tenants');
  await input(page).fill('krant'); await expect(page.locator('h4')).toHaveText(['Kant Somchai']);
  for(const dark of [false,true]) {
    await page.evaluate(dark=>document.documentElement.classList.toggle('dark',dark),dark);
    for(const element of [input(page),page.locator('h4 mark')]) {
      await expect(element).toBeVisible();
      expect(await element.evaluate(el=>getComputedStyle(el).backgroundColor)).not.toBe('rgba(0, 0, 0, 0)');
    }
  }
  await expect(page.locator('h4')).toHaveText('Kant Somchai');
  await page.getByRole('button',{name:/English \(EN\)/}).click();
  await expect(page.getByRole('textbox',{name:'ค้นหาชื่อผู้เช่า',exact:true}).last()).toHaveAttribute('placeholder','ค้นหาชื่อผู้เช่า…');
  await expect(page.getByRole('textbox',{name:'ค้นหาชื่อผู้เช่า',exact:true}).last()).toHaveAttribute('title','ค้นหาชื่อผู้เช่าได้แม้พิมพ์คลาดเคลื่อนเล็กน้อย');
  await expect(page.getByRole('button',{name:'ล้างคำค้น',exact:true})).toBeVisible();
  await expect(page.getByText('ไม่พบตรงตัว แสดงผลที่ใกล้เคียง', {exact:true})).toBeVisible();
  await expect(page.getByText(/^smartSearch\./)).toHaveCount(0);
});

test('lease search is independent and filtered actions retain the lease identity',async({page})=>{
  await setup(page,'/admin/tenants');
  const leaseInput = page.getByRole('textbox',{name:'Search tenant names'}).first();
  const rows = page.locator('tbody tr');
  const original = await rows.allTextContents();
  const summary = page.getByText('3 Active Leases',{exact:true});
  await expect(summary).toBeVisible();
  for(const query of ['krant','kart','204']) {
    await leaseInput.fill(query); await expect(rows).toHaveCount(1);
    await expect(rows).toContainText('Kant Somchai');
    if(query !== '204') await expect(rows.locator('mark')).toHaveText('Kant');
    await expect(page.locator('h4')).toHaveText(names);
    await expect(summary).toBeVisible();
  }
  await input(page).fill('Somchai Jaidee'); await expect(page.locator('h4')).toHaveText(['Somchai Jaidee']);
  await expect(leaseInput).toHaveValue('204'); await expect(rows).toHaveCount(1);
  await leaseInput.fill('zzzzzzzzzz'); await expect(rows).toHaveCount(0);
  await expect(page.getByRole('status').filter({hasText:'No results for'})).toBeVisible();
  await leaseInput.fill('kart'); await expect(rows).toHaveCount(1);
  await expect(page.getByRole('status').filter({hasText:'No direct matches'})).toBeVisible();
  await leaseInput.press('Escape'); await expect(rows).toHaveCount(3);
  expect(await rows.allTextContents()).toEqual(original);
  // Pick a non-first record: an index-based action would target lease-0 incorrectly.
  await leaseInput.fill('205'); await expect(rows).toHaveCount(1);
  await rows.getByRole('button',{name:'PDF Contract'}).click();
  await expect(page.getByText('Contract Ref: #LEASE-1',{exact:true})).toBeVisible();
  await page.keyboard.press('Escape');
  await rows.getByRole('button',{name:'Attach Scan'}).click();
  await expect(page.getByText('เอกสารสแกนสัญญาเช่า - ห้อง 205',{exact:true})).toBeVisible();
  await expect(page.getByText(/ผู้เช่า: Somchai Jaidee \| ระยะเวลา:/)).toBeVisible();
  await page.keyboard.press('Escape');
  const request = page.waitForRequest(req=>req.url().endsWith('/api/leases/lease-1/terminate') && req.method()==='PUT');
  page.once('dialog',dialog=>dialog.accept());
  await rows.getByRole('button',{name:'Terminate',exact:true}).click();
  await request;
});

for(const width of [1280,1024,768,375]) {
  for(const path of ['/admin/tenants','/admin/documents']) {
    test(`search layout ${width}px ${path}`,async({page},testInfo)=>{
      await page.setViewportSize({width,height:1000});
      await setup(page,path);
      await expect(page.getByRole('textbox',{name:'Search tenant names'}).first()).toBeVisible();
      const boxes = page.getByRole('textbox',{name:'Search tenant names'});
      for(let i=0;i<await boxes.count();i++) {
        const field = boxes.nth(i);
        const count = field.locator('../..').locator('[aria-live="polite"]');
        const a = (await field.boundingBox())!;
        const b = (await count.boundingBox())!;
        const container = (await field.locator('..').boundingBox())!;
        expect(container.width).toBeGreaterThanOrEqual(200);
        expect(b.x).toBeGreaterThanOrEqual(a.x+a.width);
        expect(b.y).toBeGreaterThanOrEqual(container.y);
        expect(b.y+b.height).toBeLessThanOrEqual(container.y+container.height);
        expect(await count.evaluate(el=>Number(getComputedStyle(el).fontWeight))).toBeGreaterThanOrEqual(700);
        expect(a.x).toBeGreaterThanOrEqual(0); expect(a.x+a.width).toBeLessThanOrEqual(width);
        // Measure actual font and padding to ensure both translated placeholders fit.
        expect(await field.evaluate(el=>{
          const node = el as HTMLInputElement;
          const style = getComputedStyle(node);
          const canvas = document.createElement('canvas');
          const context = canvas.getContext('2d')!; context.font = style.font;
          const available = node.clientWidth-parseFloat(style.paddingLeft)-parseFloat(style.paddingRight);
          return ['Search tenant name…','ค้นหาชื่อผู้เช่า…'].every(text=>context.measureText(text).width<=available);
        })).toBe(true);
      }
      if(path.endsWith('documents')) {
        const filters = page.getByRole('button',{name:'ทั้งหมด',exact:true}).locator('..');
        const filterBox = (await filters.boundingBox())!;
        expect(filterBox.x+filterBox.width).toBeLessThanOrEqual(width);
        for(const button of await filters.getByRole('button').all()) {
          expect(await button.evaluate(el=>el.scrollWidth<=el.clientWidth)).toBe(true);
        }
      }
      if(width===1280 || width===375) await page.screenshot({path:testInfo.outputPath(`layout-${width}.png`),fullPage:true});
    });
  }
}

test('documents preserve fields, filters, tabs and handle maintenance without tenant',async({page})=>{
  await setup(page,'/admin/documents');
  const rows = page.locator('tbody tr');
  await expect(rows).toHaveCount(3);
  for(const q of ['krant','kart']) {
    await input(page).fill(q); await expect(rows).toHaveCount(1);
    await expect(rows.locator('mark')).toHaveText('Kant');
  }
  await page.getByRole('button',{name:'ยังไม่แนบ',exact:true}).click(); await expect(rows).toHaveCount(0);
  await page.getByRole('button',{name:'มีไฟล์สแกนแล้ว',exact:true}).click(); await expect(rows).toHaveCount(1);
  await page.getByRole('button',{name:'ทั้งหมด',exact:true}).click();
  await page.getByRole('button',{name:/3\. Receipts/}).click();
  await expect(input(page)).toHaveValue('kart'); await expect(rows).toHaveCount(1);
  await expect(page.getByText('1 results',{exact:true})).toBeVisible();
  for(const q of ['krant','INV-DEMO','November 2026','204']) { await input(page).fill(q); await expect(rows).toHaveCount(1); }
  await page.getByRole('button',{name:/1\. Leases/}).click();
  await input(page).fill('204'); await expect(rows).toHaveCount(1); await expect(rows).toContainText('Unit 204');
  await input(page).fill('lease-0'); await expect(rows).toHaveCount(1);
  await page.getByRole('button',{name:/2\. Maintenance/}).click();
  for(const q of ['TASK-DEMO','Plumbing','Faucet','204']) {await input(page).fill(q); await expect(rows).toHaveCount(1);}
  // Maintenance deliberately has no tenant association; searching a name is safe.
  await input(page).fill('krant'); await expect(rows).toHaveCount(0);
  await expect(page.getByRole('status').filter({hasText:'No results for'})).toBeVisible();
  await input(page).press('Escape'); await expect(rows).toHaveCount(1);
});
