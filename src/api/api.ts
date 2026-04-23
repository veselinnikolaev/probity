import axios, { AxiosInstance } from 'axios';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';

const api: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

function getCookie(name: string): string | null {
  return document.cookie
      .split('; ')
      .find(row => row.startsWith(name + '='))
      ?.split('=')[1] ?? null;
}

api.interceptors.request.use((config) => {
  const token = getCookie('XSRF-TOKEN');
  if (token) {
    config.headers.set('X-XSRF-TOKEN', token);
  }
  return config;
});

api.interceptors.response.use(
    response => response,
    async error => {
      if (
          error.response?.status === 403 &&
          !error.config._csrfRetry &&
          !error.config.url?.includes('/auth/csrf')
      ) {
        error.config._csrfRetry = true;
        await api.get('/auth/csrf');
        return api(error.config);
      }
      return Promise.reject(error);
    }
);

export default api;