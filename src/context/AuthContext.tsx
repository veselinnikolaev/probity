import React, {
  createContext,
  useState,
  useEffect,
  useCallback,
  ReactNode,
} from 'react';
import { authService, User, LoginPayload, RegisterPayload } from '../api/authService';
import { getRefreshToken } from '../api/api';

// ---------------------------------------------------------------------------
// Context shape
// ---------------------------------------------------------------------------
export interface AuthContextValue {
  user: User | null;
  isAuthenticated: boolean;
  isInitializing: boolean; // true while silent refresh is in flight on mount
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------
interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);

  // -------------------------------------------------------------------------
  // Silent refresh on mount — restores session after page reload.
  // If no refresh token exists in sessionStorage we skip silently.
  // -------------------------------------------------------------------------
  useEffect(() => {
    const initAuth = async () => {
      const hasRefreshToken = !!getRefreshToken();
      if (!hasRefreshToken) {
        setIsInitializing(false);
        return;
      }
      try {
        const restoredUser = await authService.refresh();
        setUser(restoredUser);
      } catch {
        // Refresh token expired or invalid — user must log in again
        setUser(null);
      } finally {
        setIsInitializing(false);
      }
    };

    initAuth();
  }, []);

  // -------------------------------------------------------------------------
  // Listen for the global logout event dispatched by the Axios interceptor
  // when a refresh attempt fails mid-session.
  // -------------------------------------------------------------------------
  useEffect(() => {
    const handleForceLogout = () => setUser(null);
    window.addEventListener('auth:logout', handleForceLogout);
    return () => window.removeEventListener('auth:logout', handleForceLogout);
  }, []);

  // -------------------------------------------------------------------------
  // Actions
  // -------------------------------------------------------------------------
  const login = useCallback(async (payload: LoginPayload) => {
    const loggedInUser = await authService.login(payload);
    setUser(loggedInUser);
  }, []);

  const register = useCallback(async (payload: RegisterPayload) => {
    await authService.register(payload);
    // No token storage — caller redirects to login
  }, []);

  const logout = useCallback(async () => {
    await authService.logout();
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isInitializing,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};