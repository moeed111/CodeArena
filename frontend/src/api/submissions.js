import api from './axios';
export const submitCode       = data             => api.post('/submissions',              data);
export const runCode          = data             => api.post('/submissions/run',          data);
export const getMySubmissions = params           => api.get('/submissions',               { params });
export const getProblemSubmissions = (id, params)=> api.get(`/submissions/problem/${id}`, { params });
export const getSubmission    = id               => api.get(`/submissions/${id}`);
