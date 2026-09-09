
import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'sonner';

// Context Providers
import { ThemeProvider } from './context/ThemeContext';
import { LanguageProvider } from './context/LanguageContext';
import { AuthProvider } from './context/AuthContext';

// Layout Components
import { AdminSidebar } from './components/admin/AdminSidebar';

// Admin Pages
import { AdminDashboard } from './pages/admin/AdminDashboard';
import { BuildingManagement } from './pages/admin/BuildingManagement';
import { RoomManagement } from './pages/admin/RoomManagement';

// Admin Layout Wrapper
const AdminLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  return (
    <div className="min-h-screen bg-slate-50 dark:bg-zinc-950 text-slate-900 dark:text-white relative">
      <AdminSidebar />
      <main className="md:ml-64 p-4 sm:p-6 md:p-10 min-h-screen">
        {children}
      </main>
import { Navbar } from './components/common/Navbar';
import { Footer } from './components/common/Footer';

// Public Pages (M1 - Auth & User System)
import { Login } from './pages/public/Login';
import { Register } from './pages/public/Register';
import { ProfilePage } from './pages/public/ProfilePage';

/* 
  ================================================================================
  [Option A: Temporary Commented Out Module Routes]
  โมดูลอื่นๆ จะถูกนำมาเปิดใช้งานเมื่อเพื่อนร่วมทีมสร้างไฟล์เพจในโมดูลของตนเองเรียบร้อยแล้ว
  ================================================================================
  // import { AdminSidebar } from './components/admin/AdminSidebar';
  // import { Home } from './pages/public/Home';
  // const Rooms = React.lazy(() => import('./pages/public/Rooms').then(m => ({ default: m.Rooms })));
  // const RoomDetail = React.lazy(() => import('./pages/public/RoomDetail').then(m => ({ default: m.RoomDetail })));
  // const BookingPage = React.lazy(() => import('./pages/public/BookingPage').then(m => ({ default: m.BookingPage })));
  // const PaymentPage = React.lazy(() => import('./pages/public/PaymentPage').then(m => ({ default: m.PaymentPage })));
  // const CheckBookingPage = React.lazy(() => import('./pages/public/CheckBookingPage').then(m => ({ default: m.CheckBookingPage })));
  // const ResidentMaintenancePage = React.lazy(() => import('./pages/public/ResidentMaintenancePage').then(m => ({ default: m.ResidentMaintenancePage })));
  // const MyApartmentPage = React.lazy(() => import('./pages/public/MyApartmentPage').then(m => ({ default: m.MyApartmentPage })));

  // Admin Pages
  // const AdminDashboard = React.lazy(() => import('./pages/admin/AdminDashboard').then(m => ({ default: m.AdminDashboard })));
  // const BuildingManagement = React.lazy(() => import('./pages/admin/BuildingManagement').then(m => ({ default: m.BuildingManagement })));
  // const TenantManagement = React.lazy(() => import('./pages/admin/TenantManagement').then(m => ({ default: m.TenantManagement })));
  // const UtilityReceiptManagement = React.lazy(() => import('./pages/admin/UtilityReceiptManagement').then(m => ({ default: m.UtilityReceiptManagement })));
  // const MaintenanceManagement = React.lazy(() => import('./pages/admin/MaintenanceManagement').then(m => ({ default: m.MaintenanceManagement })));
  // const RoomManagement = React.lazy(() => import('./pages/admin/RoomManagement').then(m => ({ default: m.RoomManagement })));
  // const BookingManagement = React.lazy(() => import('./pages/admin/BookingManagement').then(m => ({ default: m.BookingManagement })));
  // const ActivityLogList = React.lazy(() => import('./pages/admin/ActivityLogList').then(m => ({ default: m.ActivityLogList })));
  // const NotificationCenter = React.lazy(() => import('./pages/admin/NotificationCenter').then(m => ({ default: m.NotificationCenter })));
*/

// Public Layout Wrapper
const PublicLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  return (
    <div className="min-h-screen flex flex-col bg-nike-canvas dark:bg-nike-dark-surface text-nike-ink dark:text-white">
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
      <React.Suspense
        fallback={
          <div className="min-h-screen flex items-center justify-center bg-nike-canvas dark:bg-nike-dark-surface">
            <div className="flex flex-col items-center gap-3">
              <div className="w-8 h-8 border-3 border-nike-ink/20 dark:border-white/20 border-t-nike-ink dark:border-t-white rounded-full animate-spin"></div>
              <span className="text-xs font-semibold text-nike-mute dark:text-nike-stone">กำลังโหลด...</span>
            </div>
          </div>
        }
      >
        <Routes>
          {/* M1 — AUTH & USER SYSTEM ROUTES */}
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="/login" element={<PublicLayout><Login /></PublicLayout>} />
          <Route path="/register" element={<PublicLayout><Register /></PublicLayout>} />
          <Route path="/signup" element={<PublicLayout><Register /></PublicLayout>} />
          <Route path="/sign-up" element={<PublicLayout><Register /></PublicLayout>} />
          <Route path="/profile" element={<PublicLayout><ProfilePage /></PublicLayout>} />

          {/* FALLBACK FOR UNMATCHED ROUTES */}
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </React.Suspense>
    </Router>
  );
};

export default function App() {
  return (
    <ThemeProvider>
      <LanguageProvider>
        <AuthProvider>
          <Router>
            <Toaster position="top-right" richColors />
            <Routes>
              {/* Redirect root to admin dashboard */}
              <Route path="/" element={<Navigate to="/admin/dashboard" replace />} />
              <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />

              {/* Admin Routes */}
              <Route
                path="/admin/dashboard"
                element={
                  <AdminLayout>
                    <AdminDashboard />
                  </AdminLayout>
                }
              />
              <Route
                path="/admin/buildings"
                element={
                  <AdminLayout>
                    <BuildingManagement />
                  </AdminLayout>
                }
              />
              <Route
                path="/admin/rooms"
                element={
                  <AdminLayout>
                    <RoomManagement />
                  </AdminLayout>
                }
              />

              {/* Fallback */}
              <Route path="*" element={<Navigate to="/admin/dashboard" replace />} />
            </Routes>
          </Router>
        </AuthProvider>
      </LanguageProvider>
    </ThemeProvider>
  );
}
