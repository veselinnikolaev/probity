import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { StatCard } from '../../components/probity/StatCard';
import { SidebarNavItem } from '../../components/probity/SidebarNavItem';
import { DropdownSelect } from '../../components/probity/DropdownSelect';
import { DataTable, DataRow } from '../../components/probity/DataTable';
import { DonutChart } from '../../components/probity/DonutChart';
import { LineChart } from '../../components/probity/LineChart';
import styles from './dashboard.module.css';
import {usePortfolio} from "../../hooks/usePortfolio";

const DashboardPage: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [activeNav, setActiveNav]           = useState('dashboard');
  const [selectedPortfolio, setSelectedPortfolio] = useState('');   // set after portfolios load
  const [selectedDateRange, setSelectedDateRange] = useState('90d');
  const [sectorFilter, setSectorFilter]     = useState<string | null>(null);

  const {
    portfolios, summary, positions,
    composition, volatility, alerts,
    loading, error,
  } = usePortfolio(selectedPortfolio, selectedDateRange);

  // Set default portfolio once list loads
  useEffect(() => {
    if (portfolios.length > 0 && !selectedPortfolio) {
      setSelectedPortfolio(portfolios[0].id);
    }
  }, [portfolios]);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  // ── Derived data ────────────────────────────────────────────────
  const portfolioOptions = portfolios.map(p => ({
    value: p.id,
    label: p.name,
    description: `$${p.totalValue.toLocaleString()}`,
  }));

  const dateRangeOptions = [
    { value: '7d',  label: 'Last 7 days' },
    { value: '30d', label: 'Last 30 days' },
    { value: '90d', label: 'Last 90 days' },
    { value: '1y',  label: 'Last year' },
  ];

  const navItems = [
    { label: 'Dashboard',    icon: '📊', id: 'dashboard' },
    { label: 'Portfolios',   icon: '💼', id: 'portfolios' },
    { label: 'Risk Analysis',icon: '⚠️', id: 'risk' },
    { label: 'VaR Report',   icon: '📈', id: 'var' },
    { label: 'Monte Carlo',  icon: '🎲', id: 'monte' },
    { label: 'Correlation',  icon: '🔗', id: 'correlation' },
    { label: 'Settings',     icon: '⚙️', id: 'settings' },
  ];

  const statCards = summary ? [
    {
      label: 'Total Value',
      value: summary.totalValue.toLocaleString('en-US', { maximumFractionDigits: 0 }),
      delta: `${summary.totalValueDelta >= 0 ? '+' : ''}${summary.totalValueDelta.toFixed(1)}%`,
      deltaType: summary.totalValueDelta >= 0 ? 'up' as const : 'down' as const,
      sparklineData: summary.sparklines.totalValue,
      prefix: '$',
    },
    {
      label: 'Daily Return',
      value: summary.dailyReturn.toLocaleString('en-US', { maximumFractionDigits: 2 }),
      delta: `${summary.dailyReturnDelta >= 0 ? '+' : ''}${summary.dailyReturnDelta.toFixed(2)}%`,
      deltaType: summary.dailyReturnDelta >= 0 ? 'up' as const : 'down' as const,
      sparklineData: summary.sparklines.dailyReturn,
      prefix: '$',
    },
    {
      label: 'Portfolio Volatility',
      value: `${summary.volatility.toFixed(1)}%`,
      delta: `${summary.volatilityDelta >= 0 ? '+' : ''}${summary.volatilityDelta.toFixed(1)}%`,
      // For volatility, DOWN is good
      deltaType: summary.volatilityDelta <= 0 ? 'down' as const : 'up' as const,
      sparklineData: summary.sparklines.volatility,
    },
    {
      label: 'Sharpe Ratio',
      value: summary.sharpeRatio.toFixed(2),
      delta: `${summary.sharpeRatioDelta >= 0 ? '+' : ''}${summary.sharpeRatioDelta.toFixed(2)}`,
      deltaType: summary.sharpeRatioDelta >= 0 ? 'up' as const : 'down' as const,
      sparklineData: summary.sparklines.sharpeRatio,
    },
    {
      label: 'VaR (95%)',
      value: summary.var95.toLocaleString('en-US', { maximumFractionDigits: 0 }),
      delta: `${summary.var95Delta >= 0 ? '+' : ''}${summary.var95Delta.toFixed(1)}%`,
      // For VaR, DOWN is good (less risk)
      deltaType: summary.var95Delta <= 0 ? 'down' as const : 'up' as const,
      sparklineData: summary.sparklines.var95,
      prefix: '$',
    },
  ] : [];

  // Donut drill-down: filter positions table by clicked sector
  const filteredPositions: DataRow[] = (sectorFilter
          ? positions.filter(p => p.sector === sectorFilter)
          : positions
  ).map(p => ({
    id: p.id,
    ticker: p.ticker,
    name: p.name,
    assetType: p.assetType,
    price: `$${p.price.toFixed(2)}`,
    change: `${p.change >= 0 ? '+' : ''}$${Math.abs(p.change).toFixed(2)}`,
    changePercent: `${p.changePercent >= 0 ? '+' : ''}${p.changePercent.toFixed(2)}%`,
    riskScore: p.riskScore,
    riskLevel: p.riskLevel,
    volume: p.volume,
    marketCap: p.marketCap,
  }));

  // ── Render guards ────────────────────────────────────────────────
  if (loading) return (
      <div className={styles['dashboard-layout']}>
        <div className={styles['loading-state']}>Loading portfolio data…</div>
      </div>
  );

  if (error) return (
      <div className={styles['dashboard-layout']}>
        <div className={styles['error-state']}>{error}</div>
      </div>
  );

  // ── JSX — identical structure to your original ────────────────
  return (
      <div className={styles['dashboard-layout']}>
        <aside className={styles['sidebar']}>
          <div className={styles['sidebar-logo']}>Probity</div>
          <nav className={styles['sidebar-nav']}>
            {navItems.map(item => (
                <SidebarNavItem
                    key={item.id} label={item.label} icon={item.icon}
                    active={activeNav === item.id}
                    onClick={() => setActiveNav(item.id)}
                />
            ))}
          </nav>
        </aside>

        <div className={styles['main-wrapper']}>
          <header className={styles['topbar']}>
            <div className={styles['topbar-left']}>
              <DropdownSelect
                  options={portfolioOptions}
                  value={selectedPortfolio}
                  onChange={(val) => {
                    setSelectedPortfolio(val);
                    setSectorFilter(null);   // clear drill-down on portfolio switch
                  }}
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

          <main className={styles['content-area']}>
            <div className={styles['content-zones']}>
              <section className={styles['summary-strip']}>
                {statCards.map((card, idx) => (
                    <StatCard key={idx} {...card} />
                ))}
              </section>

              <section className={styles['middle-row']}>
                <div className={styles['position-table-container']}>
                  <div className={styles['table-header']}>
                    Positions by Risk Contribution
                    {sectorFilter && (
                        <button
                            onClick={() => setSectorFilter(null)}
                            style={{ marginLeft: '12px', fontSize: '11px', opacity: 0.7 }}
                        >
                          ✕ {sectorFilter}
                        </button>
                    )}
                  </div>
                  <DataTable rows={filteredPositions} />
                </div>

                <div className={styles['chart-container']}>
                  <div className={styles['chart-header']}>Portfolio Composition</div>
                  <div className={styles['chart-wrapper']}>
                    <DonutChart
                        data={composition}
                        onSegmentClick={(label) => setSectorFilter(label)}
                    />
                  </div>
                </div>
              </section>

              <section className={styles['bottom-row']}>
                <div className={styles['volatility-chart-container']}>
                  <div className={styles['volatility-header']}>
                    Volatility Over Time ({selectedDateRange})
                  </div>
                  <div className={styles['volatility-wrapper']}>
                    <LineChart data={volatility} dateRange={selectedDateRange} />
                  </div>
                </div>

                <div className={styles['alert-feed-container']}>
                  <div className={styles['alert-feed-header']}>Risk Alerts</div>
                  <div className={styles['alert-feed']}>
                    {alerts.map(alert => (
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