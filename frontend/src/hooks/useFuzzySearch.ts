import { useMemo } from 'react';
import { fuzzyFilter } from '../utils/fuzzySearch';
import type { FilterResult } from '../utils/fuzzySearch';
import { useDebouncedValue } from './useDebouncedValue';

/** Keep getTexts stable with useCallback or define it outside the component.
 * Results and hasFuzzyOnly describe debouncedQuery; isActive describes current input.
 */
export const useFuzzySearch = <T>(items: T[], query: string, getTexts: (item: T) => string[],
  options?: { debounceMs?: number }): {
    results: FilterResult<T>[]; debouncedQuery: string; isActive: boolean; hasFuzzyOnly: boolean;
  } => {
  const debouncedQuery = useDebouncedValue(query, options?.debounceMs ?? 250);
  const results = useMemo(() => fuzzyFilter(items, debouncedQuery, getTexts), [items, debouncedQuery, getTexts]);
  return { results, debouncedQuery, isActive: query.trim().length > 0,
    hasFuzzyOnly: results.length > 0 && results.every(result => result.kind === 'fuzzy') };
};
