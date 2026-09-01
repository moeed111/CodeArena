import api from './axios';
export const getProblems   = params => api.get('/problems', { params });
export const getProblem    = slug   => api.get(`/problems/${slug}`);
export const getProblemStats = ()   => api.get('/problems/stats');
export const getAllTags    = ()     => api.get('/problems/tags/all');
