import axios, { AxiosInstance, InternalAxiosRequestConfig, AxiosError } from 'axios';

const BASE_URL = 'http://localhost:8080';

// In-memory access token — never touches localStorage
let accessToken: string | null = null;

export const setAccessToken = (token: string | null) => {
  accessToken = token;
};

export const getAccessToken = (): string | null => accessToken;

// ---------------------------------------------------------------------------
// Axios instance
// ---------------------------------------------------------------------------
const api: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true, // send cookies (if you later move refresh token to httpOnly cookie)
});

// ---------------------------------------------------------------------------
// Request interceptor — attach access token to every request
// ---------------------------------------------------------------------------
api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (accessToken) {
    config.headers['Authorization'] = `Bearer ${accessToken}`;
  }
  return config;
});

// ---------------------------------------------------------------------------
// Response interceptor — on 401, try refresh once, then retry original request
// ---------------------------------------------------------------------------
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value: unknown) => void;
  reject: (reason?: unknown) => void;
}> = [];

const processQueue = (error: AxiosError | null, token: string | null) => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error);
    else resolve(token);
  });
  failedQueue = [];
};

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    // Only attempt refresh on 401, and not on the refresh endpoint itself
    if (
      error.response?.status === 401 &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/auth/refresh')
    ) {
      if (isRefreshing) {
        // Queue concurrent requests while refresh is in progress
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers['Authorization'] = `Bearer ${token}`;
            return api(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const { data } = await api.post<{ accessToken: string; refreshToken: string }>(
          '/auth/refresh',
          // If you're sending refreshToken in body rather than cookie:
          { refreshToken: getRefreshToken() }
        );

        setAccessToken(data.accessToken);
        setRefreshToken(data.refreshToken);
        processQueue(null, data.accessToken);

        originalRequest.headers['Authorization'] = `Bearer ${data.accessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError as AxiosError, null);
        // Refresh failed — clear tokens and let the app redirect to login
        setAccessToken(null);
        clearRefreshToken();
        window.dispatchEvent(new Event('auth:logout'));
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

// ---------------------------------------------------------------------------
// Refresh token storage — in sessionStorage (survives page reload, not XSS-safe
// as localStorage but acceptable if you're not using httpOnly cookies).
// Swap to httpOnly cookie on the server side for maximum security.
// ---------------------------------------------------------------------------
const REFRESH_TOKEN_KEY = 'probity_rt';

export const setRefreshToken = (token: string) => {
  sessionStorage.setItem(REFRESH_TOKEN_KEY, token);
};

export const getRefreshToken = (): string | null => {
  return sessionStorage.getItem(REFRESH_TOKEN_KEY);
};

export const clearRefreshToken = () => {
  sessionStorage.removeItem(REFRESH_TOKEN_KEY);
};

export default api;