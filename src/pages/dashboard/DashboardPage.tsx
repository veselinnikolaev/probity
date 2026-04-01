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
import { usePortfolio } from "../../hooks/usePortfolio";

// ── Alert icon component ─────────────────────────────────────────────────────
// FIX: Replace the raw `alert.icon` string (which was just text/emoji from the
// API) with a proper SVG icon determined by the alert's severity level.
type AlertSeverity = 'high' | 'moderate' | 'low' | string;

function AlertIcon({ severity }: { severity: AlertSeverity }) {
  const baseStyle: React.CSSProperties = {
    width: 18,
    height: 18,
    flexShrink: 0,
    marginTop: 2,
  };

  // "high"     → red filled warning triangle  (⚠ shape)
  // "moderate" → amber outlined info circle   (ℹ shape)
  // "low"      → green outlined check circle  (✓ shape)
  switch (severity) {
    case 'high':
      return (
          <svg viewBox="0 0 20 20" fill="none" style={baseStyle} aria-label="High severity">
            <path
                d="M8.485 3.215a1.75 1.75 0 0 1 3.03 0l6.28 10.875A1.75 1.75 0 0 1 16.28 17H3.72a1.75 1.75 0 0 1-1.515-2.91L8.485 3.215Z"
                fill="#EF4444" fillOpacity="0.15" stroke="#EF4444" strokeWidth="1.4"
            />
            <path d="M10 7.5v4" stroke="#EF4444" strokeWidth="1.6" strokeLinecap="round" />
            <circle cx="10" cy="13.5" r="0.85" fill="#EF4444" />
          </svg>
      );
    case 'moderate':
      return (
          <svg viewBox="0 0 20 20" fill="none" style={baseStyle} aria-label="Moderate severity">
            <circle cx="10" cy="10" r="7.5" stroke="#F59E0B" strokeWidth="1.4" fill="#F59E0B" fillOpacity="0.1" />
            <path d="M10 9v5" stroke="#F59E0B" strokeWidth="1.6" strokeLinecap="round" />
            <circle cx="10" cy="6.5" r="0.85" fill="#F59E0B" />
          </svg>
      );
    case 'low':
    default:
      return (
          <svg viewBox="0 0 20 20" fill="none" style={baseStyle} aria-label="Low severity">
            <circle cx="10" cy="10" r="7.5" stroke="#10B981" strokeWidth="1.4" fill="#10B981" fillOpacity="0.1" />
            <path d="M6.5 10.5l2.5 2.5 4.5-5" stroke="#10B981" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
      );
  }
}
// ─────────────────────────────────────────────────────────────────────────────

const DashboardPage: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [activeNav, setActiveNav] = useState('dashboard');
  const [selectedPortfolio, setSelectedPortfolio] = useState('');
  const [selectedDateRange, setSelectedDateRange] = useState('90d');
  const [sectorFilter, setSectorFilter] = useState<string | null>(null);

  const {
    portfolios, summary, positions,
    composition, volatility, alerts,
    loading, error,
  } = usePortfolio(selectedPortfolio, selectedDateRange);

  useEffect(() => {
    if (portfolios.length > 0 && !selectedPortfolio) {
      setSelectedPortfolio(portfolios[0].id);
    }
  }, [portfolios, selectedPortfolio]);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  // ── Derived data ─────────────────────────────────────────────────────────
  const portfolioOptions = portfolios.map(p => ({
    value: p.id,
    label: p.name,
    description: `$${p.totalValue.toLocaleString()}`,
  }));

  const dateRangeOptions = [
    { value: '7d',  label: 'Last 7 days'  },
    { value: '30d', label: 'Last 30 days' },
    { value: '90d', label: 'Last 90 days' },
    { value: '1y',  label: 'Last year'    },
  ];

  const navItems = [
    { label: 'Dashboard',   icon: '📊', id: 'dashboard'   },
    { label: 'Portfolios',  icon: '💼', id: 'portfolios'  },
    { label: 'Risk Analysis', icon: '⚠️', id: 'risk'      },
    { label: 'VaR Report',  icon: '📈', id: 'var'         },
    { label: 'Monte Carlo', icon: '🎲', id: 'monte'       },
    { label: 'Correlation', icon: '🔗', id: 'correlation' },
    { label: 'Settings',    icon: '⚙️', id: 'settings'   },
  ];

  const statCards = summary ? [
    {
      label: 'Total Value',
      value: summary.totalValue.toLocaleString('en-US', { maximumFractionDigits: 0 }),
      delta: `${summary.totalValueDelta >= 0 ? '+' : ''}${summary.totalValueDelta.toFixed(1)}%`,
      deltaType: summary.totalValueDelta >= 0 ? 'up' as const : 'down' as const,
      sparklineData: summary.sparklines?.totalValue || [],
      prefix: '$',
      invertSentiment: false,
    },
    {
      label: 'Daily Return',
      value: summary.dailyReturn.toLocaleString('en-US', { maximumFractionDigits: 2 }),
      delta: `${summary.dailyReturnDelta >= 0 ? '+' : ''}${summary.dailyReturnDelta.toFixed(2)}%`,
      deltaType: summary.dailyReturnDelta >= 0 ? 'up' as const : 'down' as const,
      sparklineData: summary.sparklines?.dailyReturn || [],
      prefix: '$',
      invertSentiment: false,
    },
    {
      label: 'Portfolio Volatility',
      value: `${summary.volatility.toFixed(1)}%`,
      delta: `${summary.volatilityDelta >= 0 ? '+' : ''}${summary.volatilityDelta.toFixed(1)}%`,
      deltaType: summary.volatilityDelta <= 0 ? 'down' as const : 'up' as const,
      sparklineData: summary.sparklines?.volatility || [],
      // FIX: Volatility going up is bad → invert sparkline colour
      invertSentiment: true,
    },
    {
      label: 'Sharpe Ratio',
      value: summary.sharpeRatio.toFixed(2),
      delta: `${summary.sharpeRatioDelta >= 0 ? '+' : ''}${summary.sharpeRatioDelta.toFixed(2)}`,
      deltaType: summary.sharpeRatioDelta >= 0 ? 'up' as const : 'down' as const,
      sparklineData: summary.sparklines?.sharpeRatio || [],
      invertSentiment: false,
    },
    {
      label: 'VaR (95%)',
      value: summary.var95.toLocaleString('en-US', { maximumFractionDigits: 0 }),
      delta: `${summary.var95Delta >= 0 ? '+' : ''}${summary.var95Delta.toFixed(1)}%`,
      deltaType: summary.var95Delta <= 0 ? 'down' as const : 'up' as const,
      sparklineData: summary.sparklines?.var95 || [],
      prefix: '$',
      // FIX: VaR going up is bad → invert sparkline colour
      invertSentiment: true,
    },
  ] : [];

  const filteredPositions: DataRow[] = (sectorFilter && positions
          ? positions.filter(p => p.sector === sectorFilter)
          : positions || []
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

  // ── Render guards ─────────────────────────────────────────────────────────
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

  const hasNoPortfolios = portfolios.length === 0;
  const isPortfolioEmpty = positions?.length === 0;

  // ── Shared UI ─────────────────────────────────────────────────────────────
  const Sidebar = (
      <aside className={styles['sidebar']}>
        <div className={styles['sidebar-logo']}>Probity</div>
        <nav className={styles['sidebar-nav']}>
          {navItems.map(item => (
              <SidebarNavItem
                  key={item.id} label={item.label} icon={item.icon}
                  active={activeNav === item.id}
                  onClick={() => { setActiveNav(item.id); navigate(`/${item.id}`); }}
              />
          ))}
        </nav>
      </aside>
  );

  const Topbar = (
      <header className={styles['topbar']}>
        <div className={styles['topbar-left']}>
          {!hasNoPortfolios && (
              <DropdownSelect
                  options={portfolioOptions}
                  value={selectedPortfolio}
                  onChange={(val) => { setSelectedPortfolio(val); setSectorFilter(null); }}
                  width="240px"
              />
          )}
        </div>
        <div className={styles['topbar-center']}>
          {!hasNoPortfolios && (
              <DropdownSelect
                  options={dateRangeOptions}
                  value={selectedDateRange}
                  onChange={setSelectedDateRange}
                  width="180px"
              />
          )}
        </div>
        <div className={styles['topbar-right']}>
          <span className={styles['notification-bell']}>🔔</span>
          <div
              className={styles['user-avatar']}
              onClick={handleLogout}
              title="Click to sign out"
          >
            {user?.username?.charAt(0).toUpperCase() || 'U'}
          </div>
        </div>
      </header>
  );

  // ── Main Render ───────────────────────────────────────────────────────────
  if (hasNoPortfolios) {
    return (
        <div className={styles['dashboard-layout']}>
          {Sidebar}
          <div className={styles['main-wrapper']}>
            {Topbar}
            <main className={styles['content-area']}>
              <div className={styles['empty-state-container']}>
                <div className={styles['empty-state-icon']}>💼</div>
                <h2>Welcome to Probity</h2>
                <p>You don't have any portfolios set up yet.</p>
                <button className={styles['primary-button']} onClick={() => navigate('/portfolios/new')}>
                  Create Your First Portfolio
                </button>
              </div>
            </main>
          </div>
        </div>
    );
  }

  return (
      <div className={styles['dashboard-layout']}>
        {Sidebar}
        <div className={styles['main-wrapper']}>
          {Topbar}
          <main className={styles['content-area']}>
            <div className={styles['content-zones']}>

              {/* Stat Cards Row */}
              {statCards.length > 0 && (
                  <section className={styles['summary-strip']}>
                    {statCards.map((card, idx) => (
                        <StatCard key={idx} {...card} />
                    ))}
                  </section>
              )}

              {isPortfolioEmpty ? (
                  <div className={styles['empty-portfolio-message']}>
                    <h3>This portfolio is empty</h3>
                    <p>Add some assets to start seeing your risk analysis, composition, and alerts.</p>
                    <button
                        className={styles['secondary-button']}
                        onClick={() => console.log('Open Add Asset Modal')}
                    >
                      + Add Assets
                    </button>
                  </div>
              ) : (
                  <>
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
                          <DonutChart data={composition} onSegmentClick={(label) => setSectorFilter(label)} />
                        </div>
                      </div>
                    </section>

                    <section className={styles['bottom-row']}>
                      <div className={styles['volatility-chart-container']}>
                        <div className={styles['volatility-header']}>
                          Volatility Over Time ({selectedDateRange})
                        </div>
                        {/* FIX: wrapper no longer uses flexbox centering which was
                        fighting the SVG's natural aspect-ratio expansion.
                        It now just provides the bounded box the chart fills. */}
                        <div className={styles['volatility-wrapper']}>
                          <LineChart
                              data={volatility}
                              dateRange={selectedDateRange}
                          />
                        </div>
                      </div>

                      <div className={styles['alert-feed-container']}>
                        <div className={styles['alert-feed-header']}>Risk Alerts</div>
                        <div className={styles['alert-feed']}>
                          {alerts.map(alert => (
                              <div key={alert.id} className={styles['alert-item']}>
                                {/* FIX: replaced alert.icon (raw string) with severity-driven SVG */}
                                <AlertIcon severity={alert.severity} />
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
                  </>
              )}
            </div>
          </main>
        </div>
      </div>
  );
};

export default DashboardPage;