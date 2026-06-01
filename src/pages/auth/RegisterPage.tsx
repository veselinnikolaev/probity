import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Register } from '../../components/auth';
import { ROUTES } from '../../constants/routes';

const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  return <Register onSignInClick={() => navigate(ROUTES.LOGIN)} />;
};

export default RegisterPage;
