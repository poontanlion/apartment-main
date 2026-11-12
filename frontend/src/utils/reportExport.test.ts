import { describe, expect, it } from 'vitest';
import { SummaryReport, SummaryReportRow } from '../types';
import { reportColumnHeaders, reportFileName, reportRowLabel, toCsv, toWorksheetRows } from './reportExport';

const amounts = {
  billCount: 2, rent: 10000, water: 180.5, electric: 800, commonFee: 600, billed: 1234.5, paid: 1000, outstanding: 234.5,
};

const row = (key: string, label: string): SummaryReportRow => ({ key, label, ...amounts });

const report = (rows: SummaryReportRow[], groupBy: SummaryReport['groupBy'] = 'UNIT'): SummaryReport => ({
  groupBy, rows, skippedBills: 0, duplicateRoomMonths: 0, totals: { ...amounts, billCount: 9, billed: 9999.99 },
});

const csvLines = (csv: string) => csv.replace('﻿', '').split('\r\n');

describe('toCsv', () => {
  it('starts with a byte order mark so Excel reads Thai', () => {
    expect(toCsv(report([row('a', 'สมชาย')]), 'th').startsWith('﻿')).toBe(true);
  });

  it('has a header and one line per row, without a totals line', () => {
    const lines = csvLines(toCsv(report([row('a', 'A101'), row('b', 'A102')]), 'en'));

    expect(lines).toEqual([
      'Room,Bills,Rent,Water,Electricity,Common fee,Billed,Paid,Outstanding',
      'A101,2,10000.00,180.50,800.00,600.00,1234.50,1000.00,234.50',
      'A102,2,10000.00,180.50,800.00,600.00,1234.50,1000.00,234.50',
    ]);
  });

  it('uses Thai headers', () => {
    expect(csvLines(toCsv(report([]), 'th'))[0])
      .toBe('ห้อง,จำนวนบิล,ค่าเช่า,ค่าน้ำ,ค่าไฟ,ค่าส่วนกลาง,ยอดเรียกเก็บ,ชำระแล้ว,ค้างชำระ');
  });

  it('keeps a label with delimiters in one cell', () => {
    const csv = toCsv(report([row('name:x', 'Somchai, "Jr"\nA')], 'TENANT'), 'en');

    expect(csv).toContain('\r\n"Somchai, ""Jr""\nA",2,10000.00,');
  });

  it.each(['=SUM(A1)', '+66', '-1', '@cmd', '\tx', '\rx'])('neutralizes the formula prefix in %j', (label) => {
    const lines = toCsv(report([row('name:x', label)], 'TENANT'), 'en');

    expect(lines).toMatch(new RegExp(`\\r\\n"?'`));
  });

  it('leaves an ordinary label untouched', () => {
    expect(csvLines(toCsv(report([row('a', 'A-101')]), 'en'))[1].startsWith('A-101,')).toBe(true);
  });

  it('writes a negative amount as a number', () => {
    const negative = { ...row('a', 'A101'), outstanding: -5 };

    expect(csvLines(toCsv(report([negative]), 'en'))[1].endsWith(',-5.00')).toBe(true);
  });
});

describe('toWorksheetRows', () => {
  const rows = toWorksheetRows(report([row('a', 'A101')]), ['Status: Paid', 'Building: All'], 'en');

  it('starts with the filters in use', () => {
    expect(rows[0]).toEqual(['Status: Paid']);
    expect(rows[1]).toEqual(['Building: All']);
  });

  it('ends with the totals from the report', () => {
    expect(rows[rows.length - 1]).toEqual(['Total', 9, 10000, 180.5, 800, 600, 9999.99, 1000, 234.5]);
  });

  it('keeps amounts as numbers', () => {
    const dataRow = rows.find(cells => cells[0] === 'A101');

    expect(dataRow?.slice(1).every(cell => typeof cell === 'number')).toBe(true);
    expect(dataRow?.[6]).toBe(1234.5);
  });

  it('has the column headers before the rows', () => {
    const headerIndex = rows.findIndex(cells => cells[0] === 'Room');

    expect(headerIndex).toBeGreaterThan(1);
    expect(rows[headerIndex + 1][0]).toBe('A101');
  });

  it('names the totals in Thai', () => {
    const thai = toWorksheetRows(report([]), [], 'th');

    expect(thai[thai.length - 1][0]).toBe('รวม');
  });
});

describe('reportRowLabel', () => {
  it('names an unidentified tenant with the room', () => {
    const unidentified = row('unidentified:rm-A101', 'A101');

    expect(reportRowLabel(unidentified, 'TENANT', 'th')).toBe('ไม่ระบุผู้เช่า (ห้อง A101)');
    expect(reportRowLabel(unidentified, 'TENANT', 'en')).toBe('Unidentified tenant (Room A101)');
  });

  it('keeps a tenant name', () => {
    expect(reportRowLabel(row('tenant-1', 'สมชาย'), 'TENANT', 'en')).toBe('สมชาย');
  });

  it('names a month in the chosen language', () => {
    expect(reportRowLabel(row('2026-08', '2026-08'), 'MONTH', 'th')).toBe('สิงหาคม 2026');
    expect(reportRowLabel(row('2026-08', '2026-08'), 'MONTH', 'en')).toBe('August 2026');
  });

  it('keeps a year and a room', () => {
    expect(reportRowLabel(row('2026', '2026'), 'YEAR', 'th')).toBe('2026');
    expect(reportRowLabel(row('rm-A101', 'A101'), 'UNIT', 'th')).toBe('A101');
  });
});

describe('reportColumnHeaders', () => {
  it.each([
    ['UNIT', 'Room'], ['TENANT', 'Tenant'], ['MONTH', 'Month'], ['YEAR', 'Year'],
  ] as const)('starts %s with its grouping', (groupBy, first) => {
    expect(reportColumnHeaders(groupBy, 'en')[0]).toBe(first);
  });
});

describe('reportFileName', () => {
  it('names the grouping and range', () => {
    expect(reportFileName('UNIT', '2026-01', '2026-12', 'xlsx')).toBe('summary-report_unit_2026-01_2026-12.xlsx');
  });

  it('says all when the range is open', () => {
    expect(reportFileName('TENANT', undefined, undefined, 'csv')).toBe('summary-report_tenant_all.csv');
  });

  it('marks an open end of a range', () => {
    expect(reportFileName('MONTH', '2026-01', undefined, 'csv')).toBe('summary-report_month_2026-01_latest.csv');
    expect(reportFileName('YEAR', undefined, '2026-12', 'csv')).toBe('summary-report_year_earliest_2026-12.csv');
  });
});
