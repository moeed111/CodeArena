import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import Navbar           from './components/Navbar';
import LoginPage        from './pages/LoginPage';
import RegisterPage     from './pages/RegisterPage';
import ProblemListPage  from './pages/ProblemListPage';
import ProblemDetailPage from './pages/ProblemDetailPage';
import DashboardPage    from './pages/DashboardPage';
import SubmissionsPage  from './pages/SubmissionsPage';
import AdminPage        from './pages/AdminPage';
import LoadingSpinner   from './components/LoadingSpinner';

function PrivateRoute({ children }) {
  const { user, loading } = useAuth();
  if (loading) return <LoadingSpinner />;
  return user ? children : <Navigate to="/login" replace />;
}

function AdminRoute({ children }) {
  const { user, loading } = useAuth();
  if (loading) return <LoadingSpinner />;
  if (!user) return <Navigate to="/login" replace />;
  return user.role === 'ADMIN' ? children : <Navigate to="/problems" replace />;
}

function AppRoutes() {
  const { loading } = useAuth();
  if (loading) return <LoadingSpinner />;
  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      <main className="flex-1">
        <Routes>
          <Route path="/"              element={<Navigate to="/problems" replace />} />
          <Route path="/login"         element={<LoginPage />} />
          <Route path="/register"      element={<RegisterPage />} />
          <Route path="/problems"      element={<ProblemListPage />} />
          <Route path="/problems/:slug" element={<ProblemDetailPage />} />
          <Route path="/dashboard"     element={<PrivateRoute><DashboardPage /></PrivateRoute>} />
          <Route path="/submissions"   element={<PrivateRoute><SubmissionsPage /></PrivateRoute>} />
          <Route path="/admin"         element={<AdminRoute><AdminPage /></AdminRoute>} />
          <Route path="*"              element={<Navigate to="/problems" replace />} />
        </Routes>
      </main>
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  );
}
