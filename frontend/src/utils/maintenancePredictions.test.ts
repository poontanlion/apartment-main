import { describe, expect, it } from 'vitest';
import { cardToStatusQuery, predictionDaysText, predictionStatuses, predictionStatusPresentation, predictionText, statusQueryToCard } from './maintenancePredictions';
import { formatDateDMY } from './formatters';

describe('Bangkok date-only formatting', () => {
  it.each([
    ['2026-01-31', '31/01/2026'], ['2026-12-31', '31/12/2026'],
    ['2024-02-29', '29/02/2024'], ['2000-02-29', '29/02/2000'],
    ['2026-01-01', '01/01/2026'], ['2026-03-01', '01/03/2026'],
  ])('formats %s without a timezone conversion', (input, output) => expect(formatDateDMY(input)).toBe(output));
  it.each([null, undefined, '', '2026-02-29', '1900-02-29', '2026-04-31', '2026-13-01', '2026-00-01', '2026-01-00', '2026-1-1', '2026-01-01T00:00:00Z'])
    ('rejects missing or invalid date %s', value => expect(formatDateDMY(value)).toBe('—'));
});

describe('prediction status presentation', () => {
  it.each([
    ['overdue', 'red'], ['due_soon', 'amber'], ['within_30', 'blue'], ['on_track', 'emerald'], ['no_data', 'nike'],
  ] as const)('maps %s to its translation key and color', (status, color) => {
    const presentation = predictionStatusPresentation(status);
    expect(presentation.key).toBe(`mnt.predictions.status.${status}`);
    expect(presentation.color).toContain(color);
    expect(presentation.color).toContain('dark:');
  });
  it('covers every API status', () => expect(predictionStatuses).toHaveLength(5));
});

describe('days remaining text', () => {
  const messages: Record<string, string> = {
    'mnt.predictions.today': 'วันนี้', 'mnt.predictions.daysRemaining': 'อีก {count} วัน',
    'mnt.predictions.daysOverdue': 'เลยกำหนด {count} วัน',
  };
  it.each([[0, 'วันนี้'], [1, 'อีก 1 วัน'], [-1, 'เลยกำหนด 1 วัน'], [null, '—']] as const)
    ('formats %s', (days, expected) => expect(predictionDaysText(days, key => messages[key])).toBe(expected));
  it('replaces all named numeric placeholders', () => {
    expect(predictionText('{start}–{end} / {total} ({total})', { start: 1, end: 20, total: 21 })).toBe('1–20 / 21 (21)');
  });
});

describe('summary card and status query mapping', () => {
  it.each([['overdue', 'overdue'], ['dueSoon', 'due_soon'], ['within30', 'due_soon,within_30'], ['total', '']] as const)
    ('round trips %s', (card, query) => {
      expect(cardToStatusQuery(card)).toBe(query);
      expect(statusQueryToCard(query)).toBe(card);
    });
  it('recognizes reordered, duplicated combined statuses', () => expect(statusQueryToCard('within_30, due_soon,due_soon')).toBe('within30'));
  it.each(['on_track', 'no_data', 'within_30', 'overdue,due_soon', 'unknown'])
    ('does not select a summary card for %s', value => expect(statusQueryToCard(value)).toBeNull());
});
