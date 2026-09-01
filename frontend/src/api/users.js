import api from './axios';
export const getProfile       = ()       => api.get('/users/profile');
export const updateProfile    = data     => api.put('/users/profile', data);
export const getPublicProfile = username => api.get(`/users/${username}`);
