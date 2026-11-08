import React from 'react';
import { getHighlightRanges } from '../../utils/fuzzySearch/highlight';

export interface HighlightedTextProps { text: string; query: string; className?: string }

export const HighlightedText = ({ text, query, className }: HighlightedTextProps) => {
  const nodes: React.ReactNode[] = [];
  let cursor = 0;
  for (const [start, end] of getHighlightRanges(query, text)) {
    nodes.push(text.slice(cursor, start));
    nodes.push(<mark key={start} className="rounded bg-nike-accent-purple-soft/40 text-nike-ink dark:bg-nike-accent-purple-soft/25 dark:text-white">{text.slice(start, end)}</mark>);
    cursor = end;
  }
  nodes.push(text.slice(cursor));
  return <span className={className}>{nodes}</span>;
};
