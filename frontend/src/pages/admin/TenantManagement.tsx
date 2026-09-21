import React, { useEffect, useState } from 'react';
import { Search, Building2, Plus, Edit3, Trash2, X } from 'lucide-react';
import { Tenant, Building, Room } from '../../types';
import { getTenants, getBuildings, saveTenant, deleteTenant, getRooms } from '../../services/api';
import { useLanguage } from '../../context/LanguageContext';
import { toast } from 'sonner';

export const TenantManagement: React.FC = () => {
  const { t, language } = useLanguage();
  
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [buildings, setBuildings] = useState<Building[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  
  const [filteredTenants, setFilteredTenants] = useState<Tenant[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [buildingFilter, setBuildingFilter] = useState('All');
  
  const [modalOpen, setModalOpen] = useState(false);
  const [editingTenant, setEditingTenant] = useState<Partial<Tenant> | null>(null);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [tenantList, bldList, roomList] = await Promise.all([
        getTenants(), 
        getBuildings(),
        getRooms()
      ]);
      setTenants(tenantList);
      setBuildings(bldList);
      setRooms(roomList);
      applyFilters(tenantList, searchQuery, buildingFilter, roomList, bldList);
    } catch (err) {
      console.error('Failed to fetch tenants:', err);
      toast.error(language === 'th' ? 'โหลดข้อมูลผู้เช่าไม่สำเร็จ' : 'Failed to load tenants');
    }
  };

  const applyFilters = (list: Tenant[], query: string, bldId: string, roomList: Room[], bldList: Building[]) => {
    let result = [...list];
    
    if (query.trim()) {
      const q = query.toLowerCase();
      result = result.filter(t => 
        (t.name || '').toLowerCase().includes(q) || 
        (t.phone || '').toLowerCase().includes(q) || 
        (t.email || '').toLowerCase().includes(q)
      );
    }
    
    if (bldId !== 'All') {
      const targetBld = bldList.find(b => b.id === bldId || b.code === bldId);
      
      result = result.filter(tenant => {
        const room = roomList.find(r => r.id === tenant.roomId || r.roomNumber === tenant.roomNumber);
        const rBldId = room?.buildingId;
        
        if (targetBld && rBldId === targetBld.id) return true;
        if (rBldId === bldId) return true;
        
        const isBldB = (targetBld && (targetBld.code === 'B' || targetBld.id === 'bld-2')) || bldId === 'bld-2';
        const isBldA = (targetBld && (targetBld.code === 'A' || targetBld.id === 'bld-1')) || bldId === 'bld-1';
        
        const roomIsB = rBldId === 'bld-2' || (tenant.roomNumber && tenant.roomNumber.toUpperCase().startsWith('B'));
        
        if (isBldB) return roomIsB;
        if (isBldA) return !roomIsB;
        
        return rBldId === bldId;
      });
    }
    
    setFilteredTenants(result);
  };

  const handleSearchChange = (q: string) => {
    setSearchQuery(q);
    applyFilters(tenants, q, buildingFilter, rooms, buildings);
  };

  const handleBuildingFilterChange = (bldId: string) => {
    setBuildingFilter(bldId);
    applyFilters(tenants, searchQuery, bldId, rooms, buildings);
  };

  const handleDelete = async (id: string, name: string) => {
    if (window.confirm(language === 'th' ? `คุณต้องการลบข้อมูลของ ${name} ใช่หรือไม่?` : `Are you sure you want to delete ${name}?`)) {
      try {
        await deleteTenant(id);
        toast.success(language === 'th' ? `ลบข้อมูล ${name} แล้ว` : `Deleted ${name}`);
        fetchData();
      } catch (err) {
        toast.error(language === 'th' ? 'ลบข้อมูลไม่สำเร็จ' : 'Failed to delete');
      }
    }
  };

  const handleSaveModal = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingTenant) return;
    try {
      await saveTenant(editingTenant);
      toast.success(editingTenant.id ? (language === 'th' ? 'อัปเดตข้อมูลแล้ว' : 'Tenant updated') : (language === 'th' ? 'เพิ่มผู้เช่าแล้ว' : 'New tenant added'));
      setModalOpen(false);
      fetchData();
    } catch (err) {
      toast.error(language === 'th' ? 'บันทึกข้อมูลไม่สำเร็จ' : 'Failed to save');
    }
  };

  const openAddModal = () => {
    setEditingTenant({
      name: '',
      phone: '',
      email: '',
      idCardNo: '',
      roomId: '',
      roomNumber: '',
      status: 'Active'
    });
    setModalOpen(true);
  };

  const openEditModal = (tnt: Tenant) => {
    setEditingTenant(tnt);
    setModalOpen(true);
  };

  return (
    <div className="space-y-8 pb-10">
      {/* HEADER */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-nike-hairline dark:border-nike-dark-card pb-4">
        <div>
          <h1 className="text-[28px] font-bold text-nike-ink dark:text-white flex items-center gap-2">
            <Building2 className="w-7 h-7 text-blue-600" /> {t('tnt.title')}
          </h1>
          <p className="text-[14px] text-nike-mute dark:text-nike-stone mt-0.5">
            {t('tnt.sub')}
          </p>
        </div>

        <button
          onClick={openAddModal}
          className="bg-blue-600 text-white text-[14px] font-semibold px-6 py-2.5 rounded-xl hover:bg-blue-700 transition-colors flex items-center gap-2 shadow-xs"
        >
          <Plus className="w-4 h-4" /> {t('tnt.newTenant')}
        </button>
      </div>

      {/* CONTROLS */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <div className="sm:col-span-3 relative">
          <Search className="w-4 h-4 text-nike-mute absolute left-3.5 top-3.5" />
          <input
            type="text"
            value={searchQuery}
            onChange={e => handleSearchChange(e.target.value)}
            placeholder={language === 'th' ? 'ค้นหาชื่อ, เบอร์โทร, อีเมล...' : 'Search by name, phone, email...'}
            className="w-full pl-10 p-3 bg-nike-soft-cloud dark:bg-nike-dark-card border-0 text-nike-ink dark:text-white text-[14px] rounded-[24px] focus:outline-none"
          />
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
              <th className="p-4">{language === 'th' ? 'ชื่อ-นามสกุล' : 'Full Name'}</th>
              <th className="p-4">{language === 'th' ? 'เบอร์โทรศัพท์' : 'Phone'}</th>
              <th className="p-4">{language === 'th' ? 'อีเมล' : 'Email'}</th>
              <th className="p-4">{language === 'th' ? 'ห้องพัก' : 'Unit'}</th>
              <th className="p-4">{language === 'th' ? 'เลขบัตรปชช.' : 'ID Card'}</th>
              <th className="p-4 text-right">{t('roomMgmt.actions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-nike-hairline-soft dark:divide-nike-dark-card">
            {filteredTenants.length === 0 ? (
              <tr>
                <td colSpan={6} className="p-8 text-center text-nike-mute">
                  {language === 'th' ? 'ไม่พบข้อมูลผู้เช่า' : 'No tenants found'}
                </td>
              </tr>
            ) : (
              filteredTenants.map(tenant => (
                <tr key={tenant.id} className="hover:bg-nike-soft-cloud dark:hover:bg-nike-dark-card/50 transition-colors">
                  <td className="p-4 font-bold text-nike-ink dark:text-white">{tenant.name}</td>
                  <td className="p-4 text-nike-mute dark:text-nike-stone">{tenant.phone}</td>
                  <td className="p-4 text-nike-mute dark:text-nike-stone">{tenant.email}</td>
                  <td className="p-4 font-bold text-blue-600 dark:text-blue-400">
                    {tenant.roomNumber ? `${t('common.unit')} ${tenant.roomNumber}` : '-'}
                  </td>
                  <td className="p-4 text-nike-mute dark:text-nike-stone">{tenant.idCardNo || '-'}</td>
                  <td className="p-4 text-right space-x-2">
                    <button
                      onClick={() => openEditModal(tenant)}
                      className="p-2 text-nike-mute hover:text-nike-ink dark:hover:text-white transition-colors"
                      title={language === 'th' ? 'แก้ไข' : 'Edit'}
                    >
                      <Edit3 className="w-4 h-4 inline" />
                    </button>
                    <button
                      onClick={() => handleDelete(tenant.id, tenant.name)}
                      className="p-2 text-rose-500 hover:opacity-80 transition-opacity"
                      title={language === 'th' ? 'ลบ' : 'Delete'}
                    >
                      <Trash2 className="w-4 h-4 inline" />
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* MODAL */}
      {modalOpen && editingTenant && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-white dark:bg-slate-900 rounded-2xl w-full max-w-md overflow-hidden shadow-xl">
            <div className="flex justify-between items-center p-4 border-b dark:border-slate-800">
              <h2 className="text-lg font-bold dark:text-white">
                {editingTenant.id ? (language === 'th' ? 'แก้ไขข้อมูลผู้เช่า' : 'Edit Tenant') : (language === 'th' ? 'เพิ่มผู้เช่าใหม่' : 'Add New Tenant')}
              </h2>
              <button onClick={() => setModalOpen(false)} className="text-gray-500 hover:text-gray-700 dark:hover:text-gray-300">
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleSaveModal} className="p-4 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{language === 'th' ? 'ชื่อ-นามสกุล' : 'Full Name'}</label>
                <input
                  required
                  type="text"
                  value={editingTenant.name || ''}
                  onChange={e => setEditingTenant({ ...editingTenant, name: e.target.value })}
                  className="w-full p-2.5 bg-gray-50 dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-lg text-gray-900 dark:text-white"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{language === 'th' ? 'เบอร์โทรศัพท์' : 'Phone'}</label>
                  <input
                    required
                    type="text"
                    value={editingTenant.phone || ''}
                    onChange={e => setEditingTenant({ ...editingTenant, phone: e.target.value })}
                    className="w-full p-2.5 bg-gray-50 dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-lg text-gray-900 dark:text-white"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{language === 'th' ? 'อีเมล' : 'Email'}</label>
                  <input
                    required
                    type="email"
                    value={editingTenant.email || ''}
                    onChange={e => setEditingTenant({ ...editingTenant, email: e.target.value })}
                    className="w-full p-2.5 bg-gray-50 dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-lg text-gray-900 dark:text-white"
                  />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{language === 'th' ? 'เลขบัตรประชาชน / พาสปอร์ต' : 'ID Card / Passport'}</label>
                <input
                  type="text"
                  value={editingTenant.idCardNo || ''}
                  onChange={e => setEditingTenant({ ...editingTenant, idCardNo: e.target.value })}
                  className="w-full p-2.5 bg-gray-50 dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-lg text-gray-900 dark:text-white"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{language === 'th' ? 'รหัสห้อง' : 'Room ID'}</label>
                  <input
                    type="text"
                    value={editingTenant.roomId || ''}
                    onChange={e => setEditingTenant({ ...editingTenant, roomId: e.target.value })}
                    className="w-full p-2.5 bg-gray-50 dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-lg text-gray-900 dark:text-white"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{language === 'th' ? 'หมายเลขห้อง' : 'Room Number'}</label>
                  <input
                    type="text"
                    value={editingTenant.roomNumber || ''}
                    onChange={e => setEditingTenant({ ...editingTenant, roomNumber: e.target.value })}
                    className="w-full p-2.5 bg-gray-50 dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-lg text-gray-900 dark:text-white"
                  />
                </div>
              </div>
              <div className="pt-4 flex justify-end gap-3 border-t dark:border-slate-800">
                <button
                  type="button"
                  onClick={() => setModalOpen(false)}
                  className="px-4 py-2 font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 dark:text-gray-300 dark:bg-slate-800 dark:hover:bg-slate-700 rounded-lg transition-colors"
                >
                  {t('common.cancel')}
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors shadow-sm"
                >
                  {t('common.save')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
