export type UserRole = 'admin' | 'customer' | 'resident';

export interface User {
  id: string | number;
  fullname: string;
  email: string;
  phone?: string;
  role: UserRole;
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
