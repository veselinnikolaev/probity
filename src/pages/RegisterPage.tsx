import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Register } from '../components/auth';

const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  return <Register onSignInClick={() => navigate('/login')} />;
};

export default RegisterPage;