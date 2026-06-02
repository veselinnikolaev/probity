import axios, { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios';

const api: AxiosInstance = axios.create({
  baseURL: '/api',
  withCredentials: true,
});

// Request interceptor to add CSRF token
api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getCsrfToken();
  if (token) {
    config.headers['X-XSRF-TOKEN'] = token;
  }
  return config;
});

// Response interceptor to store CSRF token
api.interceptors.response.use(
  (response: AxiosResponse) => {
    const token = response.headers['x-xsrf-token'];
    if (token) {
      storeCsrfToken(token);
    }
    return response;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export function getCsrfToken(): string | null {
  return document.cookie
    .split('; ')
    .find((row) => row.startsWith('XSRF-TOKEN='))
    ?.split('=')[1] || null;
}

export function storeCsrfToken(token: string): void {
  document.cookie = `XSRF-TOKEN=${token}; path=/; SameSite=Strict`;
}

export default api;
