import React, { useEffect, useState } from 'react';
import { Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { Room, RoomPriceHistory } from '../../types';
import { addRoomPrice, deleteRoomPrice, getRoomPrices } from '../../services/api';
import { useLanguage } from '../../context/LanguageContext';
import { formatCurrency } from '../../utils/formatters';
import { priceRowStatus } from '../../utils/billingMonth';

interface RoomPricePanelProps {
  room: Room;
  /** Called with the price in effect this month after a change. */
  onChanged: (currentPrice: number) => void;
}

const BASELINE_MONTH = '2000-01';

// Prices are newest first, so the first one that has started is in effect.
const rowInEffect = (history: RoomPriceHistory) =>
  history.prices.find(row => row.effectiveMonth.slice(0, 7) <= history.currentMonth);

export const RoomPricePanel: React.FC<RoomPricePanelProps> = ({ room, onChanged }) => {
  const { language } = useLanguage();
  const th = language === 'th';
  const [history, setHistory] = useState<RoomPriceHistory | null>(null);
  const [loadError, setLoadError] = useState('');
  const [price, setPrice] = useState('');
  const [month, setMonth] = useState('');
  const [saving, setSaving] = useState(false);

  const activeRow = history ? rowInEffect(history) : undefined;

  const load = async (): Promise<RoomPriceHistory> => {
    const next = await getRoomPrices(room.id);
    setHistory(next);
    setMonth(current => current || next.currentMonth);
    return next;
  };

  useEffect(() => {
    load().catch((err: Error) => setLoadError(err.message));
  }, [room.id]);

  const refresh = async () => {
    const active = rowInEffect(await load());
    if (active) onChanged(active.price);
  };

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await addRoomPrice(room.id, Number(price), month);
      setPrice('');
      await refresh();
      toast.success(th ? 'บันทึกราคาแล้ว' : 'Price saved');
    } catch (err) {
      toast.error((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (priceId: string) => {
    try {
      await deleteRoomPrice(room.id, priceId);
      await refresh();
      toast.success(th ? 'ลบราคาที่ตั้งไว้แล้ว' : 'Scheduled price removed');
    } catch (err) {
      toast.error((err as Error).message);
    }
  };

  const monthLabel = (effectiveMonth: string) => {
    if (effectiveMonth.startsWith(BASELINE_MONTH)) return th ? 'ราคาเริ่มต้น' : 'Starting price';
    const [year, monthNumber] = effectiveMonth.split('-').map(Number);
    return new Date(year, monthNumber - 1, 1).toLocaleDateString(th ? 'th-TH' : 'en-US', { month: 'long', year: 'numeric' });
  };

  const statusLabel = {
    past: th ? 'ราคาเดิม' : 'Past',
    current: th ? 'ปัจจุบัน' : 'Current',
    scheduled: th ? 'ตั้งล่วงหน้า' : 'Scheduled',
  };

  const statusClass = {
    past: 'bg-nike-soft-cloud dark:bg-nike-dark-card text-nike-mute dark:text-nike-stone',
    current: 'bg-emerald-100 dark:bg-emerald-900/40 text-emerald-700 dark:text-emerald-300',
    scheduled: 'bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300',
  };

  const inputClass = 'w-full p-3 bg-nike-soft-cloud dark:bg-nike-dark-card border border-nike-hairline dark:border-nike-dark-card text-nike-ink dark:text-white text-[14px] rounded-xl focus:outline-none';

  return (
    <section aria-label={th ? 'ประวัติราคาห้อง' : 'Room price history'} className="space-y-3 text-[14px] border-t border-nike-hairline-soft dark:border-nike-dark-card pt-5">
      <div>
        <h4 className="font-bold text-nike-ink dark:text-white">{th ? 'ราคาห้องตามช่วงเวลา' : 'Room price over time'}</h4>
        <p className="text-[12px] text-nike-mute dark:text-nike-stone mt-1">
          {th
            ? 'ราคาใหม่ใช้กับการจองและสัญญาใหม่ ส่วนสัญญาที่ยังไม่หมดอายุและบิลที่ออกแล้วยังใช้ตัวเลขเดิม'
            : 'New prices apply to new bookings and leases. Running leases and issued bills keep their amounts.'}
        </p>
      </div>

      {loadError && <p role="alert" className="text-[13px] text-red-600">{loadError}</p>}

      {history && (
        <ul className="divide-y divide-nike-hairline-soft dark:divide-nike-dark-card">
          {history.prices.map(row => {
            const status = priceRowStatus(row.effectiveMonth, activeRow?.effectiveMonth ?? history.currentMonth);
            return (
              <li key={row.effectiveMonth} className="flex items-center justify-between gap-3 py-2">
                <div className="flex items-center gap-2 min-w-0">
                  <span className="font-medium text-nike-ink dark:text-white">{monthLabel(row.effectiveMonth)}</span>
                  <span className={`text-[11px] font-semibold px-2 py-0.5 rounded-full ${statusClass[status]}`}>{statusLabel[status]}</span>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <span className="font-semibold text-nike-ink dark:text-white">{formatCurrency(row.price)}</span>
                  {status === 'scheduled' && row.id && (
                    <button
                      type="button"
                      onClick={() => handleDelete(row.id as string)}
                      aria-label={th ? `ลบราคา ${monthLabel(row.effectiveMonth)}` : `Remove price for ${monthLabel(row.effectiveMonth)}`}
                      className="p-1 text-nike-mute hover:text-red-600 transition-colors"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {history && (
        <form onSubmit={handleAdd} className="grid grid-cols-1 sm:grid-cols-3 gap-3 items-end">
          <div>
            <label htmlFor="room-price-month" className="block font-medium mb-1.5 text-nike-ink dark:text-white">
              {th ? 'เริ่มมีผลเดือน' : 'Effective month'}
            </label>
            <input
              id="room-price-month"
              type="month"
              required
              min={history.currentMonth}
              value={month}
              onChange={e => setMonth(e.target.value)}
              className={inputClass}
            />
          </div>
          <div>
            <label htmlFor="room-price-amount" className="block font-medium mb-1.5 text-nike-ink dark:text-white">
              {th ? 'ราคาใหม่ (บาท)' : 'New price (THB)'}
            </label>
            <input
              id="room-price-amount"
              type="number"
              required
              min="1"
              value={price}
              onChange={e => setPrice(e.target.value)}
              className={inputClass}
            />
          </div>
          <button type="submit" disabled={saving} className="bg-blue-600 text-white font-semibold py-3 rounded-xl hover:bg-blue-700 disabled:opacity-60 transition-colors">
            {th ? 'ตั้งราคา' : 'Set price'}
          </button>
        </form>
      )}
    </section>
  );
};
