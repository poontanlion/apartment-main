import React, { useEffect, useState } from 'react';
import { Landmark, Trash2, Upload, X } from 'lucide-react';
import { BankInfo } from '../../types';
import { saveBankInfo } from '../../services/api';
import { useLanguage } from '../../context/LanguageContext';
import { isValidAccountNumber, qrFileProblem } from '../../utils/bankInfo';

interface BankInfoModalProps {
  initial: BankInfo | null;
  onClose: () => void;
  onSaved: (info: BankInfo) => void;
}

/** Admin form for the bank account and QR code printed on invoices. */
export const BankInfoModal: React.FC<BankInfoModalProps> = ({ initial, onClose, onSaved }) => {
  const { language } = useLanguage();
  const th = language === 'th';
  const [bankName, setBankName] = useState(initial?.bankName || '');
  const [accountName, setAccountName] = useState(initial?.accountName || '');
  const [accountNumber, setAccountNumber] = useState(initial?.accountNumber || '');
  const [qrCodeImage, setQrCodeImage] = useState(initial?.qrCodeImage || '');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const handleQrChange = (file?: File) => {
    if (!file) return;
    const problem = qrFileProblem(file, th);
    if (problem) {
      setError(problem);
      return;
    }
    setError('');
    const reader = new FileReader();
    reader.onload = () => setQrCodeImage(String(reader.result || ''));
    reader.readAsDataURL(file);
  };

  const accountNumberValid = isValidAccountNumber(accountNumber);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!accountNumberValid) return;
    setSaving(true);
    setError('');
    try {
      const saved = await saveBankInfo({ bankName, accountName, accountNumber, qrCodeImage });
      onSaved({
        bankName: saved.bankName,
        accountName: saved.accountName,
        accountNumber: saved.accountNumber,
        qrCodeImage: saved.qrCodeImage,
      });
      onClose();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const inputClass = 'w-full p-3 bg-nike-soft-cloud dark:bg-nike-dark-card border border-nike-hairline dark:border-nike-dark-card text-nike-ink dark:text-white text-[14px] rounded-xl focus:outline-none';
  const labelClass = 'block font-medium mb-1.5 text-nike-ink dark:text-white';

  return (
    <div
      className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4"
      onClick={e => { if (e.target === e.currentTarget) onClose(); }}
    >
      <form
        onSubmit={submit}
        role="dialog"
        aria-modal="true"
        aria-labelledby="bank-info-title"
        className="w-full max-w-md max-h-[90vh] overflow-y-auto rounded-2xl bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card shadow-2xl p-5 space-y-4 text-xs"
      >
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2">
            <Landmark className="w-5 h-5 text-emerald-600" />
            <div>
              <h2 id="bank-info-title" className="text-lg font-bold text-nike-ink dark:text-white">
                {th ? 'บัญชีธนาคาร / QR Code' : 'Bank account & QR code'}
              </h2>
              <p className="text-nike-mute">
                {th ? 'แสดงในใบแจ้งหนี้ที่ยังไม่ชำระ ปล่อยว่างได้' : 'Shown on invoices that are not yet paid. All fields are optional.'}
              </p>
            </div>
          </div>
          <button type="button" onClick={onClose} aria-label={th ? 'ปิด' : 'Close'} className="p-1.5 rounded-full hover:bg-nike-soft-cloud dark:hover:bg-nike-dark-card text-nike-mute">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div>
          <label htmlFor="bank-name" className={labelClass}>{th ? 'ธนาคาร' : 'Bank'}</label>
          <input id="bank-name" type="text" maxLength={100} value={bankName} onChange={e => setBankName(e.target.value)}
            placeholder={th ? 'เช่น ธนาคารกสิกรไทย' : 'e.g. Kasikorn Bank'} className={inputClass} />
        </div>
        <div>
          <label htmlFor="bank-account-name" className={labelClass}>{th ? 'ชื่อบัญชี' : 'Account name'}</label>
          <input id="bank-account-name" type="text" maxLength={100} value={accountName} onChange={e => setAccountName(e.target.value)} className={inputClass} />
        </div>
        <div>
          <label htmlFor="bank-account-number" className={labelClass}>{th ? 'เลขที่บัญชี' : 'Account number'}</label>
          <input id="bank-account-number" type="text" inputMode="numeric" maxLength={30} value={accountNumber}
            onChange={e => setAccountNumber(e.target.value)} placeholder="123-4-56789-0" className={inputClass} />
          {!accountNumberValid && (
            <p role="alert" className="text-red-600 mt-1">
              {th ? 'ใส่ได้เฉพาะตัวเลข เว้นวรรค และขีดกลาง' : 'Digits, spaces and hyphens only.'}
            </p>
          )}
        </div>

        <div>
          <span className={labelClass}>{th ? 'QR Code สำหรับโอนเงิน' : 'Transfer QR code'}</span>
          {qrCodeImage ? (
            <div className="flex items-center gap-3">
              <img src={qrCodeImage} alt={th ? 'ตัวอย่าง QR Code' : 'QR code preview'} className="w-28 h-28 object-contain border border-nike-hairline bg-white rounded-lg" />
              <button type="button" onClick={() => setQrCodeImage('')} className="px-3 py-2 rounded-xl border border-nike-hairline dark:border-nike-dark-card text-red-600 font-semibold flex items-center gap-1.5">
                <Trash2 className="w-4 h-4" /> {th ? 'ลบ QR' : 'Remove'}
              </button>
            </div>
          ) : (
            <label htmlFor="bank-qr-file" className="flex items-center justify-center gap-2 p-4 border border-dashed border-nike-hairline dark:border-nike-dark-card rounded-xl cursor-pointer text-nike-mute hover:text-nike-ink dark:hover:text-white">
              <Upload className="w-4 h-4" /> {th ? 'อัปโหลดรูป QR (PNG / JPEG / WebP, ไม่เกิน 1 MB)' : 'Upload QR image (PNG / JPEG / WebP, up to 1 MB)'}
            </label>
          )}
          <input id="bank-qr-file" type="file" accept="image/png,image/jpeg,image/webp" className="sr-only"
            onChange={e => { handleQrChange(e.target.files?.[0]); e.target.value = ''; }} />
        </div>

        {error && <p role="alert" className="text-red-600 font-medium">{error}</p>}

        <div className="flex justify-end gap-2 pt-1">
          <button type="button" onClick={onClose} className="px-4 py-2.5 rounded-xl bg-nike-soft-cloud dark:bg-nike-dark-card text-nike-ink dark:text-white font-semibold">
            {th ? 'ยกเลิก' : 'Cancel'}
          </button>
          <button type="submit" disabled={saving || !accountNumberValid} className="px-4 py-2.5 rounded-xl bg-emerald-600 text-white font-bold hover:bg-emerald-700 disabled:opacity-50">
            {saving ? (th ? 'กำลังบันทึก...' : 'Saving...') : (th ? 'บันทึก' : 'Save')}
          </button>
        </div>
      </form>
    </div>
  );
};
