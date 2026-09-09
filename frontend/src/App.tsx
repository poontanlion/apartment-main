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
    </div>
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
