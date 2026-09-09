export type UserRole = 'admin' | 'customer' | 'resident';

export interface User {
  id: string | number;
  fullname: string;
  email: string;
  phone?: string;
  role: UserRole;
}

export type RoomType = 'Standard' | 'Deluxe' | 'Suite' | string;
export type RoomStatus = 'Available' | 'Occupied' | 'Maintenance' | 'Reserved' | string;

export interface Room {
  id: string;
  roomNumber: string;
  roomName?: string;
  buildingId?: string;
  buildingName?: string;
  floor?: number;
  type: RoomType;
  roomType?: string;
  price: number;
  capacity?: number;
  status: RoomStatus;
  amenities?: string[] | string;
  description?: string;
  images?: string[];
  coverImage?: string;
  sizeSqm?: number;
  bedType?: string;
  currentTenantId?: string;
  currentTenantName?: string;
  tenantName?: string;
  tenantPhone?: string;
  prevWaterMeter?: number;
  currWaterMeter?: number;
  prevElectricMeter?: number;
  currElectricMeter?: number;
}

export interface Building {
  id: string;
  name: string;
  code?: string;
  address?: string;
  floors: number;
  totalRooms?: number;
  description?: string;
  coverImage?: string;
  createdAt?: string;
}

export interface Tenant {
  id: string;
  name: string;
  phone: string;
  email: string;
  roomId?: string;
  roomNumber?: string;
  status?: string;
  idCardNo?: string;
}

export interface Lease {
  id: string;
  tenantId: string;
  tenantName?: string;
  roomId: string;
  roomNumber?: string;
  startDate: string;
  endDate: string;
  deposit?: number;
  monthlyRent?: number;
  rentAmount?: number;
  status?: string;
}

export type BillStatus = 'Pending' | 'Paid' | 'Overdue' | string;

export interface UtilityBill {
  id: string;
  invoiceNo?: string;
  leaseId?: string;
  roomId: string;
  roomNumber?: string;
  tenantName?: string;
  month: string;
  billingMonth?: string;
  waterAmount: number;
  electricAmount?: number;
  electricityAmount: number;
  rentAmount: number;
  totalAmount: number;
  prevWaterMeter?: number;
  currWaterMeter?: number;
  waterRate?: number;
  prevElectricMeter?: number;
  currElectricMeter?: number;
  electricRate?: number;
  commonFee?: number;
  dueDate: string;
  paymentDate?: string;
  status: BillStatus;
  slipUrl?: string;
  createdAt?: string;
}

export interface MaintenanceTask {
  id: string;
  taskNo?: string;
  roomId?: string;
  roomNumber?: string;
  title: string;
  description: string;
  category?: string;
  priority?: 'Low' | 'Medium' | 'High';
  status: 'Pending' | 'In Progress' | 'Completed';
  createdAt?: string;
  reportedDate?: string;
  preferredTime?: string;
  assignedWorker?: string;
  reporterName?: string;
  reporterPhone?: string;
  reporterEmail?: string;
  suppliesUsed?: string;
  occupancyType?: string;
  cost?: number;
  laborCost?: number;
  totalCost?: number;
}

export interface SupplyItem {
  id: string;
  name: string;
  category?: string;
  quantity: number;
  stockQuantity?: number;
  unit?: string;
  unitName?: string;
  unitCost?: number;
}

export interface MaintenanceLog {
  id: string;
  taskId?: string;
  taskNo?: string;
  roomId?: string;
  roomNumber?: string;
  category?: string;
  date?: string;
  description?: string;
  suppliesSummary?: string;
  performedBy?: string;
  totalCost?: number;
  details: string;
  createdAt: string;
}

export interface ScheduledReminder {
  id: string;
  title: string;
  category?: string;
  roomId?: string;
  roomNumber?: string;
  frequency?: string;
  dueDate: string;
  nextDueDate?: string;
  isActive?: boolean;
}

export interface Booking {
  id: string;
  bookingNo: string;
  roomId: string;
  roomNumber?: string;
  guestName: string;
  guestPhone: string;
  guestEmail: string;
  checkIn: string;
  checkOut: string;
  guestCount: number;
  totalPrice: number;
  specialRequests?: string;
  status: string;
  createdAt: string;
}

export interface AppNotification {
  id: string;
  userId?: string;
  title: string;
  message: string;
  type?: 'info' | 'success' | 'warning' | 'error';
  isRead: boolean;
  createdAt: string;
}

export interface ActivityLog {
  id: string;
  userId?: string;
  userName?: string;
  action: string;
  details: string;
  createdAt: string;
}
