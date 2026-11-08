import { describe, expect, it } from 'vitest';
import { allowedDistance, damerauLevenshtein as distance, fuzzyFilter, normalizeText, scoreMatch, stripHonorific } from './index';

describe('distance', () => {
  it.each([['kant','kant',0],['kat','kant',1],['kant','kat',1],['kart','kant',1],['knat','kant',1],['','',0],['','abc',3],['abc','',3],['a','abcdefgh',7],['😀','',1],['😀a','a😀',1],['กข','ขก',1]])('%s → %s = %i', (a,b,d) => {
    expect(distance(a,b)).toBe(d);
    for (let limit = 0; limit <= 3; limit++) expect(distance(a,b,limit)).toBe(Math.min(d,limit+1));
  });
  it.each([-1, 0.5, NaN, Infinity])('rejects invalid limit %s', limit => expect(() => distance('a','b',limit)).toThrow(RangeError));
  it.each([[0,0],[1,0],[2,0],[3,1],[5,1],[6,2],[9,2],[10,3],[30,3]])('threshold %i', (n,d) => expect(allowedDistance(n)).toBe(d));
});
describe('normalization and titles', () => {
  it.each([['  KaNT   SOMCHAI  ','kant somchai'],['K\u200ba\u200cn\u200dt\u2060\ufeff','kant'],['Élise Cafe\u0301','elise cafe'],['ก่้๊๋์า','กา'],['กิิา','กิา']])('normalizes %s', (a,b) => expect(normalizeText(a)).toBe(b));
  it.each(['นาย','นาง','นางสาว','น.ส.','นส','คุณ','Mr','Mr.','Mrs','Mrs.','Miss','Miss.','Ms','Ms.','mR.'])('strips %s', title => expect(stripHonorific(`${title} Kant`)).toBe('Kant'));
  it.each(['Mister','Missy','Mr','คุณ','  นาย  '])('preserves standalone/name %s', name => expect(stripHonorific(name)).toBe(name));
  it('characterizes ambiguous attached Thai title without hiding the limitation', () => {
    expect(scoreMatch('คุณากร','คุณากร').kind).toBe('exact');
    // Known limitation: attached คุณ is treated as a title even within this name.
    expect(stripHonorific('คุณากร')).toBe('ากร');
    expect(scoreMatch('คุณากร','Somchai Jaidee').matched).toBe(false);
  });
});
describe('matching', () => {
  it.each(['krant','kart'])('acceptance: %s finds kant', query => {
    expect(scoreMatch(query,'kant')).toMatchObject({ matched:true, kind:'fuzzy', distance:1 });
    expect(fuzzyFilter(['kant'], query, name => [name])).toHaveLength(1);
  });
  it('ranks exact > prefix > substring > fuzzy', () => {
    const matches = ['kant','kantana','xxkant','kart'].map(name => scoreMatch('kant',name));
    expect(matches.map(m => m.kind)).toEqual(['exact','prefix','substring','fuzzy']);
    expect(matches.map(m => m.score)).toEqual([...matches.map(m => m.score)].sort((a,b)=>b-a));
  });
  it.each([['kant somchai','Kant Somchai'],['somchai kant','Kant Somchai'],['kan','Kantana'],['krat','Krantana'],['ก้า Kant','กา Kant'],['กขค','กคข'],['กาน','กานา']])('matches tokens %s', (q,t) => expect(scoreMatch(q,t).matched).toBe(true));
  it('requires every token', () => expect(scoreMatch('kant zzzzzzz','Kant Somchai').matched).toBe(false));
  it('supports a missing Thai vowel and fuzzy unfinished prefix', () => {
    expect(scoreMatch('กนต์','กานต์').matched).toBe(true);
    expect(scoreMatch('krat','Krantana')).toMatchObject({matched:true, kind:'fuzzy', distance:1});
  });
  it('intentionally allows multiple query tokens to match the same target token', () => expect(scoreMatch('kan kant','kant').matched).toBe(true));
  it.each([['a','b'],['ab','ac'],['xy','zz'],['zz','kant'],['bob','xyz'],['abcd','wxyz'],['abcdef','uvwxyz'],['abcdefghij','zzzzzzzzzz'],['204','205'],['0800000204','0800000205']])('rejects unrelated %s / %s', (q,t) => expect(scoreMatch(q,t).matched).toBe(false));
  it('retains numeric substring and alphanumeric fuzzy', () => {
    expect(scoreMatch('0000','0800000205').kind).toBe('substring');
    expect(scoreMatch('A204','A205').kind).toBe('fuzzy');
  });
});
describe('filter', () => {
  const items = [{name:'kart',id:1},{name:'kant',id:2},{name:'kant',id:3}];
  it.each(['','   '])('empty query keeps original order %s', q => expect(fuzzyFilter(items,q,x=>[x.name]).map(r=>r.item)).toEqual(items));
  it('sorts stably without mutation and retains references', () => {
    const input = Object.freeze([...items]);
    const found = fuzzyFilter(input as typeof items,'kant',x=>[x.name]);
    expect(found.map(r=>r.item.id)).toEqual([2,3,1]);
    expect(found[0].item).toBe(items[1]);
    expect(input.map(x=>x.id)).toEqual([1,2,3]);
  });
  it('does not combine separate fields', () => {
    expect(fuzzyFilter([1],'Kant Somchai',()=>['Kant','Somchai'])).toHaveLength(0);
    expect(fuzzyFilter([1],'Kant Somchai',()=>['Kant Somchai'])).toHaveLength(1);
  });
  it('ignores empty and nullish runtime text values', () => {
    const getTexts = (() => [null, undefined, '', 'kant']) as unknown as (item:number)=>string[];
    expect(fuzzyFilter([1],'kant',getTexts)).toHaveLength(1);
    for (const value of [null, undefined, []]) {
      const emptyTexts = (() => value) as unknown as (item:number)=>string[];
      expect(fuzzyFilter([1],'kant',emptyTexts)).toEqual([]);
    }
  });
  it('benchmarks deterministic 500-name inputs', () => {
    const names = Array.from({length:500},(_,i)=>`Kant${i} Somchai${i}`);
    for (const query of ['ka','krant','Kant123 Somchai123','unrelatedlongquery']) {
      const start = performance.now();
      fuzzyFilter(names,query,name=>[name]);
      const elapsed = performance.now()-start;
      console.info(`synthetic benchmark length=${query.length}: ${elapsed.toFixed(2)} ms`);
      expect(elapsed).toBeLessThan(200);
    }
  });
});
