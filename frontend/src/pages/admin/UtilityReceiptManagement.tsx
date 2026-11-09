import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { UtilityBill, Room, Lease, RentQuote, BillPayment } from '../../types';
import { getUtilityBills, saveUtilityBill, updateBillStatus, getRooms, getLeases, getRentQuote, getBillPayments, addBillPayment, getBillingSettings, saveBillingSettings } from '../../services/api';
import { formatCurrency } from '../../utils/formatters';
import { currentBillingMonth, recentBillingMonths } from '../../utils/billingMonth';
import { FileText, Plus, Printer, Droplets, Zap, Search, Building2 } from 'lucide-react';
import { ReceiptModal } from '../../components/admin/ReceiptModal';
import { BillPaymentModal } from '../../components/admin/BillPaymentModal';
import { BulkReceiptModal } from '../../components/admin/BulkReceiptModal';
import { ScannedDocumentModal } from '../../components/admin/ScannedDocumentModal';
import { useLanguage } from '../../context/LanguageContext';
import { toast } from 'sonner';
import { UtilityUsagePanel } from '../../components/admin/UtilityUsagePanel';

const MONTH_NAMES = [
  { value: 'January', th: 'มกราคม (Jan)', en: 'January' },
  { value: 'February', th: 'กุมภาพันธ์ (Feb)', en: 'February' },
  { value: 'March', th: 'มีนาคม (Mar)', en: 'March' },
  { value: 'April', th: 'เมษายน (Apr)', en: 'April' },
  { value: 'May', th: 'พฤษภาคม (May)', en: 'May' },
  { value: 'June', th: 'มิถุนายน (Jun)', en: 'June' },
  { value: 'July', th: 'กรกฎาคม (Jul)', en: 'July' },
  { value: 'August', th: 'สิงหาคม (Aug)', en: 'August' },
  { value: 'September', th: 'กันยายน (Sep)', en: 'September' },
  { value: 'October', th: 'ตุลาคม (Oct)', en: 'October' },
  { value: 'November', th: 'พฤศจิกายน (Nov)', en: 'November' },
  { value: 'December', th: 'ธันวาคม (Dec)', en: 'December' },
];

const YEAR_OPTIONS = [2024, 2025, 2026, 2027, 2028, 2029, 2030];

const leaseCoversBillingMonth = (lease: Lease, billingMonth: string) => {
  const [monthName, yearText] = billingMonth.split(' ');
  const monthIndex = MONTH_NAMES.findIndex(month => month.value === monthName);
  const year = Number(yearText);
  if (monthIndex < 0 || !Number.isFinite(year) || !['Active', 'Terminated'].includes(lease.status)) return false;

  const monthStart = new Date(year, monthIndex, 1);
  const nextMonthStart = new Date(year, monthIndex + 1, 1);
  const checkIn = new Date(`${lease.checkInDate}T00:00:00`);
  let leaseEnd = new Date(`${lease.checkOutDate}T00:00:00`);
  if (lease.status === 'Terminated') {
    if (!lease.terminatedDate) return false;
    const dayAfterTermination = new Date(`${lease.terminatedDate}T00:00:00`);
    dayAfterTermination.setDate(dayAfterTermination.getDate() + 1);
    if (dayAfterTermination < leaseEnd) leaseEnd = dayAfterTermination;
  }
  return checkIn < nextMonthStart && leaseEnd > monthStart;
};

const leaseForRoomInMonth = (leases: Lease[], roomId: string, billingMonth: string) =>
  leases
    .filter(lease => lease.roomId === roomId && leaseCoversBillingMonth(lease, billingMonth))
    .sort((a, b) => b.checkInDate.localeCompare(a.checkInDate))[0];

export const UtilityReceiptManagement: React.FC = () => {
  const { t, language } = useLanguage();
  const [searchParams] = useSearchParams();
  const initialBuildingId = searchParams.get('buildingId') || 'All';

  const [bills, setBills] = useState<UtilityBill[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [leases, setLeases] = useState<Lease[]>([]);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<'All' | 'Unpaid' | 'Partial' | 'Paid'>('All');
  const [selectedBillForPayment, setSelectedBillForPayment] = useState<UtilityBill | null>(null);
  const [paymentHistory, setPaymentHistory] = useState<BillPayment[]>([]);
  const [monthlyInterestRate, setMonthlyInterestRate] = useState(0);
  const [savedInterestRate, setSavedInterestRate] = useState(0);
  const [monthFilter, setMonthFilter] = useState<string>('All');
  const [buildingFilter, setBuildingFilter] = useState<string>(initialBuildingId);

  const [showBillModal, setShowBillModal] = useState(false);
  const [selectedBillForReceipt, setSelectedBillForReceipt] = useState<UtilityBill | null>(null);
  const [selectedBillIds, setSelectedBillIds] = useState<string[]>([]);
  const [showBulkPrintModal, setShowBulkPrintModal] = useState(false);
  const [scannedDocBill, setScannedDocBill] = useState<UtilityBill | null>(null);

  const handleToggleSelectAll = () => {
    if (selectedBillIds.length === filteredBills.length) {
      setSelectedBillIds([]);
    } else {
      setSelectedBillIds(filteredBills.map(b => b.id));
    }
  };

  const handleToggleSelectBill = (id: string) => {
    setSelectedBillIds(prev =>
      prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]
    );
  };

  const [billFormData, setBillFormData] = useState<Partial<UtilityBill>>({
    billingMonth: currentBillingMonth(),
    waterRate: 9,
    electricRate: 4,
    commonFee: 300,
    prevWaterMeter: 0,
    currWaterMeter: 0,
    prevElectricMeter: 0,
    currElectricMeter: 0,
  });
  const [rentQuote, setRentQuote] = useState<RentQuote | null>(null);
  const [rentQuoteError, setRentQuoteError] = useState('');

  const [currentMonthPart, currentYearPart] = (billFormData.billingMonth || currentBillingMonth()).split(' ');

  const handleMonthChange = (newMonth: string) => {
    setBillFormData(prev => ({
      ...prev,
      billingMonth: `${newMonth} ${currentYearPart}`,
    }));
  };

  const handleYearChange = (newYear: string) => {
    setBillFormData(prev => ({
      ...prev,
      billingMonth: `${currentMonthPart} ${newYear}`,
    }));
  };

  const fetchData = async () => {
    try {
      const [billRows, roomRows, leaseRows] = await Promise.all([getUtilityBills(), getRooms(), getLeases()]);
      setBills(billRows);
      setRooms(roomRows);
      setLeases(leaseRows);
      const settings = await getBillingSettings();
      setMonthlyInterestRate(settings.monthlyInterestRate);
      setSavedInterestRate(settings.monthlyInterestRate);
    } catch (err) {
      console.error('Failed to fetch utility bills data:', err);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  // The server decides the rent, so the form shows its answer for this room and month.
  useEffect(() => {
    if (!showBillModal || !billFormData.roomId || !billFormData.billingMonth) return;
    let stale = false;
    setRentQuote(null);
    setRentQuoteError('');
    getRentQuote(billFormData.roomId, billFormData.billingMonth)
      .then(quote => {
        if (stale) return;
        setRentQuote(quote);
        // Name the tenant of the lease being billed, which may have ended, not whoever was shown last.
        const lease = leases.find(l => l.id === quote.leaseId);
        setBillFormData(prev => ({ ...prev, tenantName: lease?.tenantName || '' }));
      })
      .catch((err: Error) => {
        if (!stale) setRentQuoteError(err.message);
      });
    return () => {
      stale = true;
    };
  }, [showBillModal, billFormData.roomId, billFormData.billingMonth]);

  const handleOpenNewBillModal = () => {
    setRentQuote(null);
    setRentQuoteError('');
    setMonthlyInterestRate(savedInterestRate);
    const billingMonth = currentBillingMonth();
    const eligibleRooms = rooms.filter(room => leaseForRoomInMonth(leases, room.id, billingMonth));
    const targetRoom = eligibleRooms[0];
    const lease = targetRoom ? leaseForRoomInMonth(leases, targetRoom.id, billingMonth) : undefined;
    const prevWaterMeter = targetRoom?.currWaterMeter || targetRoom?.prevWaterMeter || 100;
    const prevElectricMeter = targetRoom?.currElectricMeter || targetRoom?.prevElectricMeter || 400;

    setBillFormData({
      billingMonth,
      roomId: targetRoom?.id || '',
      roomNumber: targetRoom?.roomNumber || '',
      tenantName: lease?.tenantName || '',
      prevWaterMeter,
      currWaterMeter: prevWaterMeter + 8,
      prevElectricMeter,
      currElectricMeter: prevElectricMeter + 90,
      waterRate: 9,
      electricRate: 4,
      commonFee: 300,
    });
    setShowBillModal(true);
  };

  const handleRoomSelectInBill = (roomId: string) => {
    const rm = rooms.find(r => r.id === roomId);
    if (!rm) return;
    const lease = leaseForRoomInMonth(leases, roomId, billFormData.billingMonth || currentBillingMonth());
    if (!lease) return;

    const prevWaterMeter = rm.currWaterMeter || rm.prevWaterMeter || 100;
    const prevElectricMeter = rm.currElectricMeter || rm.prevElectricMeter || 400;
    setBillFormData(prev => ({
      ...prev,
      roomId: rm.id,
      roomNumber: rm.roomNumber,
      tenantName: lease.tenantName,
      prevWaterMeter,
      currWaterMeter: prevWaterMeter + 8,
      prevElectricMeter,
      currElectricMeter: prevElectricMeter + 90,
    }));
  };

  const handleSaveBill = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!rentQuote || rentQuote.source !== 'LEASE' || !billFormData.roomId) return;
    try {
      if (monthlyInterestRate !== savedInterestRate) {
        const saved = await saveBillingSettings(monthlyInterestRate);
        setSavedInterestRate(saved.monthlyInterestRate);
      }
      await saveUtilityBill(billFormData);
      toast.success('Utility bill calculated and saved successfully');
      setShowBillModal(false);
      fetchData();
    } catch (err) {
      toast.error((err as Error).message);
    }
  };

  const handleOpenPaymentModal = async (bill: UtilityBill) => {
    setSelectedBillForPayment(bill);
    setPaymentHistory([]);
    try {
      setPaymentHistory(await getBillPayments(bill.id));
    } catch {
      setPaymentHistory([]);
    }
  };

  const handleRecordPayment = async (amount: number, note?: string, proofImage?: string) => {
    if (!selectedBillForPayment) return;
    try {
      const result = await addBillPayment(selectedBillForPayment.id, { amount, note, proofImage });
      setBills(prev => prev.map(bill => bill.id === result.bill.id ? result.bill : bill));
      setSelectedBillForPayment(null);
      setPaymentHistory([]);
      toast.success(language === 'th' ? 'บันทึกรับชำระแล้ว' : 'Payment recorded');
    } catch (err) {
      toast.error((err as Error).message);
    }
  };

  const waterUnitsPreview = Math.max(0, (billFormData.currWaterMeter || 0) - (billFormData.prevWaterMeter || 0));
  const electricUnitsPreview = Math.max(0, (billFormData.currElectricMeter || 0) - (billFormData.prevElectricMeter || 0));
  const waterAmtPreview = waterUnitsPreview * (billFormData.waterRate || 9);
  const electricAmtPreview = electricUnitsPreview * (billFormData.electricRate || 4);
  const extraChargeRoom = rooms.find(r => r.id === billFormData.roomId);
  const extraChargePreview = extraChargeRoom?.extraChargeAmount || 0;
  const totalAmtPreview = (rentQuote?.rentAmount || 0) + waterAmtPreview + electricAmtPreview + (billFormData.commonFee || 0) + extraChargePreview;

  const monthIndex = (value: string) => {
    const [month, year] = value.split(' ');
    return Number(year) * 12 + MONTH_NAMES.findIndex(item => item.value === month);
  };
  const latestBillIds = new Set(Array.from(bills.reduce((latest, bill) => {
    const key = bill.roomId || bill.roomNumber;
    const current = latest.get(key);
    if (!current || monthIndex(bill.billingMonth) > monthIndex(current.billingMonth)) latest.set(key, bill);
    return latest;
  }, new Map<string, UtilityBill>()).values()).map(bill => bill.id));

  const previousBillForDraft = bills
    .filter(b => b.roomId === billFormData.roomId && monthIndex(b.billingMonth) < monthIndex(billFormData.billingMonth || ''))
    .sort((a, b) => monthIndex(b.billingMonth) - monthIndex(a.billingMonth))[0];
  const carryForwardPreview = previousBillForDraft
    ? Math.max(0, previousBillForDraft.balanceDue ?? ((previousBillForDraft.amountDue ?? previousBillForDraft.totalAmount ?? 0) - (previousBillForDraft.paidAmount || 0)))
    : 0;
  const interestPreview = Math.round(carryForwardPreview * monthlyInterestRate) / 100;
  const amountDuePreview = totalAmtPreview + carryForwardPreview + interestPreview;

  const availableMonths = Array.from(new Set([...recentBillingMonths(3), ...bills.map(b => b.billingMonth)])).filter((m): m is string => Boolean(m));

  const filteredBills = bills.filter(b => {
    const matchesSearch = (b.roomNumber || '').includes(search) || (b.tenantName || '').toLowerCase().includes(search.toLowerCase()) || (b.invoiceNo || '').includes(search);
    const matchesStatus = statusFilter === 'All' || b.status === statusFilter;
    const matchesMonth = monthFilter === 'All' || b.billingMonth === monthFilter;
    
    let matchesBuilding = true;
    if (buildingFilter !== 'All') {
      const room = rooms.find(r => r.id === b.roomId || r.roomNumber === b.roomNumber);
      if (room?.buildingId) {
        matchesBuilding = room.buildingId === buildingFilter;
      } else {
        const isBldB = (room?.roomNumber || b.roomNumber || '').toUpperCase().startsWith('B');
        if (buildingFilter === 'bld-2') matchesBuilding = isBldB;
        else if (buildingFilter === 'bld-1') matchesBuilding = !isBldB;
        else matchesBuilding = false;
      }
    }

    return matchesSearch && matchesStatus && matchesMonth && matchesBuilding;
  });

  return (
    <div className="space-y-8 pb-10">

      {/* HEADER */}
      <div className="border-b border-nike-hairline dark:border-nike-dark-card pb-4 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-[28px] font-bold text-nike-ink dark:text-white flex items-center gap-2.5">
            <FileText className="w-8 h-8 text-emerald-600 dark:text-emerald-400" />
            {t('util.title')}
          </h1>
          <p className="text-[14px] text-nike-mute dark:text-nike-stone mt-0.5">
            {t('util.sub')}
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleOpenNewBillModal}
            className="px-4 py-2.5 text-xs font-semibold rounded-xl bg-emerald-600 text-white hover:bg-emerald-700 transition-all flex items-center gap-2 shadow-sm"
          >
            <Plus className="w-4 h-4" /> {t('util.newBill').replace(/^\+\s*/, '')}
          </button>
        </div>
      </div>

      {/* METRICS SUMMARY */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card p-5 rounded-2xl">
          <span className="text-xs text-nike-mute dark:text-nike-stone font-medium">{t('util.paid')}</span>
          <span className="text-3xl font-bold text-emerald-600 dark:text-emerald-400 block mt-1">
            {bills.filter(b => b.status === 'Paid').length} {language === 'th' ? 'บิล' : 'bills'}
          </span>
        </div>
        <div className="bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card p-5 rounded-2xl">
          <span className="text-xs text-nike-mute dark:text-nike-stone font-medium">{t('util.pending')}</span>
          <span className="text-3xl font-bold text-amber-600 dark:text-amber-400 block mt-1">
            {bills.filter(b => b.status !== 'Paid').length} {language === 'th' ? 'บิล' : 'bills'}
          </span>
        </div>
        <div className="bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card p-5 rounded-2xl">
          <span className="text-xs text-nike-mute dark:text-nike-stone font-medium">{language === 'th' ? 'อัตราค่าน้ำไฟมาตรฐาน' : 'Standard Utility Rates'}</span>
          <span className="text-sm font-bold text-nike-ink dark:text-white block mt-1">
            {t('util.rates')}
          </span>
        </div>
      </div>
      <UtilityUsagePanel bills={bills} rooms={rooms} onImported={fetchData} />
      {/* BILLS & RECEIPTS TABLE */}
      <div className="bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card rounded-2xl p-6 space-y-4 shadow-xs">
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
          <h3 className="text-lg font-bold text-nike-ink dark:text-white flex items-center gap-2">
            <FileText className="w-5 h-5 text-emerald-600" />
            {language === 'th' ? 'รายการบิลและใบเสร็จ' : 'Bills & Receipts Directory'}
          </h3>

          <div className="flex flex-wrap items-center gap-3 w-full sm:w-auto">
            {/* BUILDING FILTER DROPDOWN */}
            <select
              value={buildingFilter}
              onChange={(e) => setBuildingFilter(e.target.value)}
              className="px-3 py-1.5 text-xs rounded-xl bg-nike-soft-cloud dark:bg-nike-dark-surface border border-nike-hairline dark:border-nike-dark-card text-nike-ink dark:text-white font-semibold cursor-pointer focus:outline-none"
            >
              <option value="All">{language === 'th' ? 'อาคารทั้งหมด (All Buildings)' : 'All Buildings'}</option>
              <option value="bld-1">{language === 'th' ? 'อาคาร A (Victory Tower A)' : 'Building A (Victory Tower A)'}</option>
              <option value="bld-2">{language === 'th' ? 'อาคาร B (Victory Residence B)' : 'Building B (Victory Residence B)'}</option>
            </select>

            {/* MONTH FILTER DROPDOWN */}
            <select
              value={monthFilter}
              onChange={(e) => setMonthFilter(e.target.value)}
              className="px-3 py-1.5 text-xs rounded-xl bg-nike-soft-cloud dark:bg-nike-dark-surface border border-nike-hairline dark:border-nike-dark-card text-nike-ink dark:text-white font-semibold cursor-pointer focus:outline-none"
            >
              <option value="All">{language === 'th' ? 'ทุกงวดเดือน (All Months)' : 'All Months'}</option>
              {availableMonths.map(m => (
                <option key={m} value={m}>{m}</option>
              ))}
            </select>

            <div className="flex bg-nike-soft-cloud dark:bg-nike-dark-surface p-1 rounded-xl border border-nike-hairline dark:border-nike-dark-card text-xs">
              <button
                onClick={() => setStatusFilter('All')}
                className={`px-3 py-1 rounded-lg font-medium transition-all ${statusFilter === 'All' ? 'bg-nike-canvas dark:bg-nike-dark-elevated text-nike-ink dark:text-white shadow-xs' : 'text-nike-mute'}`}
              >
                {t('common.all')}
              </button>
              <button
                onClick={() => setStatusFilter('Unpaid')}
                className={`px-3 py-1 rounded-lg font-medium transition-all ${statusFilter === 'Unpaid' ? 'bg-nike-canvas dark:bg-nike-dark-elevated text-amber-600 dark:text-amber-400 shadow-xs' : 'text-nike-mute'}`}
              >
                {t('status.unpaid')}
              </button>
              <button
                onClick={() => setStatusFilter('Partial')}
                className={`px-3 py-1 rounded-lg font-medium transition-all ${statusFilter === 'Partial' ? 'bg-nike-canvas dark:bg-nike-dark-elevated text-blue-600 dark:text-blue-400 shadow-xs' : 'text-nike-mute'}`}
              >
                {language === 'th' ? 'จ่ายบางส่วน' : 'Partial'}
              </button>
              <button
                onClick={() => setStatusFilter('Paid')}
                className={`px-3 py-1 rounded-lg font-medium transition-all ${statusFilter === 'Paid' ? 'bg-nike-canvas dark:bg-nike-dark-elevated text-emerald-600 dark:text-emerald-400 shadow-xs' : 'text-nike-mute'}`}
              >
                {t('status.paid')}
              </button>
            </div>

            <div className="relative flex-1 sm:w-48">
              <Search className="w-4 h-4 absolute left-3 top-2.5 text-nike-mute" />
              <input
                type="text"
                placeholder={language === 'th' ? 'ค้นหาเลขห้อง/บิล...' : 'Search unit or bill no...'}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full pl-9 pr-3 py-1.5 text-xs rounded-xl bg-nike-soft-cloud dark:bg-nike-dark-surface border border-nike-hairline dark:border-nike-dark-card text-nike-ink dark:text-white focus:outline-none"
              />
            </div>
          </div>
        </div>

        {/* BULK ACTION BAR */}
        <div className="flex flex-wrap items-center justify-between gap-3 bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-200 dark:border-emerald-800/50 p-3 rounded-xl">
          <div className="flex items-center gap-3">
            <span className="text-xs font-semibold text-emerald-800 dark:text-emerald-300">
              {language === 'th'
                ? `เลือกอยู่ ${selectedBillIds.length} จาก ${filteredBills.length} ห้อง`
                : `Selected ${selectedBillIds.length} of ${filteredBills.length} units`}
            </span>
            {selectedBillIds.length > 0 && (
              <button
                type="button"
                onClick={() => setSelectedBillIds([])}
                className="text-[11px] text-emerald-600 dark:text-emerald-400 hover:underline cursor-pointer"
              >
                {language === 'th' ? 'ล้างที่เลือก' : 'Clear selection'}
              </button>
            )}
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => {
                if (selectedBillIds.length === 0) {
                  setSelectedBillIds(filteredBills.map(b => b.id));
                }
                setShowBulkPrintModal(true);
              }}
              disabled={filteredBills.length === 0}
              className="px-3 py-1.5 text-xs font-semibold rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white transition-colors flex items-center gap-1.5 shadow-xs cursor-pointer disabled:opacity-50"
            >
              <Printer className="w-3.5 h-3.5" />
              {language === 'th'
                ? `พิมพ์ใบเสร็จหลายห้อง (Bulk Print ${selectedBillIds.length > 0 ? selectedBillIds.length : filteredBills.length} ใบ)`
                : `Bulk Print Receipts (${selectedBillIds.length > 0 ? selectedBillIds.length : filteredBills.length})`}
            </button>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="border-b border-nike-hairline dark:border-nike-dark-card text-nike-mute dark:text-nike-stone font-semibold">
                <th className="p-3 w-10 text-center">
                  <input
                    type="checkbox"
                    aria-label="Select all bills"
                    checked={filteredBills.length > 0 && selectedBillIds.length === filteredBills.length}
                    onChange={handleToggleSelectAll}
                    className="rounded border-slate-300 text-emerald-600 focus:ring-emerald-500 cursor-pointer"
                  />
                </th>
                <th className="p-3">Invoice No / Unit</th>
                <th className="p-3">Billing Month</th>
                <th className="p-3">Water (Units / Amt)</th>
                <th className="p-3">Electric (Units / Amt)</th>
                <th className="p-3">Grand Total</th>
                <th className="p-3">Status</th>
                <th className="p-3 text-right">Receipt & Document Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-nike-hairline/60 dark:divide-nike-dark-card/60">
              {filteredBills.map((bill) => {
                const wUnits = Math.max(0, (bill.currWaterMeter || 0) - (bill.prevWaterMeter || 0));
                const eUnits = Math.max(0, (bill.currElectricMeter || 0) - (bill.prevElectricMeter || 0));
                return (
                  <tr key={bill.id} className="hover:bg-nike-soft-cloud/50 dark:hover:bg-nike-dark-card/30">
                    <td className="p-3 w-10 text-center">
                      <input
                        type="checkbox"
                        aria-label={`Select bill ${bill.roomNumber}`}
                        checked={selectedBillIds.includes(bill.id)}
                        onChange={() => handleToggleSelectBill(bill.id)}
                        className="rounded border-slate-300 text-emerald-600 focus:ring-emerald-500 cursor-pointer"
                      />
                    </td>
                    <td className="p-3">
                      <span className="font-bold text-nike-ink dark:text-white text-sm block">{t('common.unit')} {bill.roomNumber}</span>
                      <span className="text-[11px] text-nike-stone">{bill.invoiceNo}</span>
                    </td>
                    <td className="p-3 text-nike-mute dark:text-nike-stone">
                      {bill.billingMonth}
                    </td>
                    <td className="p-3 text-nike-mute dark:text-nike-stone">
                      <span className="text-blue-600 dark:text-blue-400 font-semibold">{wUnits} units</span>
                      <span className="block text-[11px] text-nike-stone">({formatCurrency(bill.waterAmount)})</span>
                    </td>
                    <td className="p-3 text-nike-mute dark:text-nike-stone">
                      <span className="text-amber-600 dark:text-amber-400 font-semibold">{eUnits} units</span>
                      <span className="block text-[11px] text-nike-stone">({formatCurrency(bill.electricAmount)})</span>
                    </td>
                    <td className="p-3 font-bold text-nike-ink dark:text-white text-sm">
                      {formatCurrency(bill.amountDue ?? bill.totalAmount)}
                      {(bill.balanceForwardAmount || 0) > 0 && <span className="block text-[11px] font-medium text-slate-500">{language === 'th' ? `รวมยอดค้าง ${formatCurrency(bill.balanceForwardAmount)}` : `Includes ${formatCurrency(bill.balanceForwardAmount)} carried forward`}</span>}
                      {(bill.interestAmount || 0) > 0 && <span className="block text-[11px] font-medium text-slate-500">{language === 'th' ? `ดอกเบี้ย ${formatCurrency(bill.interestAmount)} (${bill.interestRate || 0}%/เดือน)` : `Interest ${formatCurrency(bill.interestAmount)} (${bill.interestRate || 0}%/mo)`}</span>}
                      {(bill.status === 'Partial' || (bill.paidAmount || 0) > 0) && <span className="block text-[11px] font-medium text-amber-700 dark:text-amber-300">{language === 'th' ? 'คงค้าง' : 'Remaining'}: {formatCurrency(bill.balanceDue ?? 0)}</span>}
                    </td>
                    <td className="p-3">
                      <div className="flex flex-col items-start gap-1.5">
                        <span className={`px-2.5 py-1 rounded-full text-[11px] font-semibold border ${
                          bill.status === 'Paid' ? 'bg-emerald-500/10 text-emerald-600 border-emerald-500/20'
                            : bill.status === 'Partial' ? 'bg-blue-500/10 text-blue-600 border-blue-500/20'
                            : 'bg-amber-500/10 text-amber-600 border-amber-500/20'
                        }`}>
                          {bill.status === 'Paid' ? t('status.paid') : bill.status === 'Partial' ? (language === 'th' ? 'จ่ายบางส่วน' : 'Partially paid') : t('status.unpaid')}
                        </span>
                        {bill.status !== 'Paid' && latestBillIds.has(bill.id) && <button type="button" onClick={() => void handleOpenPaymentModal(bill)} className="text-[11px] font-semibold text-emerald-700 dark:text-emerald-400 hover:underline">{language === 'th' ? 'รับชำระ' : 'Record payment'}</button>}
                        {bill.status !== 'Paid' && !latestBillIds.has(bill.id) && <span className="text-[10px] text-slate-500">{language === 'th' ? 'ยอดยกไปบิลล่าสุดแล้ว' : 'Rolled into latest bill'}</span>}
                      </div>
                    </td>
                    <td className="p-3 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          type="button"
                          onClick={() => setScannedDocBill(bill)}
                          className={`px-2.5 py-1.5 text-xs font-semibold rounded-lg transition-colors inline-flex items-center gap-1 shadow-xs cursor-pointer ${
                            bill.scannedDocument
                              ? 'bg-blue-600 text-white hover:bg-blue-700'
                              : 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 border border-slate-300 dark:border-slate-700'
                          }`}
                          title={bill.scannedDocument ? 'ดูเอกสารสแกนใบเสร็จ' : 'อัปโหลด/จัดเก็บเอกสารสแกนใบเสร็จ'}
                        >
                          <FileText className="w-3.5 h-3.5" />
                          {bill.scannedDocument
                            ? (language === 'th' ? 'ดูเอกสารสแกน' : 'View Scanned')
                            : (language === 'th' ? 'แนบเอกสารสแกน' : 'Attach Scanned')}
                        </button>
                        <button
                          type="button"
                          onClick={() => setSelectedBillForReceipt(bill)}
                          className="px-3 py-1.5 text-xs font-semibold rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 transition-colors inline-flex items-center gap-1.5 shadow-xs cursor-pointer"
                        >
                          <Printer className="w-3.5 h-3.5" /> Print/Download Receipt
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* NEW BILL & METER ENTRY MODAL */}
      {showBillModal && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4 overflow-y-auto">
          <form onSubmit={handleSaveBill} className="bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card rounded-2xl max-w-xl w-full p-6 space-y-4 shadow-2xl my-8">
            <div className="border-b border-nike-hairline dark:border-nike-dark-card pb-3">
              <h3 className="text-lg font-bold text-nike-ink dark:text-white flex items-center gap-2">
                <FileText className="w-5 h-5 text-emerald-600" />
                Meter Reading & Utility Bill Entry
              </h3>
            </div>

            <div className="space-y-3 text-xs">

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="sm:col-span-1">
                  <label className="block text-nike-mute dark:text-nike-stone mb-1 font-medium">
                    {language === 'th' ? 'ห้องพัก (Unit) *' : 'Unit *'}
                  </label>
                  <select
                    required
                    value={billFormData.roomId}
                    onChange={(e) => handleRoomSelectInBill(e.target.value)}
                    className="w-full p-2.5 rounded-xl bg-slate-100 dark:bg-slate-800/90 border border-slate-300 dark:border-slate-600 text-slate-900 dark:text-white focus:outline-none transition-all cursor-pointer font-medium"
                  >
                    <option value="" disabled>
                      {language === 'th' ? 'เลือกห้องที่มีผู้เช่าในงวดนี้' : 'Select a unit with a tenant for this month'}
                    </option>
                    {rooms.map(r => {
                      const lease = leaseForRoomInMonth(leases, r.id, billFormData.billingMonth || currentBillingMonth());
                      return (
                        <option key={r.id} value={r.id} disabled={!lease}>
                          {t('common.unit')} {r.roomNumber} ({lease?.tenantName || (language === 'th' ? 'ไม่มีผู้เช่าในงวดนี้' : 'No tenant this month')})
                        </option>
                      );
                    })}
                  </select>
                  <p className="mt-1 text-[10px] text-nike-mute dark:text-nike-stone">
                    {language === 'th' ? 'เลือกได้เฉพาะห้องที่มีสัญญาเช่าครอบคลุมงวดเดือนนี้' : 'Only units with a lease covering this billing month can be billed.'}
                  </p>
                </div>

                <div className="sm:col-span-1">
                  <label className="block text-nike-mute dark:text-nike-stone mb-1 font-medium">
                    {language === 'th' ? 'ประจำเดือน (Month) *' : 'Billing Month *'}
                  </label>
                  <select
                    required
                    value={currentMonthPart}
                    onChange={(e) => handleMonthChange(e.target.value)}
                    className="w-full p-2.5 rounded-xl bg-slate-100 dark:bg-slate-800/90 border border-slate-300 dark:border-slate-600 text-slate-900 dark:text-white focus:outline-none transition-all cursor-pointer font-semibold"
                  >
                    {MONTH_NAMES.map(m => (
                      <option key={m.value} value={m.value}>
                        {language === 'th' ? m.th : m.en}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="sm:col-span-1">
                  <label className="block text-nike-mute dark:text-nike-stone mb-1 font-medium">
                    {language === 'th' ? 'ประจำปี (Year) *' : 'Billing Year *'}
                  </label>
                  <select
                    required
                    value={currentYearPart}
                    onChange={(e) => handleYearChange(e.target.value)}
                    className="w-full p-2.5 rounded-xl bg-slate-100 dark:bg-slate-800/90 border border-slate-300 dark:border-slate-600 text-slate-900 dark:text-white focus:outline-none transition-all cursor-pointer font-semibold"
                  >
                    {YEAR_OPTIONS.map(yr => (
                      <option key={yr} value={yr.toString()}>
                        {language === 'th' ? `${yr + 543} (${yr})` : yr.toString()}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-nike-mute dark:text-nike-stone mb-1 font-medium">
                  {language === 'th' ? 'ชื่อผู้เช่า (Tenant Name)' : 'Tenant Name'}
                </label>
                <input
                  type="text"
                  readOnly
                  value={billFormData.tenantName || ''}
                  className="w-full p-2.5 rounded-xl bg-nike-soft-cloud/70 dark:bg-nike-dark-surface/70 border border-nike-hairline dark:border-nike-dark-card text-nike-ink dark:text-white cursor-not-allowed font-semibold"
                />
              </div>

              {/* WATER METER ENTRY */}
              <div className="p-3 bg-blue-500/10 dark:bg-blue-500/10 border border-blue-500/20 rounded-xl space-y-2">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1">
                  <span className="font-bold text-blue-600 dark:text-blue-400 flex items-center gap-1.5">
                    <Droplets className="w-4 h-4" /> {language === 'th' ? 'จดมิเตอร์น้ำประปา' : 'Water Meter Reading'}
                  </span>
                  <span className="text-[10px] text-blue-700 dark:text-blue-300 font-semibold bg-blue-500/15 px-2 py-0.5 rounded-md">
                    {language === 'th' ? 'อัตราค่าน้ำ: 9 ฿/หน่วย' : 'Rate: ฿9/unit'}
                  </span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-4 gap-2">
                  <div>
                    <span className="text-[11px] text-nike-stone block">{language === 'th' ? 'เลขครั้งก่อน' : 'Prev Reading'}</span>
                    <input
                      type="number"
                      required
                      value={billFormData.prevWaterMeter || 0}
                      onChange={(e) => setBillFormData({ ...billFormData, prevWaterMeter: Number(e.target.value) })}
                      className="w-full p-2 rounded-lg bg-white dark:bg-slate-800/90 border border-slate-300 dark:border-slate-600 text-slate-900 dark:text-white text-xs"
                    />
                  </div>
                  <div>
                    <span className="text-[11px] text-nike-stone block">{language === 'th' ? 'เลขครั้งนี้' : 'Curr Reading'}</span>
                    <input
                      type="number"
                      required
                      value={billFormData.currWaterMeter || 0}
                      onChange={(e) => setBillFormData({ ...billFormData, currWaterMeter: Number(e.target.value) })}
                      className="w-full p-2 rounded-lg bg-white dark:bg-slate-800/90 border border-slate-300 dark:border-slate-600 text-slate-900 dark:text-white font-bold text-xs"
                    />
                  </div>
                  <div>
                    <span className="text-[11px] text-nike-stone block">{language === 'th' ? 'อัตรา (฿/หน่วย)' : 'Rate (฿/unit)'}</span>
                    <input
                      type="number"
                      step="0.01"
                      min="1"
                      max="50"
                      required
                      value={billFormData.waterRate ?? 9}
                      onChange={(e) => setBillFormData({ ...billFormData, waterRate: Number(e.target.value) })}
                      className="w-full p-2 rounded-lg bg-white dark:bg-slate-800/90 border border-slate-300 dark:border-slate-600 text-slate-900 dark:text-white font-bold text-xs text-blue-600"
                    />
                  </div>
                  <div>
                    <span className="text-[11px] text-nike-stone block">{language === 'th' ? 'รวมค่าน้ำ' : 'Usage × Rate'}</span>
                    <span className="text-xs font-bold text-blue-600 block mt-2">
                      {waterUnitsPreview} {language === 'th' ? 'หน่วย' : 'units'} = {formatCurrency(waterAmtPreview)}
                    </span>
                  </div>
                </div>
              </div>

              {/* ELECTRIC METER ENTRY */}
              <div className="p-3 bg-amber-500/10 dark:bg-amber-500/10 border border-amber-500/20 rounded-xl space-y-2">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1">
                  <span className="font-bold text-amber-600 dark:text-amber-400 flex items-center gap-1.5">
                    <Zap className="w-4 h-4" /> {language === 'th' ? 'จดมิเตอร์ไฟฟ้า' : 'Electric Meter Reading'}
                  </span>
                  <span className="text-[10px] text-amber-700 dark:text-amber-300 font-semibold bg-amber-500/15 px-2 py-0.5 rounded-md">
                    {language === 'th' ? 'อัตราค่าไฟ: 4 ฿/หน่วย' : 'Rate: ฿4/unit'}
                  </span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-4 gap-2">
                  <div>
                    <span className="text-[11px] text-nike-stone block">{language === 'th' ? 'เลขครั้งก่อน' : 'Prev Reading'}</span>
                    <input
                      type="number"
                      required
                      value={billFormData.prevElectricMeter || 0}
                      onChange={(e) => setBillFormData({ ...billFormData, prevElectricMeter: Number(e.target.value) })}
                      className="w-full p-2 rounded-lg bg-white dark:bg-slate-800/90 border border-slate-300 dark:border-slate-600 text-slate-900 dark:text-white text-xs"
                    />
                  </div>
                  <div>
                    <span className="text-[11px] text-nike-stone block">{language === 'th' ? 'เลขครั้งนี้' : 'Curr Reading'}</span>
                    <input
                      type="number"
                      required
                      value={billFormData.currElectricMeter || 0}
                      onChange={(e) => setBillFormData({ ...billFormData, currElectricMeter: Number(e.target.value) })}
                      className="w-full p-2 rounded-lg bg-white dark:bg-slate-800/90 border border-slate-300 dark:border-slate-600 text-slate-900 dark:text-white font-bold text-xs"
                    />
                  </div>
                  <div>
                    <span className="text-[11px] text-nike-stone block">{language === 'th' ? 'อัตรา (฿/หน่วย)' : 'Rate (฿/unit)'}</span>
                    <input
                      type="number"
                      step="0.01"
                      min="1"
                      max="10"
                      required
                      value={billFormData.electricRate ?? 4}
                      onChange={(e) => setBillFormData({ ...billFormData, electricRate: Number(e.target.value) })}
                      className="w-full p-2 rounded-lg bg-white dark:bg-slate-800/90 border border-slate-300 dark:border-slate-600 text-slate-900 dark:text-white font-bold text-xs text-amber-600"
                    />
                  </div>
                  <div>
                    <span className="text-[11px] text-nike-stone block">{language === 'th' ? 'รวมค่าไฟ' : 'Usage × Rate'}</span>
                    <span className="text-xs font-bold text-amber-600 block mt-2">
                      {electricUnitsPreview} {language === 'th' ? 'หน่วย' : 'units'} = {formatCurrency(electricAmtPreview)}
                    </span>
                  </div>
                </div>
              </div>

              {/* TOTAL CALCULATION PREVIEW */}
              <div className="p-4 bg-nike-soft-cloud dark:bg-nike-dark-surface rounded-xl border border-nike-hairline dark:border-nike-dark-card space-y-1 text-xs">
                <div className="flex justify-between text-nike-mute">
                  <span>
                    {language === 'th' ? 'ค่าเช่าห้อง' : 'Room Rent'}
                    {rentQuote && (rentQuote.source === 'LEASE'
                      ? (language === 'th' ? ' (ตามสัญญา)' : ' (per lease)')
                      : (language === 'th' ? ' (ราคาห้อง)' : ' (room price)'))}:
                  </span>
                  <span className="font-semibold text-nike-ink dark:text-white">
                    {rentQuote ? formatCurrency(rentQuote.rentAmount) : '-'}
                  </span>
                </div>
                {rentQuoteError && (
                  <p role="alert" className="text-red-600 font-medium">
                    {language === 'th' ? `หาค่าเช่าไม่ได้: ${rentQuoteError}` : `Could not get the rent: ${rentQuoteError}`}
                  </p>
                )}
                <div className="flex justify-between text-nike-mute">
                  <span>{language === 'th' ? `ค่าน้ำประปา (${waterUnitsPreview} หน่วย @ ${billFormData.waterRate ?? 9}):` : `Water (${waterUnitsPreview} units @ ${billFormData.waterRate ?? 9}):`}</span>
                  <span className="font-semibold text-blue-600">{formatCurrency(waterAmtPreview)}</span>
                </div>
                <div className="flex justify-between text-nike-mute">
                  <span>{language === 'th' ? `ค่าไฟฟ้า (${electricUnitsPreview} หน่วย @ ${billFormData.electricRate ?? 4}):` : `Electricity (${electricUnitsPreview} units @ ${billFormData.electricRate ?? 4}):`}</span>
                  <span className="font-semibold text-amber-600">{formatCurrency(electricAmtPreview)}</span>
                </div>
                <div className="flex justify-between text-nike-mute">
                  <span>{language === 'th' ? 'ค่าส่วนกลาง:' : 'Common Fee:'}</span>
                  <span className="font-semibold text-nike-ink dark:text-white">{formatCurrency(billFormData.commonFee || 300)}</span>
                </div>
                {extraChargePreview > 0 && (
                  <div className="flex justify-between text-nike-mute">
                    <span>{extraChargeRoom?.extraChargeLabel || (language === 'th' ? 'ค่าใช้จ่ายเสริม' : 'Extra charge')}:</span>
                    <span className="font-semibold text-nike-ink dark:text-white">{formatCurrency(extraChargePreview)}</span>
                  </div>
                )}
                {carryForwardPreview > 0 ? (
                  <div className="mt-2 rounded-lg border border-amber-300/70 dark:border-amber-700/60 bg-amber-50/70 dark:bg-amber-950/20 p-3 space-y-2">
                    <div className="flex justify-between font-semibold text-amber-800 dark:text-amber-300">
                      <span>{language === 'th' ? 'ยอดค้างจากบิลก่อน' : 'Previous outstanding balance'}</span>
                      <span>{formatCurrency(carryForwardPreview)}</span>
                    </div>
                    <div className="flex items-center justify-between gap-3 text-nike-mute">
                      <label htmlFor="bill-interest-rate" className="font-medium">
                        {language === 'th' ? 'ดอกเบี้ยหนี้ค้าง (%/เดือน)' : 'Overdue interest (%/month)'}
                      </label>
                      <div className="flex items-center gap-1.5">
                        <input
                          id="bill-interest-rate"
                          type="number"
                          min="0"
                          max="100"
                          step="0.01"
                          value={monthlyInterestRate}
                          onChange={e => setMonthlyInterestRate(Number(e.target.value))}
                          className="w-24 rounded-lg border border-nike-hairline dark:border-nike-dark-card bg-white dark:bg-nike-dark-surface px-2 py-1.5 text-right font-semibold text-nike-ink dark:text-white"
                        />
                        <span>%</span>
                      </div>
                    </div>
                    <div className="flex justify-between text-nike-mute">
                      <span>{language === 'th' ? 'ดอกเบี้ยของบิลนี้' : 'Interest on this bill'}</span>
                      <span className="font-semibold">{formatCurrency(interestPreview)}</span>
                    </div>
                    <p className="text-[10px] leading-relaxed text-nike-mute dark:text-nike-stone">
                      {language === 'th'
                        ? 'อัตรานี้จะบันทึกเมื่อสร้างบิล และไม่มีผลย้อนหลังต่อบิลเดิม'
                        : 'This rate is saved when the bill is created and does not change previous bills.'}
                    </p>
                  </div>
                ) : (
                  <p className="pt-2 text-[11px] text-nike-mute dark:text-nike-stone">
                    {language === 'th' ? 'ไม่มีหนี้ค้างจากบิลก่อน จึงไม่มีดอกเบี้ยในบิลนี้' : 'No previous balance, so no interest is charged on this bill.'}
                  </p>
                )}
                <div className="flex justify-between pt-2 border-t border-nike-hairline dark:border-nike-dark-card font-bold text-sm text-nike-ink dark:text-white">
                  <span>{language === 'th' ? 'ยอดรวมสุทธิ:' : 'Current month total:'}</span>
                  <span>{formatCurrency(totalAmtPreview)}</span>
                </div>
                {carryForwardPreview > 0 && (
                  <div className="flex justify-between font-bold text-sm text-nike-ink dark:text-white">
                    <span>{language === 'th' ? 'ยอดต้องชำระรวมยอดค้างและดอกเบี้ย:' : 'Total due including balance and interest:'}</span>
                    <span className="text-emerald-600 dark:text-emerald-400">{formatCurrency(amountDuePreview)}</span>
                  </div>
                )}
              </div>

            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-nike-hairline dark:border-nike-dark-card">
              <button
                type="button"
                onClick={() => { setMonthlyInterestRate(savedInterestRate); setShowBillModal(false); }}
                className="px-4 py-2 text-xs font-medium rounded-xl bg-nike-soft-cloud dark:bg-nike-dark-card text-nike-ink dark:text-white cursor-pointer"
              >
                {language === 'th' ? 'ยกเลิก' : 'Cancel'}
              </button>
              <button
                type="submit"
                disabled={!rentQuote || !Number.isFinite(monthlyInterestRate) || monthlyInterestRate < 0 || monthlyInterestRate > 100}
                className="px-4 py-2 text-xs font-semibold rounded-xl bg-emerald-600 text-white hover:bg-emerald-700 cursor-pointer shadow-xs disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {language === 'th' ? 'บันทึกและสร้างบิล' : 'Save & Generate Bill'}
              </button>
            </div>
          </form>
        </div>
      )}

      {selectedBillForPayment && (
        <BillPaymentModal bill={selectedBillForPayment} payments={paymentHistory} onClose={() => { setSelectedBillForPayment(null); setPaymentHistory([]); }} onSubmit={handleRecordPayment} />
      )}

      {/* PRINTABLE RECEIPT MODAL */}
      {selectedBillForReceipt && (
        <ReceiptModal
          bill={selectedBillForReceipt}
          onClose={() => setSelectedBillForReceipt(null)}
        />
      )}

      {/* BULK RECEIPT PRINT MODAL */}
      {showBulkPrintModal && (
        <BulkReceiptModal
          bills={filteredBills.filter(b => selectedBillIds.length > 0 ? selectedBillIds.includes(b.id) : true)}
          onClose={() => setShowBulkPrintModal(false)}
        />
      )}

      {/* SCANNED DOCUMENT MODAL */}
      {scannedDocBill && (
        <ScannedDocumentModal
          title={`เอกสารสแกนใบเสร็จ / บิล - ห้อง ${scannedDocBill.roomNumber}`}
          subtitle={`เลขที่บิล: ${scannedDocBill.invoiceNo} | งวด: ${scannedDocBill.billingMonth} | ผู้เช่า: ${scannedDocBill.tenantName || '-'}`}
          documentUrl={scannedDocBill.scannedDocument}
          onClose={() => setScannedDocBill(null)}
          onSave={async (newUrl) => {
            const savedBill = await updateBillStatus(scannedDocBill.id, scannedDocBill.status, undefined, newUrl);
            if (!savedBill) throw new Error('Failed to save scanned bill document');
            setBills(prev => prev.map(b => b.id === scannedDocBill.id ? { ...b, scannedDocument: newUrl } : b));
            setScannedDocBill(prev => prev ? { ...prev, scannedDocument: newUrl } : null);
          }}
        />
      )}

    </div>
  );
};
