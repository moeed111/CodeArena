import axios from 'axios';

const API_BASE = process.env.REACT_APP_API_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL: `${API_BASE}/api`,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

// Attach JWT token to every request
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  },
  (error) => Promise.reject(error)
);

// Handle 401 globally
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// ============================================================
//  Auth
// ============================================================
export const authApi = {
  register:  (data)  => api.post('/auth/register', data),
  login:     (data)  => api.post('/auth/login', data),
  logout:    ()      => api.post('/auth/logout'),
  getMe:     ()      => api.get('/auth/me'),
};

// ============================================================
//  Problems
// ============================================================
export const problemsApi = {
  getAll: (params) => api.get('/problems', { params }),
  getBySlug: (slug) => api.get(`/problems/${slug}`),
  getStats:  ()    => api.get('/problems/stats'),
  getTags:   ()    => api.get('/problems/tags/all'),
  create:   (data) => api.post('/problems', data),
};

// ============================================================
//  Submissions
// ============================================================
export const submissionsApi = {
  submit:           (data)           => api.post('/submissions', data),
  runCode:          (data)           => api.post('/submissions/run', data),
  getMyHistory:     (params)         => api.get('/submissions', { params }),
  getForProblem:    (id, params)     => api.get(`/submissions/problem/${id}`, { params }),
  getDetail:        (id)             => api.get(`/submissions/${id}`),
};

// ============================================================
//  Users
// ============================================================
export const usersApi = {
  getMyProfile:    ()       => api.get('/users/profile'),
  updateProfile:   (data)   => api.put('/users/profile', data),
  getPublicProfile:(name)   => api.get(`/users/${name}`),
};

// ============================================================
//  Admin
// ============================================================
export const adminApi = {
  getStats:        ()              => api.get('/admin/stats'),
  getUsers:        ()              => api.get('/admin/users'),
  updateUserRole:  (id, role)      => api.put(`/admin/users/${id}/role`, { role }),
  deleteUser:      (id)            => api.delete(`/admin/users/${id}`),
  createProblem:   (data)          => api.post('/problems', data),
  updateProblem:   (id, data)      => api.put(`/admin/problems/${id}`, data),
  deleteProblem:   (id)            => api.delete(`/admin/problems/${id}`),
  toggleProblem:   (id)            => api.patch(`/admin/problems/${id}/toggle`),
};

export default api;

