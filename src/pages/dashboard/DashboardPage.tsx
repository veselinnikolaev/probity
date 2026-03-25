import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { StatCard } from '../../components/probity/StatCard';
import { SidebarNavItem } from '../../components/probity/SidebarNavItem';
import { DropdownSelect } from '../../components/probity/DropdownSelect';
import { DataTable, DataRow } from '../../components/probity/DataTable';
import { DonutChart } from '../../components/probity/DonutChart';
import { LineChart } from '../../components/probity/LineChart';
import styles from './dashboard.module.css';

interface RiskAlert {
  id: string;
  message: string;
  severity: 'high' | 'moderate' | 'low';
  timestamp: string;
  icon: string;
}

const DashboardPage: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [activeNav, setActiveNav] = useState('dashboard');
  const [selectedPortfolio, setSelectedPortfolio] = useState('portfolio-1');
  const [selectedDateRange, setSelectedDateRange] = useState('90d');

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  // Sample portfolio data
  const portfolioOptions = [
    { value: 'portfolio-1', label: 'Tech Growth Fund', description: '$2.45M' },
    { value: 'portfolio-2', label: 'Dividend Income', description: '$1.89M' },
    { value: 'portfolio-3', label: 'Global Balanced', description: '$3.12M' },
  ];

  const dateRangeOptions = [
    { value: '7d', label: 'Last 7 days' },
    { value: '30d', label: 'Last 30 days' },
    { value: '90d', label: 'Last 90 days' },
    { value: '1y', label: 'Last year' },
  ];

  const navItems = [
    { label: 'Dashboard', icon: '📊', id: 'dashboard' },
    { label: 'Portfolios', icon: '💼', id: 'portfolios' },
    { label: 'Risk Analysis', icon: '⚠️', id: 'risk' },
    { label: 'VaR Report', icon: '📈', id: 'var' },
    { label: 'Monte Carlo', icon: '🎲', id: 'monte' },
    { label: 'Correlation', icon: '🔗', id: 'correlation' },
    { label: 'Settings', icon: '⚙️', id: 'settings' },
  ];

  // Sample stat data
  const statCards = [
    {
      label: 'Total Value',
      value: '2,450,000',
      delta: '+2.4%',
      deltaType: 'up' as const,
      sparklineData: [100, 110, 105, 120, 115, 125, 130],
      prefix: '$',
    },
    {
      label: 'Daily Return',
      value: '18,543.50',
      delta: '+0.76%',
      deltaType: 'up' as const,
      sparklineData: [50, 55, 52, 62, 58, 68, 72],
      prefix: '$',
    },
    {
      label: 'Portfolio Volatility',
      value: '14.2%',
      delta: '-1.2%',
      deltaType: 'down' as const,
      sparklineData: [120, 118, 115, 112, 110, 108, 106],
    },
    {
      label: 'Sharpe Ratio',
      value: '1.87',
      delta: '+0.12',
      deltaType: 'up' as const,
      sparklineData: [60, 68, 65, 75, 80, 85, 90],
    },
    {
      label: 'VaR (95%)',
      value: '45,230',
      delta: '-2.1%',
      deltaType: 'down' as const,
      sparklineData: [100, 105, 108, 110, 105, 100, 95],
      prefix: '$',
    },
  ];

  // Sample position data
  const positionData: DataRow[] = [
    {
      id: '1',
      ticker: 'AAPL',
      name: 'Apple Inc.',
      assetType: 'STOCK',
      price: '$189.50',
      change: '+$12.35',
      changePercent: '+6.95%',
      riskScore: 35,
      riskLevel: 'LOW',
      volume: '52.3M',
      marketCap: '$2.98T',
    },
    {
      id: '2',
      ticker: 'MSFT',
      name: 'Microsoft Corporation',
      assetType: 'STOCK',
      price: '$425.80',
      change: '+$18.50',
      changePercent: '+4.53%',
      riskScore: 38,
      riskLevel: 'LOW',
      volume: '18.2M',
      marketCap: '$3.16T',
    },
    {
      id: '3',
      ticker: 'SPY',
      name: 'SPDR S&P 500 ETF',
      assetType: 'ETF',
      price: '$512.45',
      change: '+$8.20',
      changePercent: '+1.62%',
      riskScore: 45,
      riskLevel: 'MODERATE',
      volume: '68.5M',
      marketCap: '$450B',
    },
    {
      id: '4',
      ticker: 'BTC',
      name: 'Bitcoin',
      assetType: 'CRYPTO',
      price: '$68,450.00',
      change: '+$4,230',
      changePercent: '+6.59%',
      riskScore: 72,
      riskLevel: 'HIGH',
      volume: '25.3M',
      marketCap: '$1.34T',
    },
    {
      id: '5',
      ticker: 'QQQ',
      name: 'Invesco QQQ Trust',
      assetType: 'ETF',
      price: '$398.75',
      change: '+$12.15',
      changePercent: '+3.14%',
      riskScore: 52,
      riskLevel: 'MODERATE',
      volume: '42.1M',
      marketCap: '$185B',
    },
    {
      id: '6',
      ticker: 'GLD',
      name: 'SPDR Gold Shares',
      assetType: 'ETF',
      price: '$182.30',
      change: '-$2.10',
      changePercent: '-1.14%',
      riskScore: 28,
      riskLevel: 'LOW',
      volume: '8.2M',
      marketCap: '$78B',
    },
  ];

  // Sample donut chart data
  const portfolioComposition = [
    { label: 'Technology', value: 42, color: '#3B82F6' },
    { label: 'Financial', value: 18, color: '#60A5FA' },
    { label: 'Healthcare', value: 15, color: '#0EA5E9' },
    { label: 'Crypto', value: 12, color: '#06B6D4' },
    { label: 'Commodities', value: 13, color: '#14B8A6' },
  ];

  // Sample volatility data (90 days)
  const volatilityData = [12.3, 12.8, 12.1, 13.2, 12.9, 13.5, 14.1, 13.8, 14.2, 14.0, 13.7, 13.4, 13.9, 14.5, 14.2, 14.8, 15.1, 14.9, 14.6, 14.3, 14.1, 13.8, 13.5, 13.2, 13.9, 14.2, 14.5, 14.8, 15.0, 14.9];

  // Sample risk alerts
  const riskAlerts: RiskAlert[] = [
    {
      id: '1',
      message: 'Concentration in tech sector increased to 42%',
      severity: 'high',
      timestamp: '2 hours ago',
      icon: '⚠️',
    },
    {
      id: '2',
      message: 'Bitcoin exposure exceeded 12% threshold',
      severity: 'moderate',
      timestamp: '4 hours ago',
      icon: '📢',
    },
    {
      id: '3',
      message: 'Portfolio correlation with S&P 500 at 0.92',
      severity: 'moderate',
      timestamp: '6 hours ago',
      icon: '📊',
    },
    {
      id: '4',
      message: 'Daily volatility decreased by 1.2%',
      severity: 'low',
      timestamp: '8 hours ago',
      icon: '✓',
    },
    {
      id: '5',
      message: 'Sharpe ratio improved to 1.87',
      severity: 'low',
      timestamp: '12 hours ago',
      icon: '📈',
    },
  ];

  return (
    <div className={styles['dashboard-layout']}>
      {/* ── Sidebar ── */}
      <aside className={styles['sidebar']}>
        <div className={styles['sidebar-logo']}>Probity</div>
        <nav className={styles['sidebar-nav']}>
          {navItems.map((item) => (
            <SidebarNavItem
              key={item.id}
              label={item.label}
              icon={item.icon}
              active={activeNav === item.id}
              onClick={() => setActiveNav(item.id)}
            />
          ))}
        </nav>
      </aside>

      {/* ── Main Content ── */}
      <div className={styles['main-wrapper']}>
        {/* ── Top Bar ── */}
        <header className={styles['topbar']}>
          <div className={styles['topbar-left']}>
            <DropdownSelect
              options={portfolioOptions}
              value={selectedPortfolio}
              onChange={setSelectedPortfolio}
              width="240px"
            />
          </div>

          <div className={styles['topbar-center']}>
            <DropdownSelect
              options={dateRangeOptions}
              value={selectedDateRange}
              onChange={setSelectedDateRange}
              width="180px"
            />
          </div>

          <div className={styles['topbar-right']}>
            <span className={styles['notification-bell']}>🔔</span>
            <div
              className={styles['user-avatar']}
              onClick={handleLogout}
              title="Click to sign out"
            >
              {user?.username?.charAt(0).toUpperCase()}
            </div>
          </div>
        </header>

        {/* ── Content Area ── */}
        <main className={styles['content-area']}>
          <div className={styles['content-zones']}>
            {/* Zone 1: Summary Strip */}
            <section className={styles['summary-strip']}>
              {statCards.map((card, idx) => (
                <StatCard
                  key={idx}
                  label={card.label}
                  value={card.value}
                  delta={card.delta}
                  deltaType={card.deltaType}
                  sparklineData={card.sparklineData}
                  prefix={card.prefix}
                />
              ))}
            </section>

            {/* Zone 2: Middle Row */}
            <section className={styles['middle-row']}>
              {/* Position Table */}
              <div className={styles['position-table-container']}>
                <div className={styles['table-header']}>Positions by Risk Contribution</div>
                <DataTable rows={positionData} />
              </div>

              {/* Portfolio Composition Chart */}
              <div className={styles['chart-container']}>
                <div className={styles['chart-header']}>Portfolio Composition</div>
                <div className={styles['chart-wrapper']}>
                  <DonutChart data={portfolioComposition} />
                </div>
              </div>
            </section>

            {/* Zone 3: Bottom Row */}
            <section className={styles['bottom-row']}>
              {/* Volatility Over Time */}
              <div className={styles['volatility-chart-container']}>
                <div className={styles['volatility-header']}>Volatility Over Time (90 days)</div>
                <div className={styles['volatility-wrapper']}>
                  <LineChart data={volatilityData} />
                </div>
              </div>

              {/* Risk Alert Feed */}
              <div className={styles['alert-feed-container']}>
                <div className={styles['alert-feed-header']}>Risk Alerts</div>
                <div className={styles['alert-feed']}>
                  {riskAlerts.map((alert) => (
                    <div key={alert.id} className={styles['alert-item']}>
                      <span className={styles['alert-icon']}>{alert.icon}</span>
                      <div className={styles['alert-content']}>
                        <div className={styles['alert-message']}>{alert.message}</div>
                        <div className={styles['alert-meta']}>
                          <span className={styles['alert-time']}>{alert.timestamp}</span>
                          <span className={`${styles['severity-badge']} ${styles[`severity-${alert.severity}`]}`}>
                            {alert.severity}
                          </span>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </section>
          </div>
        </main>
      </div>
    </div>
  );
};

export default DashboardPage;
