import api, {
  setAccessToken,
  setRefreshToken,
  clearRefreshToken,
  getRefreshToken,
} from './api';

import {LoginPayload, User, LoginResponse, RegisterPayload} from "../types/auth.types";

// ---------------------------------------------------------------------------
// Auth service
// ---------------------------------------------------------------------------
export const authService = {
  /**
   * POST /auth/login
   * Stores tokens and returns the user object.
   */
  async login(payload: LoginPayload): Promise<User> {
    const { data } = await api.post<LoginResponse>('/auth/login', payload);
    setAccessToken(data.accessToken);
    setRefreshToken(data.refreshToken);
    return data.user;
  },

  /**
   * POST /auth/register
   * Does NOT store tokens — redirects to login on success.
   */
  async register(payload: RegisterPayload): Promise<void> {
    await api.post('/auth/register', payload);
  },

  /**
   * POST /auth/refresh
   * Called automatically by the Axios interceptor.
   * Also callable manually (e.g. silent refresh on app mount).
   */
  async refresh(): Promise<User> {
    const refreshToken = getRefreshToken();
    if (!refreshToken) throw new Error('No refresh token available');

    const { data } = await api.post<LoginResponse>('/auth/refresh', { refreshToken });
    setAccessToken(data.accessToken);
    setRefreshToken(data.refreshToken);
    return data.user;
  },

  /**
   * POST /auth/logout
   * Invalidates the refresh token server-side, clears local state.
   */
  async logout(): Promise<void> {
    try {
      await api.post('/auth/logout', { refreshToken: getRefreshToken() });
    } catch {
      // Even if the server call fails, clear local tokens
    } finally {
      setAccessToken(null);
      clearRefreshToken();
    }
  },
};