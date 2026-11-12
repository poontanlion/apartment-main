export type MatchKind = 'exact' | 'prefix' | 'substring' | 'fuzzy' | 'none';
export type MatchResult = { matched: boolean; score: number; distance: number; kind: MatchKind };
export type FilterResult<T> = { item: T; score: number; kind: MatchKind };

export const DISTANCE_THRESHOLDS: readonly { readonly maxLength: number; readonly distance: number }[] = [
  { maxLength: 2, distance: 0 },
  { maxLength: 5, distance: 1 },
  { maxLength: 9, distance: 2 },
  { maxLength: Infinity, distance: 3 },
];

export const normalizeText = (input: string): string => input.normalize('NFC')
  .replace(/[\u200B-\u200D\u2060\uFEFF]/gu, '')
  // Decompose Latin letters only: Thai vowels must survive accent folding.
  .replace(/\p{Script=Latin}\p{M}*/gu, value => value.normalize('NFD').replace(/\p{M}/gu, ''))
  .replace(/([\u0E31\u0E34-\u0E3A\u0E47-\u0E4E])\1+/gu, '$1')
  .replace(/[\u0E48-\u0E4C]/gu, '')
  .toLowerCase().trim().replace(/\s+/gu, ' ').normalize('NFC');

export const stripHonorific = (input: string): string => {
  const trimmed = input.trim();
  // Thai titles commonly touch the name; Latin titles require a boundary.
  const stripped = trimmed.replace(/^(?:นางสาว|น\.?\s*ส\.?|นาย|นาง|คุณ)\s*|^(?:mrs|miss|mr|ms)\.?(?:\s+|$)/iu, '').trim();
  return stripped || input;
};

/** Optimal string alignment distance: adjacent transpositions cost one edit.
 * A substring cannot be edited twice (the restricted Damerau variant).
 */
export const damerauLevenshtein = (a: string, b: string, maxDistance?: number): number => {
  if (maxDistance !== undefined && (!Number.isInteger(maxDistance) || maxDistance < 0)) {
    throw new RangeError('maxDistance must be a non-negative integer');
  }
  const left = Array.from(a);
  const right = Array.from(b);
  const limit = maxDistance ?? Math.max(left.length, right.length);
  const overflow = limit + 1;
  if (Math.abs(left.length - right.length) > limit) return overflow;
  let previous = Array.from({ length: right.length + 1 }, (_, j) => j <= limit ? j : overflow);
  let beforePrevious = previous;
  for (let i = 1; i <= left.length; i++) {
    const current = new Array<number>(right.length + 1).fill(overflow);
    current[0] = i <= limit ? i : overflow;
    for (let j = Math.max(1, i - limit); j <= Math.min(right.length, i + limit); j++) {
      current[j] = Math.min(previous[j] + 1, current[j - 1] + 1,
        previous[j - 1] + (left[i - 1] === right[j - 1] ? 0 : 1));
      if (i > 1 && j > 1 && left[i - 1] === right[j - 2] && left[i - 2] === right[j - 1]) {
        current[j] = Math.min(current[j], beforePrevious[j - 2] + 1);
      }
    }
    beforePrevious = previous;
    previous = current;
  }
  return Math.min(previous[right.length], overflow);
};

export const allowedDistance = (queryLength: number): number =>
  DISTANCE_THRESHOLDS.find(row => queryLength <= row.maxLength)?.distance ?? 3;

const prepare = (value: string): string => normalizeText(stripHonorific(value));
const none = (): MatchResult => ({ matched: false, score: 0, distance: Infinity, kind: 'none' });
const result = (kind: Exclude<MatchKind, 'none'>, distance = 0): MatchResult => ({
  matched: true,
  score: { exact: 400, prefix: 300, substring: 200, fuzzy: 100 }[kind] + 1 / (1 + distance),
  distance,
  kind,
});

const matchToken = (query: string, target: string): MatchResult => {
  if (query === target) return result('exact');
  if (target.startsWith(query)) return result('prefix');
  if (target.includes(query)) return result('substring');
  const length = Array.from(query).length;
  const limit = /\p{L}/u.test(query) ? allowedDistance(length) : 0;
  const distance = Math.min(damerauLevenshtein(query, target, limit),
    damerauLevenshtein(query, Array.from(target).slice(0, length).join(''), limit));
  return distance <= limit ? result('fuzzy', distance) : none();
};

export const scoreMatch = (query: string, target: string): MatchResult => {
  const q = prepare(query);
  const t = prepare(target);
  if (!q) return result('exact');
  if (!t) return none();
  const targets = t.split(' ');
  const matches = q.split(' ').map(token => targets.map(candidate => matchToken(token, candidate))
    .reduce((best, candidate) => candidate.score > best.score ? candidate : best, none()));
  if (matches.some(match => !match.matched)) return none();
  if (q === t) return result('exact');
  if (t.startsWith(q)) return result('prefix');
  if (t.includes(q)) return result('substring');
  const weakest = matches.reduce((best, match) => match.score < best.score ? match : best);
  const distance = matches.reduce((sum, match) => sum + match.distance, 0);
  return result(weakest.kind as Exclude<MatchKind, 'none'>, distance);
};

export const fuzzyFilter = <T>(items: T[], query: string, getTexts: (item: T) => string[]): FilterResult<T>[] => {
  if (!prepare(query)) return items.map(item => ({ item, score: 401, kind: 'exact' }));
  return items.map((item, index) => {
    const match = (getTexts(item) ?? []).filter(text => typeof text === 'string').map(text => scoreMatch(query, text))
      .reduce((best, candidate) => candidate.score > best.score ? candidate : best, none());
    return { item, index, ...match };
  }).filter(entry => entry.matched)
    .sort((a, b) => b.score - a.score || a.index - b.index)
    .map(({ item, score, kind }) => ({ item, score, kind }));
};
