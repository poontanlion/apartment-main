import { MaintenancePredictionRow } from '../types';
import { MaintenancePredictionApiError } from '../services/api';
import { formatDateDMY } from './formatters';

export const bangkokToday = (now: Date = new Date()): string => {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now);
  const value = (type: string) => parts.find(part => part.type === type)!.value;
  return `${value('year')}-${value('month')}-${value('day')}`;
};

export const initialAppointmentDate = (
  row: Pick<MaintenancePredictionRow, 'hasAppointment' | 'appointmentDate' | 'predictedDate'>,
  today: string = bangkokToday(),
): string => {
  if (row.hasAppointment) return row.appointmentDate || '';
  const date = row.predictedDate || '';
  return date && date < today ? today : date;
};

export const validAppointmentDate = (date: string, today: string): boolean =>
  formatDateDMY(date) !== '—' && date >= today;

export const appointmentErrorKey = (error: unknown): string => {
  if (error instanceof MaintenancePredictionApiError) {
    if (error.status === 400) return 'mnt.predictions.appointmentValidation';
    if (error.status === 401 || error.status === 403) return 'mnt.predictions.accessError';
  }
  return 'mnt.predictions.appointmentError';
};
