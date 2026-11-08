import React from 'react';
import { Search, X } from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';

export interface SmartSearchInputProps {
  value: string; onChange: (value: string) => void; placeholder?: string;
  className?: string; autoFocus?: boolean; resultCount?: number;
}

export const SmartSearchInput = ({ value, onChange, placeholder, className, autoFocus, resultCount }: SmartSearchInputProps) => {
  const { t } = useLanguage();
  return <div className={`w-full min-w-0 sm:w-80 sm:shrink-0 ${className ?? ''}`}>
    <div className="flex items-center gap-2 rounded-xl bg-nike-soft-cloud dark:bg-nike-dark-surface border border-nike-hairline dark:border-nike-dark-card px-3 focus-within:ring-2 focus-within:ring-nike-accent-purple-soft">
      <Search aria-hidden="true" className="pointer-events-none w-4 h-4 shrink-0 text-nike-mute" />
      <input type="text" value={value} onChange={event => onChange(event.target.value)} autoFocus={autoFocus}
        onKeyDown={event => { if (event.key === 'Escape' && !event.nativeEvent.isComposing) { event.preventDefault(); onChange(''); } }}
        aria-label={t('smartSearch.label')} title={t('smartSearch.hint')} placeholder={placeholder ?? t('smartSearch.placeholder')}
        className="w-full min-w-0 flex-1 py-1.5 text-xs bg-nike-soft-cloud dark:bg-nike-dark-surface text-nike-ink dark:text-white focus:outline-none" />
      <span aria-live="polite" className="shrink-0 whitespace-nowrap text-xs font-bold text-nike-ink dark:text-white">{resultCount !== undefined ? t('smartSearch.resultCount').replace('{count}', String(resultCount)) : null}</span>
      {value && <button type="button" onClick={() => onChange('')} aria-label={t('smartSearch.clear')}
        className="shrink-0 p-1 rounded text-nike-mute dark:text-nike-stone hover:text-nike-ink dark:hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-nike-accent-purple-soft">
        <X aria-hidden="true" className="w-4 h-4" />
      </button>}
    </div>
  </div>;
};
