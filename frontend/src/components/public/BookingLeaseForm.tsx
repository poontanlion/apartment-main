import React, { useState } from 'react';
import { FileSignature, RefreshCw } from 'lucide-react';
import { toast } from 'sonner';
import { Booking, BookingLeaseApplication } from '../../types';
import { createBookingLease } from '../../services/api';
import { useLanguage } from '../../context/LanguageContext';
import { formatCurrency, formatDate } from '../../utils/formatters';
import { BookingLeaseError, bookingLeaseErrorMessage } from '../../utils/bookingLeaseErrors';
import { LeaseField, LeaseFieldErrors, leaseFieldErrorMessage, validateBookingLease } from '../../utils/bookingLeaseValidation';

interface Props {
  booking: Booking;
  rentAmount: number;
  onSaved: (booking: Booking) => void;
  onCancel: () => void;
}

export const BookingLeaseForm: React.FC<Props> = ({ booking, rentAmount, onSaved, onCancel }) => {
  const { language } = useLanguage();
  const isThai = language === 'th';
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<LeaseFieldErrors>({});
  const [form, setForm] = useState<BookingLeaseApplication>({
    fullname: booking.guestName || '',
    phone: booking.guestPhone || '',
    email: booking.guestEmail || '',
    idCardPassport: '',
    emergencyContact: '',
  });

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting) return;
    const validationErrors = validateBookingLease(form);
    setFieldErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) {
      setError(isThai ? 'กรุณาตรวจสอบและแก้ไขข้อมูลในช่องที่ระบุ' : 'Please correct the highlighted fields.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      const saved = await createBookingLease(booking.id, form);
      onSaved(saved);
      toast.success(isThai ? 'บันทึกสัญญาเช่าเรียบร้อยแล้ว' : 'Lease agreement created successfully');
    } catch (err) {
      setError(bookingLeaseErrorMessage(err, isThai));
      if (err instanceof BookingLeaseError) setFieldErrors(err.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  };

  const inputClass = 'w-full p-3 rounded-xl bg-white dark:bg-nike-dark-card border border-nike-hairline dark:border-neutral-700 text-nike-ink dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-60';

  const updateField = (field: LeaseField, value: string) => {
    setForm(prev => ({ ...prev, [field]: value }));
    setFieldErrors(prev => ({ ...prev, [field]: undefined }));
    setError('');
  };
  const errorId = (field: LeaseField) => `lease-${booking.id}-${field}-error`;
  const fieldProps = (field: LeaseField) => ({
    'aria-invalid': Boolean(fieldErrors[field]),
    'aria-describedby': fieldErrors[field] ? errorId(field) : undefined,
  });
  const fieldError = (field: LeaseField) => fieldErrors[field] && (
    <span id={errorId(field)} className="block text-xs text-rose-600 dark:text-rose-400">
      {leaseFieldErrorMessage(fieldErrors[field]!, isThai)}
    </span>
  );

  return (
    <form noValidate onSubmit={handleSubmit} className="space-y-5 border-t border-nike-hairline dark:border-neutral-800 pt-6" aria-label={isThai ? 'ฟอร์มสัญญาเช่า' : 'Lease agreement form'}>
      <div>
        <h4 className="font-bold text-lg text-nike-ink dark:text-white flex items-center gap-2">
          <FileSignature className="w-5 h-5 text-blue-600" /> {isThai ? 'ทำสัญญาเช่าใหม่' : 'Create New Lease Agreement'}
        </h4>
        <p className="text-sm text-nike-mute dark:text-nike-stone mt-1">
          {isThai ? 'กรอกข้อมูลผู้เช่าเพื่อทำสัญญาสำหรับห้องที่ได้รับการอนุมัติ' : 'Complete your tenant details for your approved room.'}
        </p>
        <p className="text-xs text-nike-mute dark:text-nike-stone mt-2">
          {isThai ? 'ช่องที่มี * จำเป็นต้องกรอก ส่วนเลขบัตรประชาชน / หนังสือเดินทางและผู้ติดต่อฉุกเฉินเป็นข้อมูลเพิ่มเติม' : 'Fields marked * are required. ID / passport and emergency contact details are optional.'}
        </p>
      </div>
      <div className="rounded-2xl bg-blue-50 dark:bg-blue-950/30 p-4 grid sm:grid-cols-2 gap-3 text-sm text-nike-ink dark:text-white">
        <p>{isThai ? 'ห้อง' : 'Unit'}: <strong>{booking.roomNumber}</strong></p>
        <p>{isThai ? 'ระยะเวลาเช่า' : 'Lease period'}: {formatDate(booking.checkIn)} – {formatDate(booking.checkOut)}</p>
        <p>{isThai ? 'ค่าเช่ารายเดือน' : 'Monthly rent'}: <strong>{formatCurrency(rentAmount)}</strong></p>
        <p>{isThai ? 'เงินประกัน (2 เดือน)' : 'Security deposit (2 months)'}: <strong>{formatCurrency(rentAmount * 2)}</strong></p>
      </div>
      <fieldset disabled={submitting} className="grid sm:grid-cols-2 gap-4 text-sm">
        <label className="space-y-1.5 text-nike-ink dark:text-white">
          <span className="block font-medium">{isThai ? 'ชื่อ-นามสกุล' : 'Full Name'} *</span>
          <input required name="fullname" autoComplete="name" maxLength={255} value={form.fullname} onChange={e => updateField('fullname', e.target.value)} {...fieldProps('fullname')} className={inputClass} />
          {fieldError('fullname')}
        </label>
        <label className="space-y-1.5 text-nike-ink dark:text-white">
          <span className="block font-medium">{isThai ? 'เบอร์โทรศัพท์' : 'Phone Number'} *</span>
          <input required type="tel" name="phone" autoComplete="tel" maxLength={255} value={form.phone} onChange={e => updateField('phone', e.target.value)} {...fieldProps('phone')} className={inputClass} />
          {fieldError('phone')}
        </label>
        <label className="space-y-1.5 text-nike-ink dark:text-white">
          <span className="block font-medium">{isThai ? 'อีเมล' : 'Email Address'}</span>
          <input type="email" name="email" autoComplete="email" readOnly value={form.email} {...fieldProps('email')} className={`${inputClass} opacity-75`} />
          {fieldError('email')}
          <span className="block text-xs text-nike-mute dark:text-nike-stone">{isThai ? 'ใช้อีเมลเดียวกับการจองของคุณ' : 'Uses the email address associated with your booking.'}</span>
        </label>
        <label className="space-y-1.5 text-nike-ink dark:text-white">
          <span className="block font-medium">{isThai ? 'เลขบัตรประชาชน / หนังสือเดินทาง' : 'ID Card / Passport No.'}</span>
          <input name="idCardPassport" maxLength={255} value={form.idCardPassport} onChange={e => updateField('idCardPassport', e.target.value)} {...fieldProps('idCardPassport')} className={inputClass} />
          {fieldError('idCardPassport')}
        </label>
        <label className="sm:col-span-2 space-y-1.5 text-nike-ink dark:text-white">
          <span className="block font-medium">{isThai ? 'ผู้ติดต่อกรณีฉุกเฉิน' : 'Emergency Contact'}</span>
          <input name="emergencyContact" maxLength={255} placeholder={isThai ? 'ชื่อผู้ติดต่อและเบอร์โทรศัพท์' : 'Contact person and phone number'} value={form.emergencyContact} onChange={e => updateField('emergencyContact', e.target.value)} {...fieldProps('emergencyContact')} className={inputClass} />
          {fieldError('emergencyContact')}
        </label>
      </fieldset>
      {error && <p role="alert" className="text-sm text-rose-600 dark:text-rose-400">{error}</p>}
      <div className="flex flex-wrap justify-end gap-3">
        <button type="button" onClick={onCancel} disabled={submitting} className="rounded-full px-5 py-2.5 text-sm font-semibold text-nike-ink dark:text-white bg-nike-soft-cloud dark:bg-nike-dark-card disabled:opacity-50">{isThai ? 'ยกเลิก' : 'Cancel'}</button>
        <button type="submit" disabled={submitting} className="rounded-full px-5 py-2.5 text-sm font-semibold bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-2 disabled:opacity-50">
          {submitting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <FileSignature className="w-4 h-4" />}
          {submitting ? (isThai ? 'กำลังบันทึก...' : 'Saving...') : (isThai ? 'บันทึกสัญญาเช่า' : 'Save Lease Agreement')}
        </button>
      </div>
    </form>
  );
};
