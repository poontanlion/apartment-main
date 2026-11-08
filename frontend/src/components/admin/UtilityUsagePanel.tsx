import React, { useMemo, useState } from 'react';
import {
  Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { BarChart3, Droplets, FileSpreadsheet, Zap } from 'lucide-react';
import { Room, UtilityBill } from '../../types';
import { useLanguage } from '../../context/LanguageContext';
import { UtilityCsvImport } from './UtilityCsvImport';
import {
  MONTH_NAMES_EN, MONTH_SHORT_EN, UsageGroupBy, UsageMetric, UsagePeriod,
  billingYears, usageByGroup, usageByPeriod,
} from '../../utils/utilityUsage';

const MONTH_NAMES_TH = [
  'มกราคม', 'กุมภาพันธ์', 'มีนาคม', 'เมษายน', 'พฤษภาคม', 'มิถุนายน',
  'กรกฎาคม', 'สิงหาคม', 'กันยายน', 'ตุลาคม', 'พฤศจิกายน', 'ธันวาคม',
];
const MONTH_SHORT_TH = ['ม.ค.', 'ก.พ.', 'มี.ค.', 'เม.ย.', 'พ.ค.', 'มิ.ย.', 'ก.ค.', 'ส.ค.', 'ก.ย.', 'ต.ค.', 'พ.ย.', 'ธ.ค.'];

const COLORS: Record<UsageMetric, string> = { water: '#0ea5e9', electric: '#f59e0b' };

const selectClass =
  'px-3 py-1.5 text-xs rounded-xl bg-nike-soft-cloud dark:bg-nike-dark-surface border border-nike-hairline dark:border-nike-dark-card text-nike-ink dark:text-white font-semibold cursor-pointer focus:outline-none';

interface Props {
  bills: UtilityBill[];
  rooms: Room[];
  /** Called after bills are imported, so the page can load them again. */
  onImported: () => void | Promise<void>;
}

/** Segmented buttons that share the look of the status filter on the bills table. */
const Segmented = <T extends string>(props: {
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
}) => (
  <div className="flex bg-nike-soft-cloud dark:bg-nike-dark-surface p-1 rounded-xl border border-nike-hairline dark:border-nike-dark-card text-xs">
    {props.options.map(option => (
      <button
        key={option.value}
        type="button"
        onClick={() => props.onChange(option.value)}
        className={`px-3 py-1 rounded-lg font-medium transition-all ${
          props.value === option.value
            ? 'bg-nike-canvas dark:bg-nike-dark-elevated text-nike-ink dark:text-white shadow-xs'
            : 'text-nike-mute'
        }`}
      >
        {option.label}
      </button>
    ))}
  </div>
);

export const UtilityUsagePanel: React.FC<Props> = ({ bills, rooms, onImported }) => {
  const { language } = useLanguage();
  const isThai = language === 'th';

  const [metric, setMetric] = useState<UsageMetric>('water');
  const [groupBy, setGroupBy] = useState<UsageGroupBy>('room');
  const [period, setPeriod] = useState<UsagePeriod>('month');
  const [yearChoice, setYearChoice] = useState<number | null>(null);
  const [monthChoice, setMonthChoice] = useState<number | 'all'>('all');
  const [buildingId, setBuildingId] = useState('All');
  const [showImport, setShowImport] = useState(false);

  const years = useMemo(() => billingYears(bills), [bills]);
  // Until the person picks a year, show the latest one that has bills.
  const year = yearChoice ?? years[years.length - 1] ?? new Date().getFullYear();

  const buildings = useMemo(() => {
    const seen = new Map<string, string>();
    for (const room of rooms) {
      if (room.buildingId) seen.set(room.buildingId, room.buildingName || room.buildingId);
    }
    return [...seen.entries()];
  }, [rooms]);

  const groupData = useMemo(
    () => usageByGroup(bills, rooms, { metric, groupBy, year, monthIndex: monthChoice, buildingId }),
    [bills, rooms, metric, groupBy, year, monthChoice, buildingId],
  );

  const periodData = useMemo(() => {
    const points = usageByPeriod(bills, rooms, { metric, period, year, buildingId });
    if (period !== 'month') return points;
    return points.map((point, i) => ({ ...point, label: isThai ? MONTH_SHORT_TH[i] : MONTH_SHORT_EN[i] }));
  }, [bills, rooms, metric, period, year, buildingId, isThai]);

  const color = COLORS[metric];
  const unitText = metric === 'water' ? (isThai ? 'หน่วยน้ำ' : 'water units') : (isThai ? 'หน่วยไฟ' : 'electric units');
  const hasGroupData = groupData.some(point => point.value > 0);
  const hasPeriodData = periodData.some(point => point.value > 0);

  const yearOptions = years.includes(year) ? years : [...years, year].sort((a, b) => a - b);
  const monthLabel = monthChoice === 'all'
    ? (isThai ? `ทั้งปี ${year}` : `All of ${year}`)
    : `${isThai ? MONTH_NAMES_TH[monthChoice] : MONTH_NAMES_EN[monthChoice]} ${year}`;

  const tooltipStyle = { borderRadius: 12, fontSize: 12 };
  const tickStyle = { fontSize: 11, fill: '#94a3b8' };
  const formatTip = (value: unknown) => [`${Number(value).toLocaleString()} ${unitText}`, unitText] as [string, string];

  return (
    <div className="bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card rounded-2xl p-6 space-y-5 shadow-xs">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <h3 className="text-lg font-bold text-nike-ink dark:text-white flex items-center gap-2">
          <BarChart3 className="w-5 h-5 text-emerald-600" />
          {isThai ? 'กราฟการใช้น้ำ / ไฟฟ้า' : 'Water & Electricity Usage'}
        </h3>

        <div className="flex flex-wrap items-center gap-3">
          <button
            type="button"
            onClick={() => setShowImport(true)}
            className="px-3 py-1.5 text-xs font-semibold rounded-xl bg-emerald-600 text-white hover:bg-emerald-700 transition-all flex items-center gap-1.5"
          >
            <FileSpreadsheet className="w-3.5 h-3.5" /> {isThai ? 'นำเข้า CSV' : 'Import CSV'}
          </button>

          <div className="flex bg-nike-soft-cloud dark:bg-nike-dark-surface p-1 rounded-xl border border-nike-hairline dark:border-nike-dark-card text-xs">
            <button
              type="button"
              onClick={() => setMetric('water')}
              className={`px-3 py-1 rounded-lg font-medium transition-all flex items-center gap-1.5 ${metric === 'water' ? 'bg-nike-canvas dark:bg-nike-dark-elevated text-sky-600 dark:text-sky-400 shadow-xs' : 'text-nike-mute'}`}
            >
              <Droplets className="w-3.5 h-3.5" /> {isThai ? 'น้ำ' : 'Water'}
            </button>
            <button
              type="button"
              onClick={() => setMetric('electric')}
              className={`px-3 py-1 rounded-lg font-medium transition-all flex items-center gap-1.5 ${metric === 'electric' ? 'bg-nike-canvas dark:bg-nike-dark-elevated text-amber-600 dark:text-amber-400 shadow-xs' : 'text-nike-mute'}`}
            >
              <Zap className="w-3.5 h-3.5" /> {isThai ? 'ไฟ' : 'Electric'}
            </button>
          </div>

          <select value={buildingId} onChange={e => setBuildingId(e.target.value)} className={selectClass}>
            <option value="All">{isThai ? 'อาคารทั้งหมด' : 'All Buildings'}</option>
            {buildings.map(([id, name]) => (
              <option key={id} value={id}>{name}</option>
            ))}
          </select>

          <select value={year} onChange={e => setYearChoice(Number(e.target.value))} className={selectClass}>
            {yearOptions.map(y => (
              <option key={y} value={y}>{y}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
        {/* BY ROOM OR FLOOR */}
        <div className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm font-semibold text-nike-ink dark:text-white">
              {isThai ? 'แยกตามห้อง / ชั้น' : 'By room / floor'} · {monthLabel}
            </p>
            <div className="flex flex-wrap items-center gap-2">
              <Segmented
                value={groupBy}
                onChange={setGroupBy}
                options={[
                  { value: 'room', label: isThai ? 'ห้อง' : 'Room' },
                  { value: 'floor', label: isThai ? 'ชั้น' : 'Floor' },
                ]}
              />
              <select
                value={monthChoice}
                onChange={e => setMonthChoice(e.target.value === 'all' ? 'all' : Number(e.target.value))}
                className={selectClass}
              >
                <option value="all">{isThai ? 'ทั้งปี' : 'Whole year'}</option>
                {MONTH_NAMES_EN.map((name, i) => (
                  <option key={name} value={i}>{isThai ? MONTH_NAMES_TH[i] : name}</option>
                ))}
              </select>
            </div>
          </div>

          {hasGroupData ? (
            <div className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={groupData} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#94a3b8" strokeOpacity={0.25} />
                  <XAxis
                    dataKey="label"
                    tick={tickStyle}
                    tickFormatter={(label: string) => (groupBy === 'floor' ? `${isThai ? 'ชั้น' : 'Fl.'} ${label}` : label)}
                  />
                  <YAxis tick={tickStyle} allowDecimals={false} />
                  <Tooltip contentStyle={tooltipStyle} formatter={formatTip} />
                  <Bar dataKey="value" fill={color} radius={[6, 6, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <p className="h-72 flex items-center justify-center text-xs text-nike-mute">
              {isThai ? 'ยังไม่มีข้อมูลบิลในช่วงที่เลือก' : 'No bills for the selected period.'}
            </p>
          )}
        </div>

        {/* BY MONTH OR YEAR */}
        <div className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm font-semibold text-nike-ink dark:text-white">
              {isThai ? 'แยกตามเดือน / ปี' : 'By month / year'}
              {period === 'month' ? ` · ${year}` : ''}
            </p>
            <Segmented
              value={period}
              onChange={setPeriod}
              options={[
                { value: 'month', label: isThai ? 'รายเดือน' : 'Monthly' },
                { value: 'year', label: isThai ? 'รายปี' : 'Yearly' },
              ]}
            />
          </div>

          {hasPeriodData ? (
            <div className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                {period === 'month' ? (
                  <LineChart data={periodData} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#94a3b8" strokeOpacity={0.25} />
                    <XAxis dataKey="label" tick={tickStyle} />
                    <YAxis tick={tickStyle} allowDecimals={false} />
                    <Tooltip contentStyle={tooltipStyle} formatter={formatTip} />
                    <Line type="monotone" dataKey="value" stroke={color} strokeWidth={2.5} dot={{ r: 3 }} />
                  </LineChart>
                ) : (
                  <BarChart data={periodData} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#94a3b8" strokeOpacity={0.25} />
                    <XAxis dataKey="label" tick={tickStyle} />
                    <YAxis tick={tickStyle} allowDecimals={false} />
                    <Tooltip contentStyle={tooltipStyle} formatter={formatTip} />
                    <Bar dataKey="value" fill={color} radius={[6, 6, 0, 0]} />
                  </BarChart>
                )}
              </ResponsiveContainer>
            </div>
          ) : (
            <p className="h-72 flex items-center justify-center text-xs text-nike-mute">
              {isThai ? 'ยังไม่มีข้อมูลบิล' : 'No bills yet.'}
            </p>
          )}
        </div>
      </div>

      <p className="text-[11px] text-nike-mute">
        {isThai
          ? 'หน่วยที่ใช้ = เลขมิเตอร์ปัจจุบัน − เลขมิเตอร์ก่อนหน้า ตามที่บันทึกในแต่ละบิล'
          : 'Units used = current meter reading − previous reading, as recorded on each bill.'}
      </p>

      {showImport && (
        <UtilityCsvImport rooms={rooms} bills={bills} onClose={() => setShowImport(false)} onImported={onImported} />
      )}
    </div>
  );
};
