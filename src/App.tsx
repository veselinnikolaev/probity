import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/core/ProtectedRoute';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import DashboardPage from './pages/dashboard/DashboardPage';
import PortfoliosPage from './pages/portfolios/PortfoliosPage';
import PortfolioDetailPage from './pages/portfolio-detail/PortfolioDetailPage';
import RiskAnalysisPage from './pages/risk-analysis/RiskAnalysisPage';
import VaRReportPage from './pages/var-report/VaRReportPage';
import MonteCarloPage from './pages/monte-carlo/MonteCarlo';
import SettingsPage from './pages/settings/SettingsPage';
import { ROUTES } from './constants/routes';

const App: React.FC = () => {
  return (
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            {/* Public routes */}
            <Route path={ROUTES.LOGIN} element={<LoginPage />} />
            <Route path={ROUTES.REGISTER} element={<RegisterPage />} />

            {/* Protected routes */}
            <Route element={<ProtectedRoute />}>
              <Route path={ROUTES.DASHBOARD} element={<DashboardPage />} />
              <Route path={ROUTES.PORTFOLIOS} element={<PortfoliosPage />} />
              <Route path={ROUTES.PORTFOLIO_DETAIL} element={<PortfolioDetailPage />} />
              <Route path={ROUTES.RISK} element={<RiskAnalysisPage />} />
              <Route path={ROUTES.VAR} element={<VaRReportPage />} />
              <Route path={ROUTES.MONTE} element={<MonteCarloPage />} />
              <Route path={ROUTES.SETTINGS} element={<SettingsPage />} />
            </Route>

            {/* Default redirect */}
            <Route path="*" element={<Navigate to={ROUTES.LOGIN} replace />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
  );
};

export default App;
