import React, { useCallback, useEffect, useState } from 'react';
import { Lease, MaintenanceTask, UtilityBill } from '../../types';
import { getLeases, saveLease, getMaintenanceTasks, saveMaintenanceTask, getUtilityBills, updateBillStatus } from '../../services/api';
import { formatCurrency, formatDate } from '../../utils/formatters';
import { ScannedDocumentModal } from '../../components/admin/ScannedDocumentModal';
import { useLanguage } from '../../context/LanguageContext';
import {
  FolderArchive, FileSignature, Wrench, Receipt,
  Eye, Upload, CheckCircle2, AlertCircle, ExternalLink, Filter
} from 'lucide-react';
import { toast } from 'sonner';
import { useFuzzySearch } from '../../hooks/useFuzzySearch';
import { SmartSearchInput } from '../../components/common/SmartSearchInput';
import { HighlightedText } from '../../components/common/HighlightedText';

const getLeaseSearchTexts = (lease: Lease): string[] => [lease.tenantName || '', lease.roomNumber || '', lease.id];
const getTaskSearchTexts = (task: MaintenanceTask): string[] => [
  task.roomNumber || '', task.taskNo || '', task.category || '', task.description || '',
];
const matchesDocumentFilter = (item: { scannedDocument?: string }, mode: 'all' | 'has_doc' | 'no_doc'): boolean =>
  mode === 'has_doc' ? !!item.scannedDocument : mode === 'no_doc' ? !item.scannedDocument : true;

export const DocumentRepository: React.FC = () => {
  const { language, t } = useLanguage();
  const [activeTab, setActiveTab] = useState<'leases' | 'maintenance' | 'bills'>('leases');
  const [search, setSearch] = useState('');
  const [filterMode, setFilterMode] = useState<'all' | 'has_doc' | 'no_doc'>('all');

  const [leases, setLeases] = useState<Lease[]>([]);
  const [tasks, setTasks] = useState<MaintenanceTask[]>([]);
  const [bills, setBills] = useState<UtilityBill[]>([]);
  const [loading, setLoading] = useState(true);

  // Active modal state
  const [selectedDoc, setSelectedDoc] = useState<{
    type: 'lease' | 'task' | 'bill';
    id: string;
    title: string;
    subtitle: string;
    url?: string;
  } | null>(null);

  const fetchData = async () => {
    try {
      setLoading(true);
      const [l, t, b] = await Promise.all([
        getLeases(),
        getMaintenanceTasks(),
        getUtilityBills()
      ]);
      setLeases(l);
      setTasks(t);
      setBills(b);
    } catch (err) {
      console.error(err);
      toast.error('ไม่สามารถโหลดข้อมูลเอกสารได้');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const getBillSearchTexts = useCallback((bill: UtilityBill): string[] => [
    bill.tenantName || '', bill.roomNumber || '', bill.invoiceNo || '', bill.billingMonth || '',
    leases.find(lease => lease.id === bill.leaseId)?.tenantName || '',
  ], [leases]);
  const leaseSearch = useFuzzySearch(leases, search, getLeaseSearchTexts);
  const taskSearch = useFuzzySearch(tasks, search, getTaskSearchTexts);
  const billSearch = useFuzzySearch(bills, search, getBillSearchTexts);
  const leaseResults = leaseSearch.results.filter(result => matchesDocumentFilter(result.item, filterMode));
  const taskResults = taskSearch.results.filter(result => matchesDocumentFilter(result.item, filterMode));
  const billResults = billSearch.results.filter(result => matchesDocumentFilter(result.item, filterMode));
  const filteredLeases = leaseResults.map(result => result.item);
  const filteredTasks = taskResults.map(result => result.item);
  const filteredBills = billResults.map(result => result.item);
  const activeSearch = activeTab === 'leases' ? leaseSearch : activeTab === 'maintenance' ? taskSearch : billSearch;
  const activeResults = activeTab === 'leases' ? leaseResults : activeTab === 'maintenance' ? taskResults : billResults;
  const { debouncedQuery } = activeSearch;
  const hasSearch = debouncedQuery.trim().length > 0;
  const hasFuzzyOnly = activeResults.length > 0 && activeResults.every(result => result.kind === 'fuzzy');

  // Counts
  const leaseAttached = leases.filter(l => !!l.scannedDocument).length;
  const taskAttached = tasks.filter(t => !!t.scannedDocument).length;
  const billAttached = bills.filter(b => !!b.scannedDocument).length;

  const handleSaveDoc = async (newUrl: string) => {
    if (!selectedDoc) throw new Error('No selected document');
    if (selectedDoc.type === 'lease') {
      const target = leases.find(l => l.id === selectedDoc.id);
      if (!target) throw new Error('Lease not found');
      const result = await saveLease({ ...target, scannedDocument: newUrl });
      if (!result.success) throw new Error(result.message || 'Failed to save scanned lease');
      setLeases(prev => prev.map(l => l.id === target.id ? { ...l, scannedDocument: newUrl } : l));
    } else if (selectedDoc.type === 'task') {
      const target = tasks.find(t => t.id === selectedDoc.id);
      if (!target) throw new Error('Maintenance task not found');
      await saveMaintenanceTask({ ...target, scannedDocument: newUrl });
      setTasks(prev => prev.map(t => t.id === target.id ? { ...t, scannedDocument: newUrl } : t));
    } else {
      const target = bills.find(b => b.id === selectedDoc.id);
      if (!target) throw new Error('Utility bill not found');
      const savedBill = await updateBillStatus(target.id, target.status, undefined, newUrl);
      if (!savedBill) throw new Error('Failed to save scanned bill document');
      setBills(prev => prev.map(b => b.id === target.id ? { ...b, scannedDocument: newUrl } : b));
    }
    setSelectedDoc(prev => prev ? { ...prev, url: newUrl } : null);
    toast.success('บันทึกเอกสารสแกนสำเร็จ');
  };

  return (
    <div className="space-y-6">

      {/* HEADER */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-xl bg-blue-600/10 text-blue-600 dark:text-blue-400 flex items-center justify-center font-bold">
              <FolderArchive className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-xl sm:text-2xl font-bold text-nike-ink dark:text-white">
                {language === 'th' ? 'คลังเอกสารสแกนส่วนกลาง' : 'Scanned Documents Center'}
              </h1>
              <p className="text-xs text-nike-mute dark:text-nike-stone">
                {language === 'th'
                  ? 'ศูนย์รวมและเรียกดูเอกสารสแกน สัญญาเช่า, รายงานซ่อมบำรุง และใบเสร็จรับเงินทั้งหมด'
                  : 'Central repository to store, search, and view scanned leases, maintenance reports, and utility receipts.'}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* STATS OVERVIEW CARDS */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div
          onClick={() => setActiveTab('leases')}
          className={`p-5 rounded-2xl border transition-all cursor-pointer ${
            activeTab === 'leases'
              ? 'bg-blue-50/80 dark:bg-blue-950/40 border-blue-300 dark:border-blue-800 shadow-sm'
              : 'bg-nike-canvas dark:bg-nike-dark-elevated border-nike-hairline dark:border-nike-dark-card hover:border-blue-200'
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-nike-mute dark:text-nike-stone">
              {language === 'th' ? 'สัญญาเช่าสแกน' : 'Scanned Leases'}
            </span>
            <FileSignature className="w-4 h-4 text-blue-600 dark:text-blue-400" />
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold text-nike-ink dark:text-white">{leaseAttached}</span>
            <span className="text-xs text-nike-mute">/ {leases.length} ฉบับ</span>
          </div>
          <div className="mt-2 w-full bg-slate-200 dark:bg-slate-800 h-1.5 rounded-full overflow-hidden">
            <div
              className="bg-blue-600 h-full rounded-full transition-all"
              style={{ width: `${leases.length > 0 ? (leaseAttached / leases.length) * 100 : 0}%` }}
            />
          </div>
        </div>

        <div
          onClick={() => setActiveTab('maintenance')}
          className={`p-5 rounded-2xl border transition-all cursor-pointer ${
            activeTab === 'maintenance'
              ? 'bg-rose-50/80 dark:bg-rose-950/40 border-rose-300 dark:border-rose-800 shadow-sm'
              : 'bg-nike-canvas dark:bg-nike-dark-elevated border-nike-hairline dark:border-nike-dark-card hover:border-rose-200'
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-nike-mute dark:text-nike-stone">
              {language === 'th' ? 'รายงานซ่อมบำรุงสแกน' : 'Scanned Maintenance Reports'}
            </span>
            <Wrench className="w-4 h-4 text-rose-600 dark:text-rose-400" />
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold text-nike-ink dark:text-white">{taskAttached}</span>
            <span className="text-xs text-nike-mute">/ {tasks.length} รายการ</span>
          </div>
          <div className="mt-2 w-full bg-slate-200 dark:bg-slate-800 h-1.5 rounded-full overflow-hidden">
            <div
              className="bg-rose-600 h-full rounded-full transition-all"
              style={{ width: `${tasks.length > 0 ? (taskAttached / tasks.length) * 100 : 0}%` }}
            />
          </div>
        </div>

        <div
          onClick={() => setActiveTab('bills')}
          className={`p-5 rounded-2xl border transition-all cursor-pointer ${
            activeTab === 'bills'
              ? 'bg-emerald-50/80 dark:bg-emerald-950/40 border-emerald-300 dark:border-emerald-800 shadow-sm'
              : 'bg-nike-canvas dark:bg-nike-dark-elevated border-nike-hairline dark:border-nike-dark-card hover:border-emerald-200'
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-nike-mute dark:text-nike-stone">
              {language === 'th' ? 'ใบเสร็จ / บิลสแกน' : 'Scanned Receipts'}
            </span>
            <Receipt className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold text-nike-ink dark:text-white">{billAttached}</span>
            <span className="text-xs text-nike-mute">/ {bills.length} ใบ</span>
          </div>
          <div className="mt-2 w-full bg-slate-200 dark:bg-slate-800 h-1.5 rounded-full overflow-hidden">
            <div
              className="bg-emerald-600 h-full rounded-full transition-all"
              style={{ width: `${bills.length > 0 ? (billAttached / bills.length) * 100 : 0}%` }}
            />
          </div>
        </div>
      </div>

      {/* REPOSITORY MAIN CONTAINER */}
      <div className="bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card rounded-2xl p-6 space-y-5 shadow-xs">

        {/* TABS & SEARCH CONTROLS */}
        <div className="flex flex-wrap justify-between items-start gap-4 border-b border-nike-hairline dark:border-nike-dark-card pb-4">
          <div className="flex items-center gap-2 overflow-x-auto w-full md:w-auto">
            <button
              onClick={() => setActiveTab('leases')}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition-all flex items-center gap-2 cursor-pointer ${
                activeTab === 'leases'
                  ? 'bg-blue-600 text-white shadow-xs'
                  : 'bg-nike-soft-cloud dark:bg-nike-dark-surface text-nike-mute hover:text-nike-ink dark:hover:text-white'
              }`}
            >
              <FileSignature className="w-3.5 h-3.5" />
              {language === 'th' ? '1. สัญญาเช่า' : '1. Leases'} ({filteredLeases.length})
            </button>
            <button
              onClick={() => setActiveTab('maintenance')}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition-all flex items-center gap-2 cursor-pointer ${
                activeTab === 'maintenance'
                  ? 'bg-rose-600 text-white shadow-xs'
                  : 'bg-nike-soft-cloud dark:bg-nike-dark-surface text-nike-mute hover:text-nike-ink dark:hover:text-white'
              }`}
            >
              <Wrench className="w-3.5 h-3.5" />
              {language === 'th' ? '2. รายงานซ่อมบำรุง' : '2. Maintenance'} ({filteredTasks.length})
            </button>
            <button
              onClick={() => setActiveTab('bills')}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition-all flex items-center gap-2 cursor-pointer ${
                activeTab === 'bills'
                  ? 'bg-emerald-600 text-white shadow-xs'
                  : 'bg-nike-soft-cloud dark:bg-nike-dark-surface text-nike-mute hover:text-nike-ink dark:hover:text-white'
              }`}
            >
              <Receipt className="w-3.5 h-3.5" />
              {language === 'th' ? '3. ใบเสร็จรับเงิน' : '3. Receipts'} ({filteredBills.length})
            </button>
          </div>

          {/* SEARCH & FILTER */}
          <div className="flex flex-wrap items-start justify-end gap-3 w-full">
            <div className="flex shrink-0 whitespace-nowrap bg-nike-soft-cloud dark:bg-nike-dark-surface p-1 rounded-xl border border-nike-hairline dark:border-nike-dark-card text-xs">
              <button
                onClick={() => setFilterMode('all')}
                className={`px-2.5 py-1 rounded-lg font-medium transition-all cursor-pointer ${filterMode === 'all' ? 'bg-white dark:bg-slate-800 text-nike-ink dark:text-white shadow-xs' : 'text-nike-mute'}`}
              >
                ทั้งหมด
              </button>
              <button
                onClick={() => setFilterMode('has_doc')}
                className={`px-2.5 py-1 rounded-lg font-medium transition-all cursor-pointer ${filterMode === 'has_doc' ? 'bg-white dark:bg-slate-800 text-emerald-600 dark:text-emerald-400 shadow-xs' : 'text-nike-mute'}`}
              >
                มีไฟล์สแกนแล้ว
              </button>
              <button
                onClick={() => setFilterMode('no_doc')}
                className={`px-2.5 py-1 rounded-lg font-medium transition-all cursor-pointer ${filterMode === 'no_doc' ? 'bg-white dark:bg-slate-800 text-amber-600 dark:text-amber-400 shadow-xs' : 'text-nike-mute'}`}
              >
                ยังไม่แนบ
              </button>
            </div>

            <SmartSearchInput value={search} onChange={setSearch}
              resultCount={activeResults.length} />
          </div>
        </div>

        {hasSearch && (activeResults.length === 0 || hasFuzzyOnly) && (
          <p role="status" className="text-xs text-nike-mute dark:text-nike-stone">
            {activeResults.length === 0
              ? t('smartSearch.noResults').replace('{query}', () => debouncedQuery)
              : t('smartSearch.fuzzyOnly')}
          </p>
        )}

        {/* TAB 1: LEASES TABLE */}
        {activeTab === 'leases' && (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-nike-hairline dark:border-nike-dark-card text-nike-mute dark:text-nike-stone font-semibold">
                  <th className="p-3">Unit / Contract Ref</th>
                  <th className="p-3">Tenant Name</th>
                  <th className="p-3">Period</th>
                  <th className="p-3">Rent / Status</th>
                  <th className="p-3">Scanned File Status</th>
                  <th className="p-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-nike-hairline/60 dark:divide-nike-dark-card/60">
                {filteredLeases.map((lease) => (
                  <tr key={lease.id} className="hover:bg-nike-soft-cloud/50 dark:hover:bg-nike-dark-card/30">
                    <td className="p-3">
                      <span className="font-bold text-nike-ink dark:text-white text-sm block">Unit {lease.roomNumber}</span>
                      <span className="text-[11px] text-nike-stone">Ref: #{lease.id}</span>
                    </td>
                    <td className="p-3 font-medium text-nike-ink dark:text-white">
                      <HighlightedText text={lease.tenantName || ''} query={leaseSearch.debouncedQuery} />
                      <span className="block text-[11px] text-nike-stone">{lease.tenantPhone}</span>
                    </td>
                    <td className="p-3 text-nike-mute dark:text-nike-stone">
                      {formatDate(lease.checkInDate)} – {formatDate(lease.checkOutDate)}
                    </td>
                    <td className="p-3">
                      <span className="font-bold text-nike-ink dark:text-white block">{formatCurrency(lease.rentAmount)}</span>
                      <span className="text-[10px] text-emerald-600 font-semibold">{lease.status}</span>
                    </td>
                    <td className="p-3">
                      {lease.scannedDocument ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-500/10 text-emerald-600 border border-emerald-500/20">
                          <CheckCircle2 className="w-3 h-3" /> มีสัญญาเช่าสแกน
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-amber-500/10 text-amber-600 border border-amber-500/20">
                          <AlertCircle className="w-3 h-3" /> ยังไม่มีไฟล์สแกน
                        </span>
                      )}
                    </td>
                    <td className="p-3 text-right">
                      <button
                        onClick={() => setSelectedDoc({
                          type: 'lease',
                          id: lease.id,
                          title: `สัญญาเช่าห้องพัก - ห้อง ${lease.roomNumber}`,
                          subtitle: `ผู้เช่า: ${lease.tenantName} | สัญญา Ref: ${lease.id}`,
                          url: lease.scannedDocument
                        })}
                        className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors inline-flex items-center gap-1.5 shadow-xs cursor-pointer ${
                          lease.scannedDocument
                            ? 'bg-blue-600 text-white hover:bg-blue-700'
                            : 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-200 border border-slate-300 dark:border-slate-700'
                        }`}
                      >
                        {lease.scannedDocument ? <Eye className="w-3.5 h-3.5" /> : <Upload className="w-3.5 h-3.5" />}
                        {lease.scannedDocument ? 'เปิดดูสัญญาเช่า' : 'แนบสัญญาเช่า'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* TAB 2: MAINTENANCE TASKS TABLE */}
        {activeTab === 'maintenance' && (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-nike-hairline dark:border-nike-dark-card text-nike-mute dark:text-nike-stone font-semibold">
                  <th className="p-3">Task No / Unit</th>
                  <th className="p-3">Category & Details</th>
                  <th className="p-3">Assigned & Date</th>
                  <th className="p-3">Expenses</th>
                  <th className="p-3">Scanned Report Status</th>
                  <th className="p-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-nike-hairline/60 dark:divide-nike-dark-card/60">
                {filteredTasks.map((task) => (
                  <tr key={task.id} className="hover:bg-nike-soft-cloud/50 dark:hover:bg-nike-dark-card/30">
                    <td className="p-3">
                      <span className="font-bold text-nike-ink dark:text-white text-sm block">Unit {task.roomNumber}</span>
                      <span className="text-[11px] text-nike-stone font-mono">{task.taskNo}</span>
                    </td>
                    <td className="p-3">
                      <span className="font-semibold text-nike-ink dark:text-white block">{task.category}</span>
                      <span className="text-[11px] text-nike-stone line-clamp-1">{task.description}</span>
                    </td>
                    <td className="p-3 text-nike-mute dark:text-nike-stone">
                      <span>{task.assignedWorker || 'ช่างประจำอาคาร'}</span>
                      <span className="block text-[11px] text-nike-stone">{task.reportedDate || formatDate(task.createdAt)}</span>
                    </td>
                    <td className="p-3 font-bold text-emerald-600 dark:text-emerald-400">
                      {formatCurrency(task.totalCost || 0)}
                    </td>
                    <td className="p-3">
                      {task.scannedDocument ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-500/10 text-emerald-600 border border-emerald-500/20">
                          <CheckCircle2 className="w-3 h-3" /> มีรายงานสแกน
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-amber-500/10 text-amber-600 border border-amber-500/20">
                          <AlertCircle className="w-3 h-3" /> ยังไม่มีไฟล์รายงาน
                        </span>
                      )}
                    </td>
                    <td className="p-3 text-right">
                      <button
                        onClick={() => setSelectedDoc({
                          type: 'task',
                          id: task.id,
                          title: `รายงานซ่อมบำรุง - ${task.taskNo}`,
                          subtitle: `ห้อง: ${task.roomNumber} | หมวดหมู่: ${task.category}`,
                          url: task.scannedDocument
                        })}
                        className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors inline-flex items-center gap-1.5 shadow-xs cursor-pointer ${
                          task.scannedDocument
                            ? 'bg-rose-600 text-white hover:bg-rose-700'
                            : 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-200 border border-slate-300 dark:border-slate-700'
                        }`}
                      >
                        {task.scannedDocument ? <Eye className="w-3.5 h-3.5" /> : <Upload className="w-3.5 h-3.5" />}
                        {task.scannedDocument ? 'เปิดดูรายงาน' : 'แนบรายงาน'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* TAB 3: UTILITY BILLS TABLE */}
        {activeTab === 'bills' && (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-nike-hairline dark:border-nike-dark-card text-nike-mute dark:text-nike-stone font-semibold">
                  <th className="p-3">Invoice / Unit</th>
                  <th className="p-3">Billing Month</th>
                  <th className="p-3">Tenant Name</th>
                  <th className="p-3">Grand Total</th>
                  <th className="p-3">Scanned Bill Status</th>
                  <th className="p-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-nike-hairline/60 dark:divide-nike-dark-card/60">
                {filteredBills.map((bill) => (
                  <tr key={bill.id} className="hover:bg-nike-soft-cloud/50 dark:hover:bg-nike-dark-card/30">
                    <td className="p-3">
                      <span className="font-bold text-nike-ink dark:text-white text-sm block">Unit {bill.roomNumber}</span>
                      <span className="text-[11px] text-nike-stone">{bill.invoiceNo}</span>
                    </td>
                    <td className="p-3 text-nike-mute dark:text-nike-stone">
                      {bill.billingMonth}
                    </td>
                    <td className="p-3 font-medium text-nike-ink dark:text-white">
                      <HighlightedText text={bill.tenantName || '-'} query={billSearch.debouncedQuery} />
                    </td>
                    <td className="p-3 font-bold text-nike-ink dark:text-white text-sm">
                      {formatCurrency(bill.totalAmount)}
                    </td>
                    <td className="p-3">
                      {bill.scannedDocument ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-500/10 text-emerald-600 border border-emerald-500/20">
                          <CheckCircle2 className="w-3 h-3" /> มีใบเสร็จสแกน
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-amber-500/10 text-amber-600 border border-amber-500/20">
                          <AlertCircle className="w-3 h-3" /> ยังไม่มีไฟล์ใบเสร็จ
                        </span>
                      )}
                    </td>
                    <td className="p-3 text-right">
                      <button
                        onClick={() => setSelectedDoc({
                          type: 'bill',
                          id: bill.id,
                          title: `ใบเสร็จ / บิลค่าน้ำค่าไฟ - ห้อง ${bill.roomNumber}`,
                          subtitle: `เลขที่: ${bill.invoiceNo} | งวด: ${bill.billingMonth} | ผู้เช่า: ${bill.tenantName || '-'}`,
                          url: bill.scannedDocument
                        })}
                        className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors inline-flex items-center gap-1.5 shadow-xs cursor-pointer ${
                          bill.scannedDocument
                            ? 'bg-emerald-600 text-white hover:bg-emerald-700'
                            : 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-200 border border-slate-300 dark:border-slate-700'
                        }`}
                      >
                        {bill.scannedDocument ? <Eye className="w-3.5 h-3.5" /> : <Upload className="w-3.5 h-3.5" />}
                        {bill.scannedDocument ? 'เปิดดูใบเสร็จ' : 'แนบใบเสร็จ'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

      </div>

      {/* POPUP PREVIEW / UPLOAD MODAL */}
      {selectedDoc && (
        <ScannedDocumentModal
          title={selectedDoc.title}
          subtitle={selectedDoc.subtitle}
          documentUrl={selectedDoc.url}
          onClose={() => setSelectedDoc(null)}
          onSave={handleSaveDoc}
        />
      )}

    </div>
  );
};

