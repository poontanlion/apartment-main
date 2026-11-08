import React, { useEffect, useState } from 'react';
import { AlertTriangle, BarChart3, CheckCircle2, Clock, Download, FileText, Search } from 'lucide-react';
import { toast } from 'sonner';
import { BillStatus, Building, ReportAmounts, ReportGroupBy, SummaryReport } from '../../types';
import { getBuildings, getSummaryReport } from '../../services/api';
import { useLanguage } from '../../context/LanguageContext';
import { formatCurrency } from '../../utils/formatters';
import { formatReportMonth, reportRange, ReportRangePreset } from '../../utils/billingMonth';
import {
  downloadCsv, downloadXlsx, reportColumnHeaders, reportFileName, reportRowLabel,
} from '../../utils/reportExport';

type View = 'UNIT' | 'TENANT' | 'PERIOD';
type Period = 'MONTH' | 'YEAR';
type StatusFilter = 'All' | BillStatus;

const SEARCH_DEBOUNCE_MS = 300;

const segmentClass = (active: boolean) =>
  `px-3 py-1.5 rounded-lg font-medium whitespace-nowrap transition-all ${
    active ? 'bg-nike-canvas dark:bg-nike-dark-elevated text-nike-ink dark:text-white shadow-xs' : 'text-nike-mute dark:text-nike-stone'
  }`;
const segmentGroupClass = 'flex bg-nike-soft-cloud dark:bg-nike-dark-surface p-1 rounded-xl border border-nike-hairline dark:border-nike-dark-card text-xs w-fit max-w-full overflow-x-auto';
const fieldClass = 'px-3 py-1.5 text-xs rounded-xl bg-nike-soft-cloud dark:bg-nike-dark-surface border border-nike-hairline dark:border-nike-dark-card text-nike-ink dark:text-white focus:outline-none';
const exportButtonClass = 'px-3 py-1.5 text-xs font-semibold rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white transition-colors flex items-center gap-1.5 shadow-xs cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed';

const amountColumns = (amounts: ReportAmounts) => [
  amounts.rent, amounts.water, amounts.electric, amounts.commonFee, amounts.billed, amounts.paid, amounts.outstanding,
];

export const SummaryReports: React.FC = () => {
  const { t, language } = useLanguage();
  const [view, setView] = useState<View>('UNIT');
  const [period, setPeriod] = useState<Period>('MONTH');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [buildingId, setBuildingId] = useState('All');
  const [status, setStatus] = useState<StatusFilter>('All');
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [buildings, setBuildings] = useState<Building[]>([]);
  const [report, setReport] = useState<SummaryReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const groupBy: ReportGroupBy = view === 'PERIOD' ? period : view;
  const rangeIsBackwards = Boolean(from && to && from > to);

  useEffect(() => {
    getBuildings().then(setBuildings);
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    if (rangeIsBackwards) {
      setReport(null);
      setError(null);
      setLoading(false);
      return;
    }
    // Aborting the older request stops a slow answer from replacing a newer one.
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    getSummaryReport(
      { groupBy, from, to, buildingId, status, search: debouncedSearch },
      controller.signal,
    )
      .then(setReport)
      .catch((err: Error) => {
        if (err.name === 'AbortError') return;
        setReport(null);
        setError(err.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [groupBy, from, to, buildingId, status, debouncedSearch, rangeIsBackwards]);

  const applyPreset = (preset: ReportRangePreset) => {
    const range = reportRange(preset);
    setFrom(range.from ?? '');
    setTo(range.to ?? '');
  };

  const presetIsActive = (preset: ReportRangePreset) => {
    const range = reportRange(preset);
    return (range.from ?? '') === from && (range.to ?? '') === to;
  };

  const filterLines = (): string[] => {
    const monthName = (key: string) => formatReportMonth(key, language);
    const range = from || to
      ? `${from ? monthName(from) : t('reports.rangeStart')} - ${to ? monthName(to) : t('reports.rangeLatest')}`
      : t('common.all');
    const buildingName = buildings.find(building => building.id === buildingId)?.name ?? t('common.all');
    const statusName = status === 'Paid' ? t('status.paid') : status === 'Unpaid' ? t('status.unpaid') : t('common.all');
    return [
      `${t('reports.filter.range')}: ${range}`,
      `${t('reports.filter.building')}: ${buildingName}`,
      `${t('reports.filter.status')}: ${statusName}`,
      ...(debouncedSearch.trim() ? [`${t('reports.filter.search')}: ${debouncedSearch.trim()}`] : []),
    ];
  };

  const exportCsv = () => {
    if (!report) return;
    downloadCsv(report, language, reportFileName(report.groupBy, from || undefined, to || undefined, 'csv'));
  };

  const exportXlsx = async () => {
    if (!report) return;
    try {
      await downloadXlsx(report, filterLines(), language, reportFileName(report.groupBy, from || undefined, to || undefined, 'xlsx'));
    } catch {
      toast.error(t('reports.exportFailed'));
    }
  };

  // The rows on screen may belong to the previous filters while a request runs, so they cannot be exported yet.
  const canExport = Boolean(report && report.rows.length > 0 && !loading);

  return (
    <div className="space-y-6 pb-10">
      {/* HEADER */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-nike-hairline dark:border-nike-dark-card pb-4">
        <div>
          <h1 className="text-[28px] font-bold text-nike-ink dark:text-white flex items-center gap-2.5">
            <BarChart3 className="w-8 h-8 text-emerald-600 dark:text-emerald-400" />
            {t('reports.title')}
          </h1>
          <p className="text-[14px] text-nike-mute dark:text-nike-stone mt-0.5">{t('reports.sub')}</p>
        </div>
        <div className="flex items-center gap-2">
          <button type="button" onClick={exportXlsx} disabled={!canExport} className={exportButtonClass}>
            <Download className="w-3.5 h-3.5" /> {t('reports.exportExcel')}
          </button>
          <button type="button" onClick={exportCsv} disabled={!canExport} className={exportButtonClass}>
            <FileText className="w-3.5 h-3.5" /> {t('reports.exportCsv')}
          </button>
        </div>
      </div>

      {/* VIEW AND FILTERS */}
      <div className="bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card rounded-2xl p-4 sm:p-6 space-y-4 shadow-xs">
        <div className="flex flex-wrap items-center gap-3">
          <div className={segmentGroupClass} role="group" aria-label={t('reports.groupBy')}>
            {(['UNIT', 'TENANT', 'PERIOD'] as const).map(option => (
              <button key={option} type="button" aria-pressed={view === option} onClick={() => setView(option)} className={segmentClass(view === option)}>
                {t(`reports.view.${option.toLowerCase()}`)}
              </button>
            ))}
          </div>
          {view === 'PERIOD' && (
            <div className={segmentGroupClass} role="group" aria-label={t('reports.view.period')}>
              {(['MONTH', 'YEAR'] as const).map(option => (
                <button key={option} type="button" aria-pressed={period === option} onClick={() => setPeriod(option)} className={segmentClass(period === option)}>
                  {t(`reports.period.${option.toLowerCase()}`)}
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className={segmentGroupClass} role="group" aria-label={t('reports.filter.range')}>
            {(['all', 'thisYear', 'last12'] as const).map(preset => (
              <button key={preset} type="button" aria-pressed={presetIsActive(preset)} onClick={() => applyPreset(preset)} className={segmentClass(presetIsActive(preset))}>
                {t(`reports.preset.${preset}`)}
              </button>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <input type="month" aria-label={t('reports.from')} value={from} onChange={e => setFrom(e.target.value)} className={fieldClass} />
            <span className="text-xs text-nike-mute dark:text-nike-stone">{t('reports.to')}</span>
            <input type="month" aria-label={t('reports.toLabel')} value={to} onChange={e => setTo(e.target.value)} className={fieldClass} />
          </div>
          <select aria-label={t('reports.filter.building')} value={buildingId} onChange={e => setBuildingId(e.target.value)} className={`${fieldClass} font-semibold cursor-pointer`}>
            <option value="All">{t('reports.allBuildings')}</option>
            {buildings.map(building => (
              <option key={building.id} value={building.id}>{building.name}</option>
            ))}
          </select>
          <div className={segmentGroupClass} role="group" aria-label={t('reports.filter.status')}>
            <button type="button" aria-pressed={status === 'All'} onClick={() => setStatus('All')} className={segmentClass(status === 'All')}>{t('common.all')}</button>
            <button type="button" aria-pressed={status === 'Unpaid'} onClick={() => setStatus('Unpaid')} className={segmentClass(status === 'Unpaid')}>{t('status.unpaid')}</button>
            <button type="button" aria-pressed={status === 'Paid'} onClick={() => setStatus('Paid')} className={segmentClass(status === 'Paid')}>{t('status.paid')}</button>
          </div>
          <div className="relative flex-1 min-w-[180px] sm:max-w-xs">
            <Search className="w-4 h-4 absolute left-3 top-2 text-nike-mute" />
            <input
              type="text"
              aria-label={t('reports.filter.search')}
              placeholder={t('reports.searchPlaceholder')}
              value={search}
              onChange={e => setSearch(e.target.value)}
              className={`${fieldClass} w-full pl-9`}
            />
          </div>
        </div>
      </div>

      {(error || rangeIsBackwards) && (
        <div data-testid="report-error" role="alert" className="flex items-start gap-2 p-4 rounded-2xl border border-rose-200 dark:border-rose-800/50 bg-rose-50 dark:bg-rose-950/30 text-sm text-rose-800 dark:text-rose-200">
          <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
          <span>
            {rangeIsBackwards ? t('reports.invalidRange') : <><strong>{t('reports.errorTitle')}</strong> {error}</>}
          </span>
        </div>
      )}

      {!report && loading && (
        <p role="status" className="text-sm text-nike-mute dark:text-nike-stone">{t('reports.loading')}</p>
      )}

      {report && (
        <>
          {/* TOTALS */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card p-5 rounded-2xl">
              <span className="text-xs text-nike-mute dark:text-nike-stone font-medium flex items-center gap-1.5">
                <FileText className="w-3.5 h-3.5" /> {t('reports.total.billed')}
              </span>
              <span data-testid="report-total-billed" className="text-3xl font-bold text-nike-ink dark:text-white block mt-1 tabular-nums">{formatCurrency(report.totals.billed)}</span>
            </div>
            <div className="bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card p-5 rounded-2xl">
              <span className="text-xs text-nike-mute dark:text-nike-stone font-medium flex items-center gap-1.5">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" /> {t('status.paid')}
              </span>
              <span data-testid="report-total-paid" className="text-3xl font-bold text-nike-ink dark:text-white block mt-1 tabular-nums">{formatCurrency(report.totals.paid)}</span>
            </div>
            <div className="bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card p-5 rounded-2xl">
              <span className="text-xs text-nike-mute dark:text-nike-stone font-medium flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5 text-amber-600 dark:text-amber-400" /> {t('reports.total.outstanding')}
              </span>
              <span data-testid="report-total-outstanding" className="text-3xl font-bold text-nike-ink dark:text-white block mt-1 tabular-nums">{formatCurrency(report.totals.outstanding)}</span>
            </div>
          </div>

          {/* DATA WARNINGS */}
          {report.skippedBills > 0 && (
            <div data-testid="report-warning" role="status" className="flex items-start gap-2 p-3 rounded-xl border border-amber-200 dark:border-amber-800/50 bg-amber-50 dark:bg-amber-950/30 text-xs text-amber-900 dark:text-amber-200">
              <AlertTriangle className="w-4 h-4 shrink-0" />
              <span>{t('reports.warning.skipped').replace('{count}', String(report.skippedBills))}</span>
            </div>
          )}
          {report.duplicateRoomMonths > 0 && (
            <div data-testid="report-warning" role="status" className="flex items-start gap-2 p-3 rounded-xl border border-amber-200 dark:border-amber-800/50 bg-amber-50 dark:bg-amber-950/30 text-xs text-amber-900 dark:text-amber-200">
              <AlertTriangle className="w-4 h-4 shrink-0" />
              <span>{t('reports.warning.duplicates').replace('{count}', String(report.duplicateRoomMonths))}</span>
            </div>
          )}

          {/* TABLE */}
          <div className={`bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card rounded-2xl p-4 sm:p-6 space-y-3 shadow-xs transition-opacity ${loading ? 'opacity-60' : ''}`} aria-busy={loading}>
            {report.rows.length === 0 ? (
              <p data-testid="report-empty" className="py-10 text-center text-sm text-nike-mute dark:text-nike-stone">{t('reports.empty')}</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-xs">
                  <thead>
                    <tr className="border-b border-nike-hairline dark:border-nike-dark-card text-nike-mute dark:text-nike-stone font-semibold">
                      {reportColumnHeaders(report.groupBy, language).map((header, index) => (
                        <th key={header} scope="col" className={`p-3 whitespace-nowrap ${index === 0 ? 'text-left' : 'text-right'}`}>{header}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-nike-hairline/60 dark:divide-nike-dark-card/60">
                    {report.rows.map(row => (
                      <tr key={row.key} data-testid="report-row" className="hover:bg-nike-soft-cloud/50 dark:hover:bg-nike-dark-card/30">
                        <th scope="row" className="p-3 text-left font-bold text-nike-ink dark:text-white whitespace-nowrap">{reportRowLabel(row, report.groupBy, language)}</th>
                        <td className="p-3 text-right tabular-nums text-nike-mute dark:text-nike-stone">{row.billCount}</td>
                        {amountColumns(row).map((amount, index) => (
                          <td key={index} className="p-3 text-right tabular-nums text-nike-ink dark:text-white whitespace-nowrap">{formatCurrency(amount)}</td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr data-testid="report-totals-row" className="border-t-2 border-nike-hairline dark:border-nike-dark-card font-bold text-nike-ink dark:text-white">
                      <th scope="row" className="p-3 text-left">{t('reports.total')}</th>
                      <td className="p-3 text-right tabular-nums">{report.totals.billCount}</td>
                      {amountColumns(report.totals).map((amount, index) => (
                        <td key={index} className="p-3 text-right tabular-nums whitespace-nowrap">{formatCurrency(amount)}</td>
                      ))}
                    </tr>
                  </tfoot>
                </table>
              </div>
            )}
            <p className="text-[11px] text-nike-mute dark:text-nike-stone">{t('reports.note')}</p>
          </div>
        </>
      )}
    </div>
  );
};
