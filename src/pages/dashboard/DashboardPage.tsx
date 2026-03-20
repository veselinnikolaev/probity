import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';

const DashboardPage: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div style={{ padding: 40, color: '#fff', background: '#0a0a0f', minHeight: '100vh' }}>
      <h1>Welcome, {user?.fullName} 👋</h1>
      <p style={{ color: 'rgba(255,255,255,0.5)', marginTop: 8 }}>{user?.email}</p>
      <button
        onClick={handleLogout}
        style={{
          marginTop: 32,
          padding: '10px 20px',
          background: 'rgba(255,255,255,0.05)',
          border: '1px solid rgba(255,255,255,0.1)',
          borderRadius: 8,
          color: '#fff',
          cursor: 'pointer',
        }}
      >
        Sign Out
      </button>
    </div>
  );
};

export default DashboardPage;