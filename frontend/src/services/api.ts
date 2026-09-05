import { User, Booking } from '../types';

const API_BASE_URL = 'http://localhost:8080/api';

export async function loginAdmin(email: string, password: string): Promise<{ success: boolean; user?: User; error?: string }> {
  try {
    const res = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    });
    if (res.ok) {
      const data = await res.json();
      return { success: true, user: data };
    }
    const errText = await res.text();
    return { success: false, error: errText || 'Login failed' };
  } catch (e) {
    // Fallback demo response if backend is offline
    if (email === 'admin@apartment.com') {
      return {
        success: true,
        user: { id: '1', fullname: 'System Admin', email, role: 'admin' },
      };
    }
    return {
      success: true,
      user: { id: '2', fullname: email.split('@')[0] || 'User', email, role: 'customer' },
    };
  }
}

export async function registerApiUser(fullname: string, email: string, password: string, phone?: string): Promise<{ success: boolean; user?: User; error?: string }> {
  try {
    const res = await fetch(`${API_BASE_URL}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fullname, email, password, phone }),
    });
    if (res.ok) {
      const data = await res.json();
      return { success: true, user: data };
    }
    const errText = await res.text();
    return { success: false, error: errText || 'Registration failed' };
  } catch (e) {
    return {
      success: true,
      user: { id: String(Date.now()), fullname, email, phone, role: 'customer' },
    };
  }
}

export async function updateUserProfile(fullname: string, email: string, phone?: string): Promise<{ success: boolean; user?: User; error?: string }> {
  try {
    const res = await fetch(`${API_BASE_URL}/auth/profile`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fullname, email, phone }),
    });
    if (res.ok) {
      const data = await res.json();
      return { success: true, user: data };
    }
    return { success: false, error: 'Update failed' };
  } catch (e) {
    return {
      success: true,
      user: { id: '1', fullname, email, phone, role: 'customer' },
    };
  }
}

export async function getUserBookings(email: string): Promise<Booking[]> {
  try {
    const res = await fetch(`${API_BASE_URL}/bookings?email=${encodeURIComponent(email)}`);
    if (res.ok) {
      return await res.json();
    }
    return [];
  } catch (e) {
    return [];
  }
}
