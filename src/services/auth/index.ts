import api from '@/services/api';
import { LoginPayload, RegisterPayload, User } from '@/types/auth';

export const authService = {
  async login(payload: LoginPayload): Promise<User> {
    const { data } = await api.post<User>('/auth/login', payload);
    return data;
  },

  async register(payload: RegisterPayload): Promise<void> {
    await api.post('/auth/register', payload);
  },

  async verifyEmail(token: string): Promise<void> {
    await api.post(`/auth/verify?token=${token}`, null);
  },

  async resendVerification(email: string): Promise<void> {
    await api.post(`/auth/resend-verification?email=${encodeURIComponent(email)}`, null);
  },

  async checkEmailAvailability(email: string): Promise<{ available: boolean }> {
    const { data } = await api.get<{ available: boolean }>(
        `/auth/check-email?email=${encodeURIComponent(email)}`
    );
    return data;
  },

  async refresh(): Promise<User> {
    const { data } = await api.post<User>('/auth/refresh');
    return data;
  },

  async logout(): Promise<void> {
    try {
      await api.post('/auth/logout');
    } catch {
      // Server unreachable — cookies expire naturally
    }
  },

  // Called once on application mount by the AuthContext
  async ensureCsrf(): Promise<void> {
    await api.get('/auth/csrf');
  },
};