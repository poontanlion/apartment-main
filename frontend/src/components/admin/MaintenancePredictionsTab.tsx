import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { MaintenanceAppointmentModal } from './MaintenanceAppointmentModal';
import { useSearchParams } from 'react-router-dom';
import { AlertCircle, Calendar, ChevronLeft, ChevronRight, RefreshCw, Search } from 'lucide-react';
import { toast } from 'sonner';
import { useLanguage } from '../../context/LanguageContext';
import { MaintenancePredictionRow } from '../../types';
import { formatDateDMY } from '../../utils/formatters';
import { cardToStatusQuery, predictionDaysText, predictionStatuses, predictionStatusPresentation,
  predictionText, PredictionSummaryCard, statusQueryToCard } from '../../utils/maintenancePredictions';
import { useMaintenancePredictions } from './useMaintenancePredictions';

const PAGE_SIZE = 20;
const selectClass = 'w-full rounded-xl border border-nike-hairline bg-nike-canvas px-3 py-2.5 text-sm text-nike-ink focus:outline-none focus:ring-2 focus:ring-blue-500 dark:border-nike-dark-card dark:bg-nike-dark-surface dark:text-white';
const buttonClass = 'inline-flex items-center justify-center gap-2 rounded-xl border border-nike-hairline px-3 py-2 text-sm font-semibold text-nike-ink hover:bg-nike-soft-cloud disabled:cursor-not-allowed disabled:opacity-40 dark:border-nike-dark-card dark:text-white dark:hover:bg-nike-dark-surface';
const summaryCards: { id: PredictionSummaryCard; color: string }[] = [
  { id: 'overdue', color: 'text-red-600 dark:text-red-400' },
  { id: 'dueSoon', color: 'text-orange-600 dark:text-orange-400' },
  { id: 'within30', color: 'text-blue-600 dark:text-blue-400' },
  { id: 'total', color: 'text-nike-ink dark:text-white' },
];

export const MaintenancePredictionsTab: React.FC<{ buildingId?: string }> = ({ buildingId }) => {
  const { language, t } = useLanguage();
  const [params, setParams] = useSearchParams();
  const roomId = params.get('roomId') || '';
  const taskTypeId = params.get('taskTypeId') || '';
  const status = params.get('status') || '';
  const query = params.get('q') || '';
  const [search, setSearch] = useState(query);
  const [page, setPage] = useState(1);
  const [searchPending, setSearchPending] = useState(false);
  const publishedSearch = useRef(query);
  const [appointmentRow, setAppointmentRow] = useState<MaintenancePredictionRow | null>(null);
  const closeAppointment = useCallback(() => setAppointmentRow(null), []);

  const { data, options, loading, refreshing, error, refresh, retry } = useMaintenancePredictions(
    { buildingId, roomId, taskTypeId, status, q: query },
    result => {
      const message = predictionText(t(`mnt.predictions.${result.failure > 0 ? 'refreshPartial' : 'refreshSuccess'}`), result);
      if (result.failure > 0) toast.warning(message);
      else toast.success(message);
    },
  );

  const changeFilter = (key: string, value: string) => {
    setPage(1);
    setParams(previous => {
      const next = new URLSearchParams(previous);
      if (value) next.set(key, value); else next.delete(key);
      return next;
    });
  };

  // Only user typing schedules a debounce; URL/back/forward changes sync immediately.
  useEffect(() => {
    // Our own delayed URL update must not erase newer keystrokes.
    if (query !== publishedSearch.current) { setSearch(query); setSearchPending(false); }
    publishedSearch.current = query;
  }, [query]);
  useEffect(() => {
    if (!searchPending) return;
    const timer = window.setTimeout(() => {
      publishedSearch.current = search.trim();
      changeFilter('q', search.trim());
      setSearchPending(false);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [search, searchPending]);
  useEffect(() => { setPage(1); }, [roomId, taskTypeId, status, query, buildingId]);

  const roomOptions = useMemo(() => [...new Map(options.map(row => [row.roomId, row])).values()]
    .sort((a, b) => a.roomNumber.localeCompare(b.roomNumber)), [options]);
  const typeOptions = useMemo(() => [...new Map(options.map(row => [row.taskTypeId, row])).values()], [options]);
  const taskName = (row: MaintenancePredictionRow) => language === 'th' ? row.taskTypeNameTh : row.taskTypeNameEn;
  const rows = data?.predictions || [];
  const totalPages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const start = rows.length ? (currentPage - 1) * PAGE_SIZE : 0;
  const visibleRows = rows.slice(start, start + PAGE_SIZE);
  const selectedCard = statusQueryToCard(status);
  const busy = loading || refreshing;
  const clearFilters = () => {
    publishedSearch.current = '';
    setSearch(''); setSearchPending(false); setPage(1);
    setParams(previous => {
      const next = new URLSearchParams(previous);
      ['roomId', 'taskTypeId', 'status', 'q', 'buildingId'].forEach(key => next.delete(key));
      return next;
    });
  };
  const dateContent = (row: MaintenancePredictionRow) => <>
    <div className="text-lg font-bold tabular-nums text-nike-ink dark:text-white">{formatDateDMY(row.predictedDate)}</div>
    <div className="mt-1 text-xs text-nike-mute dark:text-nike-stone">{predictionDaysText(row.daysRemaining, t)}</div>
  </>;
  const badge = (row: MaintenancePredictionRow) => {
    const presentation = predictionStatusPresentation(row.status);
    return <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${presentation.color}`}>{t(presentation.key)}</span>;
  };
  const appointmentButton = (row: MaintenancePredictionRow) => <div className="space-y-1">
    <button type="button" onClick={() => setAppointmentRow(row)} className={buttonClass}>
      <Calendar className="h-4 w-4" aria-hidden="true" />{t(row.hasAppointment ? 'mnt.predictions.appointmentSet' : 'mnt.predictions.setAppointment')}
    </button>
    {row.hasAppointment && <p className="text-xs tabular-nums text-nike-mute dark:text-nike-stone">{formatDateDMY(row.appointmentDate)}</p>}
  </div>;

  return <section className="space-y-6 rounded-2xl border border-nike-hairline bg-nike-canvas p-4 shadow-xs sm:p-6 dark:border-nike-dark-card dark:bg-nike-dark-elevated" aria-label={t('mnt.predictions.title')}>
    <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
      <div className="flex items-start gap-3">
        <div className="rounded-xl bg-blue-50 p-3 text-blue-600 dark:bg-blue-950/50 dark:text-blue-400"><Calendar className="h-6 w-6" aria-hidden="true" /></div>
        <div><h2 className="text-lg font-bold text-nike-ink dark:text-white">{t('mnt.predictions.title')}</h2>
          <p className="mt-1 text-sm text-nike-mute dark:text-nike-stone">{t('mnt.predictions.description')}</p></div>
      </div>
      <button type="button" onClick={() => void refresh()} disabled={busy} className={buttonClass}>
        <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} aria-hidden="true" />
        {t(`mnt.predictions.${refreshing ? 'refreshing' : 'refresh'}`)}
      </button>
    </div>

    <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
      {summaryCards.map(card => <button type="button" key={card.id} aria-pressed={selectedCard === card.id}
        onClick={() => changeFilter('status', selectedCard === card.id ? '' : cardToStatusQuery(card.id))}
        className={`rounded-2xl border bg-nike-canvas p-4 text-left transition-colors dark:bg-nike-dark-surface ${selectedCard === card.id ? 'border-blue-500 ring-2 ring-blue-500/20' : 'border-nike-hairline hover:border-blue-300 dark:border-nike-dark-card'}`}>
        <span className="text-sm font-semibold text-nike-ink dark:text-white">{t(`mnt.predictions.${card.id}`)}</span>
        <span className={`my-2 block text-3xl font-bold tabular-nums ${card.color}`}>{data && !error ? data.summary[card.id] : '—'}</span>
        <span className="block text-xs text-nike-mute dark:text-nike-stone">{t(`mnt.predictions.${card.id}Hint`)}</span>
      </button>)}
    </div>

    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <label className="space-y-1.5 text-xs font-semibold text-nike-mute dark:text-nike-stone">
        <span>{t('mnt.predictions.search')}</span>
        <div className="relative"><Search className="pointer-events-none absolute left-3 top-3 h-4 w-4" aria-hidden="true" />
          <input type="search" value={search} onChange={event => { setSearch(event.target.value); setSearchPending(true); setPage(1); }}
            placeholder={t('mnt.predictions.searchPlaceholder')} className={`${selectClass} pl-9`} /></div>
      </label>
      <label className="space-y-1.5 text-xs font-semibold text-nike-mute dark:text-nike-stone">
        <span>{t('mnt.predictions.room')}</span><select aria-label={t('mnt.predictions.room')} value={roomId} onChange={event => changeFilter('roomId', event.target.value)} className={selectClass}>
          <option value="">{t('mnt.predictions.allRooms')}</option>
          {roomId && !roomOptions.some(row => row.roomId === roomId) && <option value={roomId}>{t('mnt.predictions.selectedRoom').replace('{id}', roomId)}</option>}
          {roomOptions.map(row => <option key={row.roomId} value={row.roomId}>{row.roomNumber}</option>)}
        </select>
      </label>
      <label className="space-y-1.5 text-xs font-semibold text-nike-mute dark:text-nike-stone">
        <span>{t('mnt.predictions.taskType')}</span><select aria-label={t('mnt.predictions.taskType')} value={taskTypeId} onChange={event => changeFilter('taskTypeId', event.target.value)} className={selectClass}>
          <option value="">{t('mnt.predictions.allTaskTypes')}</option>
          {taskTypeId && !typeOptions.some(row => row.taskTypeId === taskTypeId) && <option value={taskTypeId}>{t('mnt.predictions.selectedTask').replace('{id}', taskTypeId)}</option>}
          {typeOptions.map(row => <option key={row.taskTypeId} value={row.taskTypeId}>{taskName(row)}</option>)}
        </select>
      </label>
      <label className="space-y-1.5 text-xs font-semibold text-nike-mute dark:text-nike-stone">
        <span>{t('mnt.predictions.status')}</span><select aria-label={t('mnt.predictions.status')} value={status} onChange={event => changeFilter('status', event.target.value)} className={selectClass}>
          <option value="">{t('mnt.predictions.allStatuses')}</option>
          {status && !predictionStatuses.some(value => value === status) && <option value={status}>
            {t(`mnt.predictions.${selectedCard === 'within30' ? 'combinedStatus' : 'customStatus'}`)}</option>}
          {predictionStatuses.map(value => <option key={value} value={value}>{t(predictionStatusPresentation(value).key)}</option>)}
        </select>
      </label>
    </div>

    <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-nike-mute dark:text-nike-stone">
      <span aria-live="polite">{predictionText(t('mnt.predictions.resultCount'), { count: !busy && !error ? rows.length : 0 })}</span>
      <button type="button" onClick={clearFilters} className="font-semibold text-blue-600 underline underline-offset-4 dark:text-blue-400">{t('mnt.predictions.clearFilters')}</button>
    </div>

    {error ? <div role="alert" className="rounded-xl bg-red-50 p-6 text-center text-red-700 dark:bg-red-950/30 dark:text-red-300">
      <AlertCircle className="mx-auto mb-3 h-6 w-6" aria-hidden="true" /><p>{t(`mnt.predictions.${error}`)}</p>
      <button type="button" onClick={retry} disabled={refreshing} className={`${buttonClass} mt-4`}>{t('mnt.predictions.retry')}</button>
    </div> : busy ? <div role="status" aria-label={t('mnt.predictions.loading')} className="space-y-3" aria-busy="true">
      {[0, 1, 2, 3, 4].map(index => <div key={index} className="h-20 animate-pulse rounded-xl bg-nike-soft-cloud dark:bg-nike-dark-surface" />)}
    </div> : rows.length === 0 ? <div className="rounded-xl border border-dashed border-nike-hairline py-14 text-center dark:border-nike-dark-card">
      <Calendar className="mx-auto mb-3 h-8 w-8 text-nike-mute" aria-hidden="true" /><p className="font-semibold text-nike-ink dark:text-white">{t('mnt.predictions.empty')}</p>
      <p className="mt-2 text-sm text-nike-mute dark:text-nike-stone">{t('mnt.predictions.emptyHint')}</p>
    </div> : <>
      <div className="hidden overflow-x-auto md:block"><table className="w-full text-left text-sm">
        <thead className="border-b border-nike-hairline text-xs text-nike-mute dark:border-nike-dark-card dark:text-nike-stone"><tr>
          {['roomTask', 'predictedDate', 'status', 'actions'].map(key => <th key={key} scope="col" className="px-3 py-3 font-semibold">{t(`mnt.predictions.${key}`)}</th>)}
        </tr></thead>
        <tbody className="divide-y divide-nike-hairline dark:divide-nike-dark-card">{visibleRows.map(row => <tr key={`${row.roomId}/${row.taskTypeId}`}>
          <td className="px-3 py-5"><span className="inline-block rounded-lg bg-nike-soft-cloud px-2 py-1 font-bold text-nike-ink dark:bg-nike-dark-surface dark:text-white">{row.roomNumber}</span>
            <div className="mt-2 font-semibold text-nike-ink dark:text-white">{taskName(row)}</div><div className="mt-1 text-xs text-nike-mute dark:text-nike-stone">{row.buildingName || '—'}</div></td>
          <td className="px-3 py-5">{dateContent(row)}</td><td className="px-3 py-5">{badge(row)}</td><td className="px-3 py-5">{appointmentButton(row)}</td>
        </tr>)}</tbody>
      </table></div>
      <div className="space-y-3 md:hidden">{visibleRows.map(row => <article key={`${row.roomId}/${row.taskTypeId}`} className="space-y-4 rounded-xl border border-nike-hairline p-4 dark:border-nike-dark-card">
        <div><span className="rounded-lg bg-nike-soft-cloud px-2 py-1 text-sm font-bold text-nike-ink dark:bg-nike-dark-surface dark:text-white">{row.roomNumber}</span>
          <h3 className="mt-3 font-semibold text-nike-ink dark:text-white">{taskName(row)}</h3><p className="mt-1 text-xs text-nike-mute dark:text-nike-stone">{row.buildingName || '—'}</p></div>
        <div><p className="mb-1 text-xs text-nike-mute dark:text-nike-stone">{t('mnt.predictions.predictedDate')}</p>{dateContent(row)}</div>
        <div><p className="mb-1 text-xs text-nike-mute dark:text-nike-stone">{t('mnt.predictions.status')}</p>{badge(row)}</div>
        <div><p className="mb-1 text-xs text-nike-mute dark:text-nike-stone">{t('mnt.predictions.actions')}</p>{appointmentButton(row)}</div>
      </article>)}</div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-nike-hairline pt-4 text-xs text-nike-mute dark:border-nike-dark-card dark:text-nike-stone">
        <span>{predictionText(t('mnt.predictions.pageRange'), { start: start + 1, end: Math.min(start + PAGE_SIZE, rows.length), total: rows.length })}</span>
        <div className="flex gap-2"><button type="button" className={buttonClass} disabled={currentPage <= 1} onClick={() => setPage(currentPage - 1)}><ChevronLeft className="h-4 w-4" aria-hidden="true" />{t('mnt.predictions.previous')}</button>
          <button type="button" className={buttonClass} disabled={currentPage >= totalPages} onClick={() => setPage(currentPage + 1)}>{t('mnt.predictions.next')}<ChevronRight className="h-4 w-4" aria-hidden="true" /></button></div>
      </div>
    </>}
    {appointmentRow && <MaintenanceAppointmentModal row={appointmentRow} onClose={closeAppointment}
      onSaved={() => { closeAppointment(); retry(); }} />}
  </section>;
};
