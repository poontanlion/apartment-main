import { useEffect, useState } from 'react';
import { getBillingSettings } from '../services/api';
import { BankInfo } from '../types';

/** The bank details printed on invoices, or null while loading or when none are set. */
export function useBankInfo(): BankInfo | null {
  const [info, setInfo] = useState<BankInfo | null>(null);

  useEffect(() => {
    let active = true;
    getBillingSettings()
      .then(settings => {
        if (!active) return;
        const { bankName, accountName, accountNumber, qrCodeImage } = settings;
        setInfo({ bankName, accountName, accountNumber, qrCodeImage });
      })
      .catch(() => {
        // Invoices still print without payment details.
      });
    return () => { active = false; };
  }, []);

  return info;
}
