import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';

/**
 * Wraps protected routes. While the silent refresh is in flight on mount,
 * renders nothing (or a spinner) to avoid a flash-redirect to /login.
 * Once auth is resolved, redirects unauthenticated users to /login,
 * preserving the attempted URL so they land back after signing in.
 */
const ProtectedRoute: React.FC = () => {
  const { isAuthenticated, isInitializing } = useAuth();
  const location = useLocation();

  if (isInitializing) {
    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          height: '100vh',
          background: '#0a0a0f',
        }}
      >
        <div
          style={{
            width: 32,
            height: 32,
            border: '2px solid rgba(255,255,255,0.1)',
            borderTop: '2px solid #6366f1',
            borderRadius: '50%',
            animation: 'spin 0.7s linear infinite',
          }}
        />
        <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <Outlet />;
};

export default ProtectedRoute;