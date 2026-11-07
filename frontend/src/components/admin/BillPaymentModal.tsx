import React, { useState } from 'react';
import { UtilityBill } from '../../types';
import { formatCurrency } from '../../utils/formatters';
import { useLanguage } from '../../context/LanguageContext';
import { CheckCircle2, X } from 'lucide-react';

interface Props {
  bill: UtilityBill;
  onClose: () => void;
  onSubmit: (amount: number) => void;
}

export const BillPaymentModal: React.FC<Props> = ({ bill, onClose, onSubmit }) => {
  const { language } = useLanguage();
  const th = language === 'th';
  const remaining = Math.max(0, bill.balanceDue ?? bill.amountDue ?? bill.totalAmount);
  const [amount, setAmount] = useState(remaining.toFixed(2));
  const value = Number(amount);
  const valid = Number.isFinite(value) && value > 0 && value <= remaining;

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (valid) onSubmit(Math.round(value * 100) / 100);
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4" onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <section role="dialog" aria-modal="true" aria-labelledby="bill-payment-title" className="w-full max-w-md rounded-2xl bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card shadow-2xl p-5 space-y-5">
        <div className="flex items-start justify-between">
          <div>
            <h2 id="bill-payment-title" className="text-lg font-bold text-nike-ink dark:text-white">{th ? 'บันทึกรับชำระ' : 'Record payment'}</h2>
            <p className="mt-1 text-sm text-nike-mute dark:text-nike-stone">{th ? `ห้อง ${bill.roomNumber} · ${bill.billingMonth}` : `Unit ${bill.roomNumber} · ${bill.billingMonth}`}</p>
          </div>
          <button type="button" onClick={onClose} aria-label={th ? 'ปิด' : 'Close'} className="p-2 rounded-lg hover:bg-nike-soft-cloud dark:hover:bg-nike-dark-card"><X className="w-5 h-5" /></button>
        </div>

        <div className="rounded-xl bg-nike-soft-cloud dark:bg-nike-dark-card p-4 flex items-center justify-between">
          <span className="text-sm text-nike-mute dark:text-nike-stone">{th ? 'ยอดที่ยังต้องชำระ' : 'Remaining balance'}</span>
          <strong className="text-lg text-amber-700 dark:text-amber-300">{formatCurrency(remaining)}</strong>
        </div>

        <form onSubmit={submit} className="space-y-4">
          <div>
            <label htmlFor="partial-payment-amount" className="block text-sm font-semibold text-nike-ink dark:text-white mb-1.5">{th ? 'จำนวนเงินที่รับ (บาท)' : 'Amount received (THB)'}</label>
            <input id="partial-payment-amount" type="number" inputMode="decimal" min="0.01" max={remaining} step="0.01" value={amount} onChange={e => setAmount(e.target.value)} autoFocus className="w-full rounded-xl border border-nike-hairline dark:border-nike-dark-card bg-white dark:bg-nike-dark-surface px-3 py-2.5 text-base text-nike-ink dark:text-white" />
            <p className="mt-1.5 text-xs text-nike-mute dark:text-nike-stone">{th ? 'หากรับไม่ครบ ส่วนที่เหลือจะถูกยกไปบิลถัดไป' : 'Any unpaid amount will carry forward to the next bill.'}</p>
          </div>
          <div className="flex justify-end gap-2 border-t border-nike-hairline dark:border-nike-dark-card pt-4">
            <button type="button" onClick={onClose} className="px-4 py-2.5 rounded-xl bg-nike-soft-cloud dark:bg-nike-dark-card text-sm font-semibold text-nike-ink dark:text-white">{th ? 'ยกเลิก' : 'Cancel'}</button>
            <button type="submit" disabled={!valid} className="px-4 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white text-sm font-semibold inline-flex items-center gap-2"><CheckCircle2 className="w-4 h-4" />{th ? 'บันทึกการชำระ' : 'Save payment'}</button>
          </div>
        </form>
      </section>
    </div>
  );
};
