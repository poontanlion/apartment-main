import { expect, it } from 'vitest';
import { getHighlightRanges } from './highlight';

it.each([['','Kant',[]],['zzzz','Kant',[]],['kant','Kant Somchai',[[0,4]]],['kan','Kant',[[0,3]]],['ant','Kant',[[1,4]]],['krant','Kant Somchai',[[0,4]]],['ก้า','ก้า ดี',[[0,3]]],['ดี','ก้า ดี',[[4,6]]],['ab bc','abc',[[0,3]]]])('ranges for %s in %s', (q,t,ranges) => expect(getHighlightRanges(q,t)).toEqual(ranges));
it('returns ordered, non-overlapping original offsets including emoji', () => {
  const text = '😀 Kant ก้า Somchai';
  const ranges = getHighlightRanges('kant ก้า som',text);
  expect(ranges.map(([a,b])=>text.slice(a,b))).toEqual(['Kant','ก้า','Som']);
  ranges.forEach(([a,b],i)=>{
    expect(a).toBeGreaterThanOrEqual(0); expect(b).toBeLessThanOrEqual(text.length);
    expect(a).toBeLessThan(b); if(i) expect(a).toBeGreaterThan(ranges[i-1][1]);
  });
});
