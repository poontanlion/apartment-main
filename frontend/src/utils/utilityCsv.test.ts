import { describe, it, expect } from 'vitest';
import type { Room, UtilityBill } from '../types';
import { buildImportPlan, invoiceNoFor, normalizeBillingMonth, parseCsv } from './utilityCsv';

const rooms = [
  { id: 'r1', roomNumber: 'A101', floor: 1 },
  { id: 'r2', roomNumber: 'A102', floor: 1 },
] as Room[];

const existing = [
  {
    id: 'b1', roomId: 'r1', roomNumber: 'A101', billingMonth: 'July 2026',
    prevWaterMeter: 90, currWaterMeter: 100, prevElectricMeter: 380, currElectricMeter: 400,
  },
] as UtilityBill[];

const header = 'roomNumber,billingMonth,currWater,currElectric,prevWater,prevElectric\n';

describe('parseCsv', () => {
  it('reads plain rows and a BOM', () => {
    expect(parseCsv('\uFEFFa,b\n1,2\r\n3,4')).toEqual([['a', 'b'], ['1', '2'], ['3', '4']]);
  });
  it('reads quoted cells that hold commas and quotes', () => {
    expect(parseCsv('a,b\n"1,5","say ""hi"""')).toEqual([['a', 'b'], ['1,5', 'say "hi"']]);
  });
  it('detects semicolons and tabs', () => {
    expect(parseCsv('a;b\n1;2')).toEqual([['a', 'b'], ['1', '2']]);
    expect(parseCsv('a\tb\n1\t2')).toEqual([['a', 'b'], ['1', '2']]);
  });
});

describe('normalizeBillingMonth', () => {
  it('accepts the usual ways of writing a month', () => {
    expect(normalizeBillingMonth('August 2026')).toBe('August 2026');
    expect(normalizeBillingMonth('aug 2026')).toBe('August 2026');
    expect(normalizeBillingMonth('2026-08')).toBe('August 2026');
    expect(normalizeBillingMonth('8/2026')).toBe('August 2026');
  });
  it('rejects text that is not a month', () => {
    expect(normalizeBillingMonth('2026-13')).toBeNull();
    expect(normalizeBillingMonth('Augustus 2026x')).toBeNull();
    expect(normalizeBillingMonth('')).toBeNull();
  });
});

describe('invoiceNoFor', () => {
  it('follows the sample data style', () => {
    expect(invoiceNoFor('August 2026', 'a101')).toBe('INV-202608-A101');
  });
});

describe('buildImportPlan', () => {
  it('asks for the columns that are missing', () => {
    const plan = buildImportPlan('roomNumber,billingMonth\nA101,August 2026', rooms, []);
    expect(plan.fileError).toBe('MISSING_COLUMNS');
    expect(plan.missingColumns).toEqual(['currWater', 'currElectric']);
  });

  it('reports an empty file', () => {
    expect(buildImportPlan('', rooms, []).fileError).toBe('EMPTY');
  });

  it('takes the previous reading from the latest earlier bill', () => {
    const plan = buildImportPlan(`${header}A101,August 2026,108,480,,`, rooms, existing);
    const row = plan.rows[0];
    expect(row.error).toBeUndefined();
    expect([row.prevWater, row.prevElectric]).toEqual([100, 400]);
  });

  it('chains months inside the file, whatever order they are written in', () => {
    const plan = buildImportPlan(
      `${header}A102,September 2026,120,560,,\nA102,August 2026,110,500,100,400`, rooms, []);
    const september = plan.rows[0];
    expect(september.error).toBeUndefined();
    expect([september.prevWater, september.prevElectric]).toEqual([110, 500]);
  });

  it('asks for a previous reading when there is none to use', () => {
    const plan = buildImportPlan(`${header}A102,August 2026,110,500,,`, rooms, []);
    expect(plan.rows[0].error).toEqual({ code: 'NO_PREVIOUS', detail: 'water' });
  });

  it('refuses a bill the room already has', () => {
    const plan = buildImportPlan(`${header}A101,July 2026,105,410,100,400`, rooms, existing);
    expect(plan.rows[0].error?.code).toBe('DUPLICATE_BILL');
  });

  it('refuses the same room and month twice in one file', () => {
    const plan = buildImportPlan(`${header}A102,August 2026,110,500,100,400\nA102,2026-08,111,501,100,400`, rooms, []);
    expect(plan.rows[0].error).toBeUndefined();
    expect(plan.rows[1].error?.code).toBe('DUPLICATE_IN_FILE');
  });

  it('flags an unknown room, a bad month, a bad number and a meter that goes backwards', () => {
    const plan = buildImportPlan(
      `${header}Z999,August 2026,1,1,0,0\nA102,someday,1,1,0,0\nA102,August 2026,abc,1,0,0\nA102,August 2026,50,500,100,400`, rooms, []);
    expect(plan.rows.map(r => r.error?.code)).toEqual(['ROOM_NOT_FOUND', 'BAD_MONTH', 'BAD_NUMBER', 'METER_BACKWARDS']);
  });

  it('ignores blank lines and keeps the line numbers of the file', () => {
    const plan = buildImportPlan(`${header}\nA102,August 2026,110,500,100,400\n`, rooms, []);
    expect(plan.rows).toHaveLength(1);
    expect(plan.rows[0].line).toBe(3);
  });
});
