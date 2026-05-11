import api, {storeCsrfToken} from './api';
import { LoginPayload, RegisterPayload, User } from '../types/auth.types';

export const authService = {
  async login(payload: LoginPayload): Promise<User> {
    // Backend sets access_token + refresh_token httpOnly cookies in Set-Cookie header.
    // Browser stores them automatically — nothing to do on our side.
    const { data } = await api.post<User>('/auth/login', payload);
    return data; // { username, role }
  },

  async register(payload: RegisterPayload): Promise<void> {
    await api.post('/auth/register', payload);
  },

  async refresh(): Promise<User> {
    // Browser sends refresh_token cookie automatically via withCredentials.
    // Backend rotates both cookies and returns user info.
    // Throws 401 if cookie is missing/expired — AuthContext catches this.
    const { data } = await api.post<User>('/auth/refresh');
    return data;
  },

  async logout(): Promise<void> {
    try {
      await api.post('/auth/logout');
      // Backend clears both cookies server-side via Set-Cookie: Max-Age=0
    } catch {
      // Server unreachable — cookies will expire naturally
    }
  },

  async refreshCsrfToken(): Promise<void> {
    const res = await api.get('/auth/csrf');
    const token = res.headers['x-xsrf-token'];
    if (token) storeCsrfToken(token);
  },
};