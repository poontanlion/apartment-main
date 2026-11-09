import React from 'react';
import { BankInfo } from '../../types';
import { hasBankInfo } from '../../utils/bankInfo';

interface InvoicePaymentInfoProps {
  info?: BankInfo | null;
}

/** Bank details and transfer QR code for an invoice. Renders nothing when none are set. */
export const InvoicePaymentInfo: React.FC<InvoicePaymentInfoProps> = ({ info }) => {
  if (!hasBankInfo(info)) return null;

  return (
    <div className="border border-slate-300 rounded-xl p-3.5 flex items-center justify-between gap-4 break-inside-avoid">
      <div className="space-y-1 text-xs">
        <span className="block text-[10px] uppercase font-bold tracking-wider text-slate-500 print:text-black">
          Payment Information / ช่องทางชำระเงิน
        </span>
        {info.bankName && (
          <p className="text-slate-700 print:text-black">Bank: <strong className="font-extrabold">{info.bankName}</strong></p>
        )}
        {info.accountName && (
          <p className="text-slate-700 print:text-black">Account Name: <strong className="font-extrabold">{info.accountName}</strong></p>
        )}
        {info.accountNumber && (
          <p className="text-slate-700 print:text-black">Account No: <strong className="font-extrabold tracking-wide">{info.accountNumber}</strong></p>
        )}
      </div>
      {info.qrCodeImage && (
        <div className="text-center shrink-0">
          <img
            src={info.qrCodeImage}
            alt="Payment QR code"
            className="w-28 h-28 object-contain border border-slate-300 bg-white"
          />
          <span className="block text-[10px] text-slate-500 print:text-black mt-1">Scan to pay</span>
        </div>
      )}
    </div>
  );
};
