import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Login } from '@/components/auth';
import { ROUTES } from '@/constants/routes';

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  return <Login onRegisterClick={() => navigate(ROUTES.REGISTER)} />;
};

export default LoginPage;
