import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Login } from '../../components/auth';

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  return <Login onRegisterClick={() => navigate('/register')} />;
};

export default LoginPage;