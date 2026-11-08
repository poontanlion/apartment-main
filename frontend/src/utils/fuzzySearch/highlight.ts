import { scoreMatch } from './index';

/** Half-open UTF-16 ranges suitable for slicing the unchanged original text. */
export const getHighlightRanges = (query: string, text: string): Array<[number, number]> => {
  const queries = query.trim().split(/\s+/u).filter(Boolean);
  const ranges: Array<[number, number]> = [];
  for (const token of text.matchAll(/\S+/gu)) {
    const start = token.index!;
    for (const part of queries) {
      const match = scoreMatch(part, token[0]);
      if (!match.matched) continue;
      if (match.kind === 'fuzzy') {
        ranges.push([start, start + token[0].length]);
        continue;
      }
      // Match against original slices to preserve offsets even when case folding
      // changes length. Normalized-only matches intentionally receive no mark.
      for (let offset = 0; offset < token[0].length;) {
        const point = token[0].codePointAt(offset)!;
        for (let end = offset + (point > 0xFFFF ? 2 : 1); end <= token[0].length;) {
          if (token[0].slice(offset, end).toLowerCase() === part.toLowerCase()) ranges.push([start + offset, start + end]);
          if (end === token[0].length) break;
          end += token[0].codePointAt(end)! > 0xFFFF ? 2 : 1;
        }
        offset += point > 0xFFFF ? 2 : 1;
      }
    }
  }
  ranges.sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  return ranges.reduce<Array<[number, number]>>((merged, range) => {
    const last = merged[merged.length - 1];
    if (last && range[0] <= last[1]) last[1] = Math.max(last[1], range[1]);
    else merged.push([...range]);
    return merged;
  }, []);
};
