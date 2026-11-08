import React, { useEffect, useId, useRef, useState } from 'react';
import { useLanguage } from '../../context/LanguageContext';
import { MaintenancePredictionRow } from '../../types';
import { cancelMaintenanceAppointment, setMaintenanceAppointment } from '../../services/api';
import { appointmentErrorKey, bangkokToday, initialAppointmentDate, validAppointmentDate } from '../../utils/maintenanceAppointments';
import { toast } from 'sonner';

interface Props {
  row: MaintenancePredictionRow;
  onClose: () => void;
  onSaved: () => void;
}

export const MaintenanceAppointmentModal: React.FC<Props> = ({ row, onClose, onSaved }) => {
  const { t, language } = useLanguage();
  const [date, setDate] = useState(() => initialAppointmentDate(row));
  const [saving, setSaving] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [errorKey, setErrorKey] = useState<string | null>(null);
  const busy = useRef(false);
  const form = useRef<HTMLFormElement>(null);
  const titleId = useId();
  const dateId = useId();
  const errorId = useId();
  const previousFocus = useRef(document.activeElement);
  const today = bangkokToday();

  useEffect(() => {
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy.current) { event.preventDefault(); onClose(); }
      if (event.key === 'Tab') {
        const controls = form.current?.querySelectorAll<HTMLElement>('input:not([disabled]), button:not([disabled])');
        if (!controls?.length) return;
        const first = controls[0]; const last = controls[controls.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
      }
    };
    document.addEventListener('keydown', keydown);
    return () => {
      document.removeEventListener('keydown', keydown);
      if (previousFocus.current instanceof HTMLElement) previousFocus.current.focus();
    };
  }, [onClose]);

  const submit = async (cancel: boolean) => {
    if (busy.current) return;
    if (!cancel && !validAppointmentDate(date, bangkokToday())) {
      setErrorKey('mnt.predictions.appointmentValidation'); return;
    }
    busy.current = true; setSaving(true); setErrorKey(null);
    try {
      if (cancel) await cancelMaintenanceAppointment(row.roomId, row.taskTypeId);
      else await setMaintenanceAppointment(row.roomId, row.taskTypeId, date);
      toast.success(t(cancel ? 'mnt.predictions.appointmentCancelled' : 'mnt.predictions.appointmentSaved'));
      onSaved();
    } catch (error) {
      setErrorKey(appointmentErrorKey(error));
    } finally {
      busy.current = false; setSaving(false);
    }
  };

  return <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/60 p-4 backdrop-blur-xs">
    <form ref={form} role="dialog" aria-modal="true" aria-labelledby={titleId}
      onSubmit={event => { event.preventDefault(); void submit(confirmCancel); }}
      className="max-h-[90dvh] w-full max-w-md space-y-4 overflow-y-auto rounded-2xl border border-nike-hairline bg-nike-canvas p-6 shadow-2xl dark:border-nike-dark-card dark:bg-nike-dark-elevated">
      <h3 id={titleId} className="text-lg font-bold text-nike-ink dark:text-white">{t(row.hasAppointment ? 'mnt.predictions.editAppointment' : 'mnt.predictions.setAppointment')}</h3>
      <div className="rounded-xl bg-nike-soft-cloud p-3 text-sm dark:bg-nike-dark-surface">
        <span className="font-bold text-nike-ink dark:text-white">{row.roomNumber}</span>
        <p className="mt-1 text-nike-mute dark:text-nike-stone">{language === 'th' ? row.taskTypeNameTh : row.taskTypeNameEn}</p>
      </div>
      <div><label htmlFor={dateId} className="mb-1 block text-sm font-medium text-nike-mute dark:text-nike-stone">{t('mnt.predictions.appointmentDate')}</label>
        <input id={dateId} autoFocus type="date" required={!confirmCancel} min={today} value={date}
          disabled={saving || confirmCancel} onChange={event => { setDate(event.target.value); setErrorKey(null); }}
          aria-invalid={Boolean(errorKey)} aria-describedby={errorKey ? errorId : undefined}
          className="w-full rounded-xl border border-nike-hairline bg-nike-soft-cloud p-2.5 text-nike-ink focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 dark:border-nike-dark-card dark:bg-nike-dark-surface dark:text-white dark:[color-scheme:dark]" /></div>
      {confirmCancel && <p className="rounded-xl bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-300">{t('mnt.predictions.confirmCancelAppointment')}</p>}
      {errorKey && <p id={errorId} role="alert" className="text-sm text-red-600 dark:text-red-300">{t(errorKey)}</p>}
      <div className="flex flex-wrap justify-end gap-2 border-t border-nike-hairline pt-4 dark:border-nike-dark-card">
        {row.hasAppointment && !confirmCancel && <button type="button" disabled={saving} onClick={() => setConfirmCancel(true)}
          className="mr-auto rounded-xl px-3 py-2 text-sm font-semibold text-red-600 disabled:opacity-40 dark:text-red-400">{t('mnt.predictions.cancelAppointment')}</button>}
        <button type="button" disabled={saving} onClick={onClose}
          className="rounded-xl bg-nike-soft-cloud px-4 py-2 text-sm font-medium text-nike-ink disabled:opacity-40 dark:bg-nike-dark-card dark:text-white">{t('mnt.predictions.closeModal')}</button>
        <button type="submit" disabled={saving} className="rounded-xl bg-rose-600 px-4 py-2 text-sm font-semibold text-white disabled:cursor-wait disabled:opacity-50">
          {t(saving ? 'mnt.predictions.savingAppointment' : confirmCancel ? 'mnt.predictions.confirmAppointmentCancellation' : 'mnt.predictions.confirmAppointment')}
        </button>
      </div>
    </form>
  </div>;
};
