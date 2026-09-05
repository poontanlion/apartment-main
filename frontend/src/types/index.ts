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
  roomId?: string;
  userEmail?: string;
  status: 'Pending' | 'Approved' | 'Rejected' | 'Completed';
  createdAt?: string;
}
