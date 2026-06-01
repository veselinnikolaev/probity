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

// ---------------------------------------------------------------------------
// App
// ---------------------------------------------------------------------------
const App: React.FC = () => {
  return (
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            {/* Public routes */}
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />

            {/* Protected routes */}
            <Route element={<ProtectedRoute />}>
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/portfolios" element={<PortfoliosPage />} />
              <Route path="/portfolio-detail" element={<PortfolioDetailPage />} />
              <Route path="/risk" element={<RiskAnalysisPage />} />
              <Route path="/var" element={<VaRReportPage />} />
              <Route path="/monte" element={<MonteCarloPage />} />
              <Route path="/settings" element={<SettingsPage />} />
              {/* Add more protected routes here */}
            </Route>

            {/* Default redirect */}
            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
  );
};

export default App;
