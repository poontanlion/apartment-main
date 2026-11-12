import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertCircle, Calendar } from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';
import { getMaintenancePredictions } from '../../services/api';
import { MaintenancePredictionResponse, MaintenancePredictionSummary } from '../../types';
import { maintenanceDashboardState, maintenancePredictionLink, maintenanceSummaryLinks } from '../../utils/maintenanceDashboard';

export const MaintenancePredictionsDashboardCard: React.FC<{ buildingId?: string }> = ({ buildingId }) => {
  const { t } = useLanguage();
  const [summary, setSummary] = useState<MaintenancePredictionSummary | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [revision, setRevision] = useState(0);
  const request = useRef<{ buildingId?: string; revision: number; promise: Promise<MaintenancePredictionResponse> }>();
  useEffect(() => {
    let current = true;
    setSummary(null); setError(null);
    // Reuse the same request during StrictMode's setup/cleanup/setup probe.
    // The current guard also prevents old responses from applying after a filter change.
    if (!request.current || request.current.buildingId !== buildingId || request.current.revision !== revision)
      request.current = { buildingId, revision, promise: getMaintenancePredictions({ buildingId }) };
    request.current.promise.then(result => { if (current) setSummary(result.summary); })
      .catch(reason => { if (current) setError(reason); });
    return () => { current = false; };
  }, [buildingId, revision]);
  const state = maintenanceDashboardState(summary, error);
  const colors = { overdue: 'text-red-600 dark:text-red-400', dueSoon: 'text-orange-600 dark:text-orange-400', within30: 'text-blue-600 dark:text-blue-400' };
  return <section aria-label={t('mnt.predictions.dashboardTitle')} className="space-y-4 rounded-2xl border border-nike-hairline bg-nike-canvas p-5 shadow-xs dark:border-nike-dark-card dark:bg-nike-dark-elevated">
    <h2 className="flex items-center gap-2 text-base font-bold text-nike-ink dark:text-white"><Calendar className="h-5 w-5 text-blue-600 dark:text-blue-400" aria-hidden="true" />{t('mnt.predictions.dashboardTitle')}</h2>
    {state === 'loading' ? <div role="status" aria-label={t('mnt.predictions.loading')} className="grid grid-cols-1 gap-3 sm:grid-cols-3">
      {[0, 1, 2].map(key => <div key={key} className="h-24 animate-pulse rounded-xl bg-nike-soft-cloud dark:bg-nike-dark-surface" />)}
    </div> : state === 'error' || state === 'accessError' ? <div role="alert" className="flex flex-wrap items-center gap-3 text-sm text-red-600 dark:text-red-300">
      <AlertCircle className="h-4 w-4" aria-hidden="true" /><span>{t(`mnt.predictions.${state}`)}</span>
      <button type="button" onClick={() => setRevision(value => value + 1)} className="font-semibold underline underline-offset-4">{t('mnt.predictions.retry')}</button>
    </div> : state === 'empty' ? <div className="space-y-2 text-sm">
      <p className="text-nike-mute dark:text-nike-stone">{t('mnt.predictions.dashboardEmpty')}</p>
      <Link to={maintenancePredictionLink('', buildingId)} className="inline-block font-semibold text-blue-600 underline underline-offset-4 dark:text-blue-400">{t('mnt.predictions.viewPredictions')}</Link>
    </div> : summary && <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
      {maintenanceSummaryLinks(summary, buildingId).map(item => <Link key={item.key} to={item.to}
        className="rounded-xl border border-nike-hairline p-4 transition-colors hover:border-blue-400 hover:bg-nike-soft-cloud focus:outline-none focus:ring-2 focus:ring-blue-500 dark:border-nike-dark-card dark:hover:bg-nike-dark-surface">
        <span className="text-sm font-semibold text-nike-mute dark:text-nike-stone">{t(`mnt.predictions.${item.key}`)}</span>
        <span className={`mt-2 block text-3xl font-bold tabular-nums ${colors[item.key]}`}>{item.count}</span>
      </Link>)}
    </div>}
  </section>;
};
