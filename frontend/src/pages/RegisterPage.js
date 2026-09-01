import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { register as registerApi } from '../api/auth';
import { useAuth } from '../context/AuthContext';

export default function RegisterPage() {
  const [form,    setForm]    = useState({ username: '', email: '', password: '', confirm: '' });
  const [errors,  setErrors]  = useState({});
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate  = useNavigate();

  const handleChange = e => {
    setForm(f => ({ ...f, [e.target.name]: e.target.value }));
    setErrors(prev => ({ ...prev, [e.target.name]: '' }));
  };

  const validate = () => {
    const errs = {};
    if (!form.username || form.username.length < 3)
      errs.username = 'Username must be at least 3 characters';
    if (!form.email || !/\S+@\S+\.\S+/.test(form.email))
      errs.email = 'Enter a valid email address';
    if (!form.password || form.password.length < 6)
      errs.password = 'Password must be at least 6 characters';
    if (form.password !== form.confirm)
      errs.confirm = 'Passwords do not match';
    return errs;
  };

  const handleSubmit = async e => {
    e.preventDefault();
    const errs = validate();
    if (Object.keys(errs).length) { setErrors(errs); return; }
    setLoading(true);
    try {
      const { data } = await registerApi({
        username: form.username, email: form.email, password: form.password
      });
      login(data.token, {
        id: data.userId, username: data.username,
        email: data.email, role: data.role
      });
      navigate('/problems');
    } catch (err) {
      const msg = err.response?.data?.message || 'Registration failed';
      const fieldErrors = err.response?.data?.fieldErrors || {};
      if (Object.keys(fieldErrors).length) setErrors(fieldErrors);
      else setErrors({ general: msg });
    } finally {
      setLoading(false);
    }
  };

  const Field = ({ name, label, type = 'text', placeholder }) => (
    <div>
      <label className="block text-sm font-medium text-gray-300 mb-1">{label}</label>
      <input
        name={name} type={type} value={form[name]}
        onChange={handleChange} placeholder={placeholder}
        className={`input ${errors[name] ? 'border-red-500' : ''}`}
        required
      />
      {errors[name] && <p className="mt-1 text-xs text-red-400">{errors[name]}</p>}
    </div>
  );

  return (
    <div className="min-h-[calc(100vh-56px)] flex items-center justify-center px-4 py-8">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="text-5xl mb-4">🚀</div>
          <h1 className="text-2xl font-bold text-white">Create account</h1>
          <p className="text-gray-400 mt-1">Start your coding journey today</p>
        </div>

        <div className="card p-8">
          {errors.general && (
            <div className="mb-4 p-3 rounded-lg bg-red-900/30 border border-red-700/50 text-red-400 text-sm">
              {errors.general}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <Field name="username"  label="Username"         placeholder="alice"           />
            <Field name="email"     label="Email"     type="email"    placeholder="alice@example.com" />
            <Field name="password"  label="Password"  type="password" placeholder="••••••••"          />
            <Field name="confirm"   label="Confirm Password" type="password" placeholder="••••••••"   />

            <button type="submit" disabled={loading} className="btn-primary w-full mt-2 py-2.5">
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                  Creating account…
                </span>
              ) : 'Create Account'}
            </button>
          </form>

          <p className="mt-4 text-center text-sm text-gray-400">
            Already have an account?{' '}
            <Link to="/login" className="text-brand-400 hover:text-brand-300 font-medium">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
