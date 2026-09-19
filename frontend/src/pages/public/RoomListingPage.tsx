import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useLanguage } from '../../context/LanguageContext';
import { Room, Building } from '../../types';
import { getRooms, getBuildings } from '../../services/api';
import { Building2, MapPin, BedDouble, Users, DoorOpen } from 'lucide-react';
import { formatCurrency } from '../../utils/formatters';

export const RoomListingPage: React.FC = () => {
  const { t, language } = useLanguage();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialBuildingId = searchParams.get('buildingId') || 'All';

  const [rooms, setRooms] = useState<Room[]>([]);
  const [buildings, setBuildings] = useState<Building[]>([]);
  const [filteredRooms, setFilteredRooms] = useState<Room[]>([]);
  const [buildingFilter, setBuildingFilter] = useState<string>(initialBuildingId);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [roomData, buildingData] = await Promise.all([getRooms(), getBuildings()]);
        // Only show available rooms for booking
        const availableRooms = roomData.filter(r => r.status === 'Available');
        setRooms(availableRooms);
        setBuildings(buildingData);
      } catch (error) {
        console.error('Failed to fetch rooms', error);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  useEffect(() => {
    let result = [...rooms];
    if (buildingFilter !== 'All') {
       result = result.filter(r => {
           if (r.buildingId === buildingFilter) return true;
           const targetBld = buildings.find(b => b.id === buildingFilter || b.code === buildingFilter);
           const isBldB = (targetBld && (targetBld.code === 'B' || targetBld.id === 'bld-2')) || buildingFilter === 'bld-2';
           const isBldA = (targetBld && (targetBld.code === 'A' || targetBld.id === 'bld-1')) || buildingFilter === 'bld-1';
           
           const roomIsB = r.buildingId === 'bld-2' || (r.roomNumber && r.roomNumber.toUpperCase().startsWith('B'));
           if (isBldB) return roomIsB;
           if (isBldA) return !roomIsB;
           return false;
       });
    }
    setFilteredRooms(result);
  }, [rooms, buildingFilter, buildings]);

  const handleBuildingFilter = (bldId: string) => {
    setBuildingFilter(bldId);
    setSearchParams(bldId === 'All' ? {} : { buildingId: bldId });
  };

  if (loading) {
    return <div className="min-h-[60vh] flex items-center justify-center">Loading...</div>;
  }

  return (
    <div className="max-w-[1200px] mx-auto px-4 sm:px-6 py-12 space-y-8">
      <div className="text-center space-y-4">
        <h1 className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
          {language === 'th' ? 'เลือกห้องพักที่ใช่สำหรับคุณ' : 'Find Your Perfect Room'}
        </h1>
        <p className="text-slate-500 dark:text-slate-400 max-w-2xl mx-auto">
          {language === 'th' ? 'ดูรายละเอียดและทำการจองห้องพักที่คุณสนใจได้ทันที' : 'Browse our available units and book instantly.'}
        </p>
      </div>

      <div className="flex flex-wrap items-center justify-center gap-4 py-4">
        <button
          onClick={() => handleBuildingFilter('All')}
          className={`px-6 py-2.5 rounded-full text-sm font-semibold transition-all ${
            buildingFilter === 'All' ? 'bg-blue-600 text-white shadow-md' : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700'
          }`}
        >
          {language === 'th' ? 'ทุกอาคาร' : 'All Buildings'}
        </button>
        {buildings.map(bld => (
          <button
            key={bld.id}
            onClick={() => handleBuildingFilter(bld.id)}
            className={`px-6 py-2.5 rounded-full text-sm font-semibold transition-all flex items-center gap-2 ${
              buildingFilter === bld.id ? 'bg-blue-600 text-white shadow-md' : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700'
            }`}
          >
            <Building2 className="w-4 h-4" />
            {bld.name}
          </button>
        ))}
      </div>

      {filteredRooms.length === 0 ? (
        <div className="text-center py-20 bg-white dark:bg-slate-900 rounded-3xl border border-slate-200 dark:border-slate-800">
           <DoorOpen className="w-12 h-12 text-slate-300 mx-auto mb-4" />
           <h3 className="text-lg font-semibold text-slate-900 dark:text-white">
             {language === 'th' ? 'ไม่มีห้องว่างในขณะนี้' : 'No rooms available'}
           </h3>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {filteredRooms.map(room => (
            <div key={room.id} className="bg-white dark:bg-slate-900 rounded-3xl overflow-hidden border border-slate-200 dark:border-slate-800 hover:shadow-xl transition-shadow flex flex-col">
              <div className="aspect-[4/3] bg-slate-100 dark:bg-slate-800 relative">
                {room.coverImage ? (
                  <img src={room.coverImage} alt={room.roomNumber} className="w-full h-full object-cover" />
                ) : (
                  <div className="w-full h-full flex items-center justify-center text-slate-400">
                    <DoorOpen className="w-12 h-12 opacity-50" />
                  </div>
                )}
                <div className="absolute top-4 right-4 bg-white/90 backdrop-blur text-slate-900 px-3 py-1 rounded-full text-xs font-bold shadow-sm">
                  {room.roomNumber}
                </div>
              </div>
              <div className="p-6 flex-1 flex flex-col">
                <div className="mb-4">
                  <h3 className="text-xl font-bold text-slate-900 dark:text-white">{room.roomType || 'Standard Room'}</h3>
                  <div className="flex items-center text-slate-500 dark:text-slate-400 text-sm mt-1 gap-2">
                    <MapPin className="w-3.5 h-3.5" /> 
                    {room.buildingName || (room.roomNumber?.startsWith('B') ? 'Building B' : 'Building A')} • Floor {room.floor}
                  </div>
                </div>
                
                <div className="grid grid-cols-2 gap-4 mb-6">
                  <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
                    <BedDouble className="w-4 h-4 text-blue-500" />
                    <span>{room.sizeSqm} sq.m.</span>
                  </div>
                  <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
                    <Users className="w-4 h-4 text-blue-500" />
                    <span>Up to {room.capacity} Pax</span>
                  </div>
                </div>

                <div className="mt-auto flex items-end justify-between border-t border-slate-100 dark:border-slate-800 pt-6">
                  <div>
                    <div className="text-sm text-slate-500 dark:text-slate-400">{language === 'th' ? 'ราคาเริ่มต้น' : 'Starting from'}</div>
                    <div className="text-2xl font-bold text-blue-600 dark:text-blue-400">
                      {formatCurrency(room.price)}
                      <span className="text-sm font-normal text-slate-500"> /mo</span>
                    </div>
                  </div>
                  <Link
                    to={`/booking/${room.id}`}
                    className="bg-slate-900 hover:bg-slate-800 dark:bg-white dark:text-slate-900 dark:hover:bg-slate-200 text-white px-6 py-2.5 rounded-full text-sm font-semibold transition-all active:scale-95"
                  >
                    {language === 'th' ? 'จองห้องนี้' : 'Book Now'}
                  </Link>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
