import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);

  const handleLogout = () => { logout(); navigate('/login'); };

  const navLink = (to, label) => (
    <Link
      to={to}
      className={`text-sm font-medium transition-colors ${
        location.pathname.startsWith(to)
          ? 'text-brand-400'
          : 'text-gray-400 hover:text-gray-100'
      }`}
    >
      {label}
    </Link>
  );

  return (
    <nav className="sticky top-0 z-50 bg-[#0f0f23]/95 backdrop-blur border-b border-gray-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-14">
          {/* Logo */}
          <Link to="/problems" className="flex items-center gap-2 text-white font-bold text-lg">
            <span className="text-2xl">⚡</span>
            <span className="bg-gradient-to-r from-brand-400 to-orange-400 bg-clip-text text-transparent">
              CodeArena
            </span>
          </Link>

          {/* Center Nav */}
          <div className="hidden md:flex items-center gap-6">
            {navLink('/problems', 'Problems')}
            {user && navLink('/submissions', 'Submissions')}
            {user && navLink('/dashboard', 'Dashboard')}
          </div>

          {/* Auth */}
          <div className="flex items-center gap-3">
            {user ? (
              <>
                <span className="hidden sm:block text-sm text-gray-400">
                  👋 {user.username}
                </span>
                <button onClick={handleLogout} className="btn-secondary text-sm py-1.5">
                  Logout
                </button>
              </>
            ) : (
              <>
                <Link to="/login"    className="text-sm text-gray-400 hover:text-white transition-colors">Login</Link>
                <Link to="/register" className="btn-primary text-sm py-1.5">Sign Up</Link>
              </>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}
