import { describe, expect, it } from 'vitest';
import { appointmentErrorKey, bangkokToday, initialAppointmentDate, validAppointmentDate } from './maintenanceAppointments';
import { MaintenancePredictionApiError } from '../services/api';

describe('Bangkok appointment date policy', () => {
  it.each([
    ['2026-11-07T16:59:59Z', '2026-11-07'], ['2026-11-07T17:00:00Z', '2026-11-08'],
    ['2026-12-31T18:00:00Z', '2027-01-01'], ['2024-02-28T18:00:00Z', '2024-02-29'],
  ])('gets Bangkok today for %s', (instant, expected) => expect(bangkokToday(new Date(instant))).toBe(expected));
  it.each([['2026-11-07', false], ['2026-11-08', true], ['2026-11-09', true], ['', false], ['2027-02-30', false]])
    ('validates %s against Bangkok today', (date, expected) => expect(validAppointmentDate(String(date), '2026-11-08')).toBe(expected));
  it('defaults to the appointment when editing, rather than the prediction', () => {
    expect(initialAppointmentDate({ hasAppointment: true, appointmentDate: '2026-12-01', predictedDate: '2026-11-20' }, '2026-11-08')).toBe('2026-12-01');
  });
  it('defaults a new appointment to the prediction', () => {
    expect(initialAppointmentDate({ hasAppointment: false, appointmentDate: null, predictedDate: '2026-11-20' }, '2026-11-08')).toBe('2026-11-20');
  });
  it('requires choosing a date for no-data rows', () => {
    expect(initialAppointmentDate({ hasAppointment: false, appointmentDate: null, predictedDate: null }, '2026-11-08')).toBe('');
  });
  it.each(['2026-11-07', '2025-01-01'])('prefills Bangkok today instead of past prediction %s', predictedDate => {
    expect(initialAppointmentDate({ hasAppointment: false, appointmentDate: null, predictedDate }, '2026-11-08')).toBe('2026-11-08');
  });
  it('keeps an existing appointment when editing even if the prediction is overdue', () => {
    expect(initialAppointmentDate({ hasAppointment: true, appointmentDate: '2026-11-20', predictedDate: '2026-11-07' }, '2026-11-08')).toBe('2026-11-20');
  });
});
describe('appointment errors', () => {
  it.each([[400, 'appointmentValidation'], [401, 'accessError'], [403, 'accessError'], [500, 'appointmentError'], [404, 'appointmentError']])
    ('maps HTTP %s', (status, key) => expect(appointmentErrorKey(new MaintenancePredictionApiError(Number(status)))).toBe(`mnt.predictions.${key}`));
  it('uses a generic message for network failures', () => expect(appointmentErrorKey(new Error('network'))).toBe('mnt.predictions.appointmentError'));
});
