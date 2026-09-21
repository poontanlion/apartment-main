import React, { useEffect, useState } from 'react';
import { Search, Building2, Check, X, Ban } from 'lucide-react';
import { Booking, Building, Room } from '../../types';
import { getBookings, getBuildings, updateBookingStatus, getRooms } from '../../services/api';
import { useLanguage } from '../../context/LanguageContext';
import { toast } from 'sonner';
import { formatCurrency } from '../../utils/formatters';

export const BookingManagement: React.FC = () => {
  const { t, language } = useLanguage();
  
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [buildings, setBuildings] = useState<Building[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  
  const [filteredBookings, setFilteredBookings] = useState<Booking[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [buildingFilter, setBuildingFilter] = useState('All');
  const [statusFilter, setStatusFilter] = useState('All');

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [bookingList, bldList, roomList] = await Promise.all([
        getBookings(), 
        getBuildings(),
        getRooms()
      ]);
      setBookings(bookingList);
      setBuildings(bldList);
      setRooms(roomList);
      applyFilters(bookingList, searchQuery, statusFilter, buildingFilter, roomList, bldList);
    } catch (err) {
      console.error('Failed to fetch bookings:', err);
      toast.error(language === 'th' ? 'โหลดข้อมูลการจองไม่สำเร็จ' : 'Failed to load bookings');
    }
  };

  const applyFilters = (list: Booking[], query: string, status: string, bldId: string, roomList: Room[], bldList: Building[]) => {
    let result = [...list];
    
    if (query.trim()) {
      const q = query.toLowerCase();
      result = result.filter(b => 
        (b.guestName || '').toLowerCase().includes(q) || 
        (b.guestPhone || '').toLowerCase().includes(q) || 
        (b.bookingNo || '').toLowerCase().includes(q)
      );
    }
    
    if (status !== 'All') {
      result = result.filter(b => b.status === status);
    }
    
    if (bldId !== 'All') {
      const targetBld = bldList.find(b => b.id === bldId || b.code === bldId);
      
      result = result.filter(booking => {
        const room = roomList.find(r => r.id === booking.roomId || r.roomNumber === booking.roomNumber);
        const rBldId = room?.buildingId;
        
        if (targetBld && rBldId === targetBld.id) return true;
        if (rBldId === bldId) return true;
        
        const isBldB = (targetBld && (targetBld.code === 'B' || targetBld.id === 'bld-2')) || bldId === 'bld-2';
        const isBldA = (targetBld && (targetBld.code === 'A' || targetBld.id === 'bld-1')) || bldId === 'bld-1';
        
        const roomIsB = rBldId === 'bld-2' || (booking.roomNumber && booking.roomNumber.toUpperCase().startsWith('B'));
        
        if (isBldB) return roomIsB;
        if (isBldA) return !roomIsB;
        
        return rBldId === bldId;
      });
    }
    
    setFilteredBookings(result);
  };

  const handleSearchChange = (q: string) => {
    setSearchQuery(q);
    applyFilters(bookings, q, statusFilter, buildingFilter, rooms, buildings);
  };

  const handleStatusFilterChange = (st: string) => {
    setStatusFilter(st);
    applyFilters(bookings, searchQuery, st, buildingFilter, rooms, buildings);
  };

  const handleBuildingFilterChange = (bldId: string) => {
    setBuildingFilter(bldId);
    applyFilters(bookings, searchQuery, statusFilter, bldId, rooms, buildings);
  };

  const handleUpdateStatus = async (id: string, newStatus: string) => {
    try {
      await updateBookingStatus(id, newStatus);
      toast.success(language === 'th' ? `อัปเดตสถานะเป็น ${newStatus} แล้ว` : `Status updated to ${newStatus}`);
      fetchData();
    } catch (err) {
      toast.error(language === 'th' ? 'อัปเดตสถานะไม่สำเร็จ' : 'Failed to update status');
    }
  };

  return (
    <div className="space-y-8 pb-10">
      {/* HEADER */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-nike-hairline dark:border-nike-dark-card pb-4">
        <div>
          <h1 className="text-[28px] font-bold text-nike-ink dark:text-white flex items-center gap-2">
            <Building2 className="w-7 h-7 text-blue-600" /> {t('bkg.title')}
          </h1>
          <p className="text-[14px] text-nike-mute dark:text-nike-stone mt-0.5">
            {t('bkg.sub')}
          </p>
        </div>
      </div>

      {/* CONTROLS */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <div className="sm:col-span-2 relative">
          <Search className="w-4 h-4 text-nike-mute absolute left-3.5 top-3.5" />
          <input
            type="text"
            value={searchQuery}
            onChange={e => handleSearchChange(e.target.value)}
            placeholder={t('bkg.searchPlaceholder')}
            className="w-full pl-10 p-3 bg-nike-soft-cloud dark:bg-nike-dark-card border-0 text-nike-ink dark:text-white text-[14px] rounded-[24px] focus:outline-none"
          />
        </div>

        <div>
          <select
            value={statusFilter}
            onChange={e => handleStatusFilterChange(e.target.value)}
            className="w-full p-3 bg-nike-soft-cloud dark:bg-nike-dark-card border-0 text-nike-ink dark:text-white text-[14px] rounded-[24px] focus:outline-none cursor-pointer font-medium"
          >
            <option value="All">{t('roomMgmt.allStatuses')}</option>
            <option value="Pending">{language === 'th' ? 'รอตรวจสอบ' : 'Pending'}</option>
            <option value="Approved">{language === 'th' ? 'อนุมัติแล้ว' : 'Approved'}</option>
            <option value="Rejected">{language === 'th' ? 'ปฏิเสธ' : 'Rejected'}</option>
            <option value="Cancelled">{language === 'th' ? 'ยกเลิก' : 'Cancelled'}</option>
          </select>
        </div>

        <div>
          <select
            value={buildingFilter}
            onChange={e => handleBuildingFilterChange(e.target.value)}
            className="w-full p-3 bg-nike-soft-cloud dark:bg-nike-dark-card border-0 text-nike-ink dark:text-white text-[14px] rounded-[24px] focus:outline-none cursor-pointer font-medium"
          >
            <option value="All">{t('roomMgmt.allBuildings')}</option>
            {buildings.map(b => (
              <option key={b.id} value={b.id}>
                {language === 'en' ? (b.name.includes('อาคาร A') ? 'Building A' : b.name.includes('อาคาร B') ? 'Building B' : b.name) : b.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* TABLE */}
      <div className="bg-nike-canvas dark:bg-nike-dark-elevated border border-nike-hairline dark:border-nike-dark-card rounded-2xl overflow-x-auto">
        <table className="w-full text-left border-collapse text-[14px]">
          <thead>
            <tr className="border-b border-nike-hairline-soft dark:border-nike-dark-card text-nike-mute dark:text-nike-stone font-medium text-[13px]">
              <th className="p-4">{t('bkg.colNo')}</th>
              <th className="p-4">{t('bkg.colGuest')}</th>
              <th className="p-4">{t('bkg.colUnit')}</th>
              <th className="p-4">{t('bkg.colDates')}</th>
              <th className="p-4">{t('bkg.colRent')}</th>
              <th className="p-4">{t('bkg.colStatus')}</th>
              <th className="p-4 text-right">{t('roomMgmt.actions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-nike-hairline-soft dark:divide-nike-dark-card">
            {filteredBookings.length === 0 ? (
              <tr>
                <td colSpan={7} className="p-8 text-center text-nike-mute">
                  {t('bkg.empty')}
                </td>
              </tr>
            ) : (
              filteredBookings.map(booking => (
                <tr key={booking.id} className="hover:bg-nike-soft-cloud dark:hover:bg-nike-dark-card/50 transition-colors">
                  <td className="p-4 font-bold text-nike-ink dark:text-white">{booking.bookingNo}</td>
                  <td className="p-4">
                    <div className="font-medium text-nike-ink dark:text-white">{booking.guestName}</div>
                    <div className="text-[12px] text-nike-mute dark:text-nike-stone">{booking.guestPhone}</div>
                    <div className="text-[12px] text-nike-mute dark:text-nike-stone">{booking.guestEmail}</div>
                  </td>
                  <td className="p-4 font-bold text-blue-600 dark:text-blue-400">
                    {booking.roomNumber ? `${t('common.unit')} ${booking.roomNumber}` : '-'}
                  </td>
                  <td className="p-4 text-nike-mute dark:text-nike-stone">
                    <div className="whitespace-nowrap">{booking.checkIn}</div>
                    <div className="whitespace-nowrap mt-1">ถึง {booking.checkOut}</div>
                  </td>
                  <td className="p-4 font-medium text-nike-ink dark:text-white">
                    {formatCurrency(booking.totalPrice)}
                  </td>
                  <td className="p-4">
                    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-[12px] font-bold ${
                      booking.status === 'Approved' ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-400' :
                      booking.status === 'Pending' ? 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400' :
                      'bg-rose-100 text-rose-800 dark:bg-rose-900/30 dark:text-rose-400'
                    }`}>
                      {language === 'th' ? (
                        booking.status === 'Approved' ? 'อนุมัติแล้ว' :
                        booking.status === 'Pending' ? 'รอตรวจสอบ' :
                        booking.status === 'Rejected' ? 'ปฏิเสธ' :
                        'ยกเลิก'
                      ) : booking.status}
                    </span>
                  </td>
                  <td className="p-4 text-right space-x-2">
                    {booking.status === 'Pending' && (
                      <>
                        <button
                          onClick={() => handleUpdateStatus(booking.id, 'Approved')}
                          className="p-2 text-emerald-600 hover:bg-emerald-50 dark:hover:bg-emerald-900/30 rounded-lg transition-colors"
                          title={t('bkg.approve')}
                        >
                          <Check className="w-4 h-4 inline" />
                        </button>
                        <button
                          onClick={() => handleUpdateStatus(booking.id, 'Rejected')}
                          className="p-2 text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-lg transition-colors"
                          title={t('bkg.reject')}
                        >
                          <X className="w-4 h-4 inline" />
                        </button>
                        <button
                          onClick={() => handleUpdateStatus(booking.id, 'Cancelled')}
                          className="p-2 text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg transition-colors"
                          title={language === 'th' ? 'ยกเลิก' : 'Cancel'}
                        >
                          <Ban className="w-4 h-4 inline" />
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
