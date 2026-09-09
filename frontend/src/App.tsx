import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'sonner';

// Context Providers
import { ThemeProvider } from './context/ThemeContext';
import { LanguageProvider } from './context/LanguageContext';
import { AuthProvider, useAuth } from './context/AuthContext';

// Layout Components
import { AdminSidebar } from './components/admin/AdminSidebar';
import { Navbar } from './components/common/Navbar';
import { Footer } from './components/common/Footer';

// Public Pages
import { Login } from './pages/public/Login';
import { Register } from './pages/public/Register';
import { ProfilePage } from './pages/public/ProfilePage';
import { BookingPage } from './pages/public/BookingPage';
import { CheckBookingPage } from './pages/public/CheckBookingPage';
import { MyApartmentPage } from './pages/public/MyApartmentPage';
import { PaymentPage } from './pages/public/PaymentPage';
import { ResidentMaintenancePage } from './pages/public/ResidentMaintenancePage';

// Admin Pages
import { AdminDashboard } from './pages/admin/AdminDashboard';
import { BuildingManagement } from './pages/admin/BuildingManagement';
import { RoomManagement } from './pages/admin/RoomManagement';
import { UtilityReceiptManagement } from './pages/admin/UtilityReceiptManagement';
import { MaintenanceManagement } from './pages/admin/MaintenanceManagement';
import { ActivityLogList } from './pages/admin/ActivityLogList';
import { NotificationCenter } from './pages/admin/NotificationCenter';

// Protected Admin Route Guard
const ProtectedAdminRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  if (user?.role !== 'admin') {
    return <Navigate to="/profile" replace />;
  }
  return <>{children}</>;
};

// Root Redirect Guard
const RootRedirect: React.FC = () => {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  if (user?.role === 'admin') {
    return <Navigate to="/admin/dashboard" replace />;
  }
  return <Navigate to="/profile" replace />;
};

// Admin Layout Wrapper
const AdminLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  return (
    <div className="min-h-screen bg-slate-50 dark:bg-zinc-950 text-slate-900 dark:text-white relative">
      <AdminSidebar />
      <main className="md:ml-64 p-4 sm:p-6 md:p-10 min-h-screen">
        {children}
      </main>
    </div>
  );
};

// Public Layout Wrapper
const PublicLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  return (
    <div className="min-h-screen flex flex-col bg-slate-50 dark:bg-zinc-950 text-slate-900 dark:text-white">
      <Navbar />
      <main className="flex-1">
        {children}
      </main>
      <Footer />
    </div>
  );
};

export const AppContent: React.FC = () => {
  return (
    <Router>
      <Toaster
        position="top-right"
        theme="dark"
        duration={2500}
        visibleToasts={1}
        toastOptions={{
          duration: 2500,
          style: {
            background: '#09090b',
            color: '#ffffff',
            border: '1px solid rgba(255, 255, 255, 0.15)',
            borderRadius: '16px',
            padding: '14px 18px',
            fontSize: '13px',
            fontWeight: '600',
            boxShadow: '0 12px 32px -4px rgba(0, 0, 0, 0.4), 0 4px 12px -2px rgba(0, 0, 0, 0.3)',
            cursor: 'grab',
          },
        }}
      />
      <Routes>
        {/* PUBLIC & GENERAL ROUTES */}
        <Route path="/" element={<RootRedirect />} />
        <Route path="/login" element={<PublicLayout><Login /></PublicLayout>} />
        <Route path="/register" element={<PublicLayout><Register /></PublicLayout>} />
        <Route path="/signup" element={<PublicLayout><Register /></PublicLayout>} />
        <Route path="/sign-up" element={<PublicLayout><Register /></PublicLayout>} />
        <Route path="/profile" element={<PublicLayout><ProfilePage /></PublicLayout>} />

        {/* ROOMS & BOOKING ROUTES */}
        <Route path="/rooms" element={<PublicLayout><CheckBookingPage /></PublicLayout>} />
        <Route path="/rooms/:id" element={<PublicLayout><CheckBookingPage /></PublicLayout>} />
        <Route path="/booking/:roomId" element={<PublicLayout><BookingPage /></PublicLayout>} />
        <Route path="/payment/:bookingId" element={<PublicLayout><PaymentPage /></PublicLayout>} />
        <Route path="/check-booking" element={<PublicLayout><CheckBookingPage /></PublicLayout>} />
        <Route path="/track-booking" element={<PublicLayout><CheckBookingPage /></PublicLayout>} />
        <Route path="/my-bookings" element={<PublicLayout><CheckBookingPage /></PublicLayout>} />
        <Route path="/my-apartment" element={<PublicLayout><MyApartmentPage /></PublicLayout>} />
        <Route path="/my-maintenance" element={<PublicLayout><ResidentMaintenancePage /></PublicLayout>} />
        <Route path="/resident/maintenance" element={<PublicLayout><ResidentMaintenancePage /></PublicLayout>} />

        {/* ADMIN PROTECTED ROUTES */}
        <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />
        <Route path="/admin/dashboard" element={<ProtectedAdminRoute><AdminLayout><AdminDashboard /></AdminLayout></ProtectedAdminRoute>} />
        <Route path="/admin/buildings" element={<ProtectedAdminRoute><AdminLayout><BuildingManagement /></AdminLayout></ProtectedAdminRoute>} />
        <Route path="/admin/rooms" element={<ProtectedAdminRoute><AdminLayout><RoomManagement /></AdminLayout></ProtectedAdminRoute>} />
        <Route path="/admin/utility-bills" element={<ProtectedAdminRoute><AdminLayout><UtilityReceiptManagement /></AdminLayout></ProtectedAdminRoute>} />
        <Route path="/admin/maintenance" element={<ProtectedAdminRoute><AdminLayout><MaintenanceManagement /></AdminLayout></ProtectedAdminRoute>} />
        <Route path="/admin/activity-log" element={<ProtectedAdminRoute><AdminLayout><ActivityLogList /></AdminLayout></ProtectedAdminRoute>} />
        <Route path="/admin/notifications" element={<ProtectedAdminRoute><AdminLayout><NotificationCenter /></AdminLayout></ProtectedAdminRoute>} />

        {/* FALLBACK FOR UNMATCHED ROUTES */}
        <Route path="*" element={<RootRedirect />} />
      </Routes>
    </Router>
  );
};

export default function App() {
  return (
    <ThemeProvider>
      <LanguageProvider>
        <AuthProvider>
          <AppContent />
        </AuthProvider>
      </LanguageProvider>
    </ThemeProvider>
  );
}
