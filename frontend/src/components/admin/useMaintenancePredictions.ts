import { useEffect, useRef, useState } from 'react';
import { getMaintenancePredictions, MaintenancePredictionApiError, refreshMaintenancePredictions } from '../../services/api';
import { MaintenancePredictionFilters, MaintenancePredictionResponse, MaintenancePredictionRow } from '../../types';

export type PredictionErrorState = 'accessError' | 'error' | null;
const errorState = (error: unknown): PredictionErrorState =>
  error instanceof MaintenancePredictionApiError && [401, 403].includes(error.status) ? 'accessError' : 'error';

export function useMaintenancePredictions(filters: MaintenancePredictionFilters,
  notify: (result: { success: number; failure: number }) => void) {
  const { buildingId, roomId, taskTypeId, status, q } = filters;
  const [data, setData] = useState<MaintenancePredictionResponse | null>(null);
  const [options, setOptions] = useState<MaintenancePredictionRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<PredictionErrorState>(null);
  const [optionError, setOptionError] = useState<PredictionErrorState>(null);
  const [revision, setRevision] = useState(0);
  const autoRefreshAttempted = useRef(false);
  const loadController = useRef<AbortController>();
  const refreshController = useRef<AbortController>();
  const refreshingRef = useRef(false);
  const mounted = useRef(false);
  const notifyRef = useRef(notify);
  const baselineRequest = useRef<{ buildingId?: string; revision: number; usedForRows: boolean; request: Promise<MaintenancePredictionResponse> }>();
  notifyRef.current = notify;

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      refreshController.current?.abort();
    };
  }, []);

  // Cache dropdown choices independently of the current row/status/search filters.
  useEffect(() => {
    const controller = new AbortController();
    setOptions([]);
    setOptionError(null);
    const request = getMaintenancePredictions({ buildingId }, controller.signal);
    baselineRequest.current = { buildingId, revision, usedForRows: false, request };
    request.then(result => {
      if (!controller.signal.aborted) setOptions(result.predictions);
    }).catch(reason => {
      if (!controller.signal.aborted) setOptionError(errorState(reason));
    });
    return () => controller.abort();
  }, [buildingId, revision]);

  useEffect(() => {
    const controller = new AbortController();
    loadController.current = controller;
    setLoading(true);
    setError(null);
    const load = async () => {
      try {
        const baseline = baselineRequest.current;
        const onlyBuilding = !roomId && !taskTypeId && !status && !q;
        const shareBaseline = onlyBuilding && baseline && !baseline.usedForRows
          && baseline.buildingId === buildingId && baseline.revision === revision;
        if (baseline) baseline.usedForRows = true;
        const result = await (shareBaseline ? baseline.request
          : getMaintenancePredictions({ buildingId, roomId, taskTypeId, status, q }, controller.signal));
        if (controller.signal.aborted) return;
        const unfiltered = !buildingId && !roomId && !taskTypeId && !status && !q;
        if (unfiltered && result.summary.total === 0 && !autoRefreshAttempted.current && !refreshingRef.current) {
          autoRefreshAttempted.current = true;
          refreshingRef.current = true;
          setRefreshing(true);
          // Refresh is a write: let it finish even if the user changes filters.
          // The aborted read must still never apply its response to the new filters.
          const refresh = new AbortController();
          refreshController.current = refresh;
          try {
            const counts = await refreshMaintenancePredictions(refresh.signal);
            if (!mounted.current || refresh.signal.aborted) return;
            notifyRef.current(counts);
            setRevision(value => value + 1);
          } finally {
            refreshingRef.current = false;
            if (mounted.current) setRefreshing(false);
          }
          return;
        }
        if (!controller.signal.aborted) setData(result);
      } catch (reason) {
        if (!controller.signal.aborted) setError(errorState(reason));
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    };
    void load();
    return () => controller.abort();
  }, [buildingId, roomId, taskTypeId, status, q, revision]);

  const refresh = async () => {
    if (refreshingRef.current) return;
    refreshingRef.current = true;
    setRefreshing(true);
    loadController.current?.abort();
    const controller = new AbortController();
    refreshController.current = controller;
    try {
      const result = await refreshMaintenancePredictions(controller.signal);
      if (!controller.signal.aborted && mounted.current) {
        notifyRef.current(result);
        setRevision(value => value + 1);
      }
    } catch (reason) {
      if (!controller.signal.aborted && mounted.current) setError(errorState(reason));
    } finally {
      refreshingRef.current = false;
      if (mounted.current) setRefreshing(false);
    }
  };

  return { data, options, loading, refreshing, error: error || optionError, refresh,
    retry: () => setRevision(value => value + 1) };
}
