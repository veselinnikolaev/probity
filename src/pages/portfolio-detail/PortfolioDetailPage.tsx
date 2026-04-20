import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { usePortfolios } from '../../hooks/usePortfolios';
import { usePortfolioDetail } from '../../hooks/usePortfolioDetail';
import { StatCard } from '../../components/probity/StatCard';
import { SidebarNavItem } from '../../components/probity/SidebarNavItem';
import { ProbityButton } from '../../components/probity/ProbityButton';
import { PortfolioDetailTable } from '../../components/probity/PortfolioDetailTable';
import { AddPositionDrawer } from '../../components/probity/AddPositionDrawer';
import { DataBoundary } from '../../components/core/DataBoundary';
import { EmptyState } from '../../components/probity/EmptyState';
import { ErrorState } from '../../components/probity/ErrorState';
import { StatCardSkeleton, TableSkeleton } from '../../components/probity/SkeletonLoader';
import styles from './portfolio-detail.module.css';
import type { StatCardProps } from '../../components/probity/StatCard';

const PortfolioDetailPage: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const portfolioId = searchParams.get('portfolio') || '';

  const [activeNav, setActiveNav] = useState('portfolios');
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [editingPositionId, setEditingPositionId] = useState<string | null>(null);

  const portfoliosState = usePortfolios();
  const detailState = usePortfolioDetail(portfolioId, '30d');

  const currentPortfolio = portfoliosState.data?.find(p => p.id === portfolioId);

  useEffect(() => {
    if (!portfoliosState.isLoading && !currentPortfolio && portfolioId) {
      navigate('/portfolios', { replace: true });
    }
  }, [portfoliosState.isLoading, currentPortfolio, portfolioId, navigate]);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const handleEditPortfolio = () => {
    console.log('Edit portfolio:', portfolioId);
  };

  const handleEditPosition = (positionId: string) => {
    setEditingPositionId(positionId);
    setIsDrawerOpen(true);
  };

  const handleDeletePosition = (positionId: string) => {
    console.log('Delete position:', positionId);
  };

  const navItems = [
    { label: 'Dashboard', icon: '📊', id: 'dashboard' },
    { label: 'Portfolios', icon: '💼', id: 'portfolios' },
    { label: 'Risk Analysis', icon: '⚠️', id: 'risk' },
    { label: 'VaR Report', icon: '📈', id: 'var' },
    { label: 'Monte Carlo', icon: '🎲', id: 'monte' },
    { label: 'Correlation', icon: '🔗', id: 'correlation' },
    { label: 'Settings', icon: '⚙️', id: 'settings' },
  ];

  const buildStatCards = (summary: any, positions: any[]): StatCardProps[] => [
    {
      label: 'Total Value',
      value: summary.totalValue.toLocaleString('en-US', { maximumFractionDigits: 0 }),
      delta: `${summary.totalValueDelta >= 0 ? '+' : ''}${summary.totalValueDelta.toFixed(1)}%`,
      deltaType: summary.totalValueDelta >= 0 ? 'up' : 'down',
      sparklineData: summary.sparklines?.totalValue || [],
      prefix: '$',
      invertSentiment: false,
    },
    {
      label: 'Positions',
      value: (positions?.length || 0).toString(),
      delta: '',
      sparklineData: [],
      invertSentiment: false,
    },
    {
      label: 'Volatility',
      value: `${summary.volatility.toFixed(1)}%`,
      delta: `${summary.volatilityDelta >= 0 ? '+' : ''}${summary.volatilityDelta.toFixed(1)}%`,
      deltaType: summary.volatilityDelta <= 0 ? 'down' : 'up',
      sparklineData: summary.sparklines?.volatility || [],
      invertSentiment: true,
    },
    {
      label: 'Sharpe Ratio',
      value: summary.sharpeRatio.toFixed(2),
      delta: `${summary.sharpeRatioDelta >= 0 ? '+' : ''}${summary.sharpeRatioDelta.toFixed(2)}`,
      deltaType: summary.sharpeRatioDelta >= 0 ? 'up' : 'down',
      sparklineData: summary.sparklines?.sharpeRatio || [],
      invertSentiment: false,
    },
    {
      label: 'VaR (95%)',
      value: summary.var95.toLocaleString('en-US', { maximumFractionDigits: 0 }),
      delta: `${summary.var95Delta >= 0 ? '+' : ''}${summary.var95Delta.toFixed(1)}%`,
      deltaType: summary.var95Delta <= 0 ? 'down' : 'up',
      sparklineData: summary.sparklines?.var95 || [],
      prefix: '$',
      invertSentiment: true,
    },
  ];

  // ── Shared UI ─────────────────────────────────────────────────────────────
  const Sidebar = (
    <aside className={styles['sidebar']}>
      <div className={styles['sidebar-logo']}>Probity</div>
      <nav className={styles['sidebar-nav']}>
        {navItems.map(item => (
          <SidebarNavItem
            key={item.id}
            label={item.label}
            icon={item.icon}
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
        <h1 className={styles['portfolio-title']}>
          {currentPortfolio?.name || 'Loading...'}
        </h1>
        <button
          className={styles['edit-icon-btn']}
          onClick={handleEditPortfolio}
          title="Edit portfolio"
        >
          ✎
        </button>
      </div>
      <div className={styles['topbar-right']}>
        <ProbityButton
          variant="primary"
          size="md"
          onClick={() => {
            setEditingPositionId(null);
            setIsDrawerOpen(true);
          }}
        >
          + Add Position
        </ProbityButton>
        <span className={styles['notification-bell']}>🔔</span>
        <div className={styles['user-avatar']} onClick={handleLogout}>
          {user?.username?.charAt(0).toUpperCase()}
        </div>
      </div>
    </header>
  );

  // ── Main Render ───────────────────────────────────────────────────────────
  return (
    <div className={styles['portfolio-detail-layout']}>
      {Sidebar}

      <div className={styles['main-wrapper']}>
        {Topbar}

        <main className={styles['content-area']}>
          <div className={styles['content-zones']}>
            {/* STAT CARDS WITH BOUNDARY */}
            <section className={styles['summary-strip']}>
              <DataBoundary
                state={detailState}
                isEmpty={(data) => !data}
                loading={
                  <>
                    {[...Array(5)].map((_, i) => (
                      <StatCardSkeleton key={i} />
                    ))}
                  </>
                }
                empty={
                  <EmptyState
                    title="Portfolio Empty"
                    description="Add positions to see metrics"
                    size="sm"
                  />
                }
                error={(msg) => (
                  <ErrorState message={msg} onRetry={detailState.refetch} size="sm" />
                )}
              >
                {(detail) =>
                  buildStatCards(detail.summary, detail.positions).map((card, idx) => (
                    <StatCard key={idx} {...card} />
                  ))
                }
              </DataBoundary>
            </section>

            {/* POSITIONS TABLE WITH BOUNDARY */}
            <DataBoundary
              state={detailState}
              isEmpty={(data) => data.positions.length === 0}
              loading={<TableSkeleton rows={3} />}
              empty={
                <div className={styles['empty-state-container']}>
                  <EmptyState
                    icon="📊"
                    title="No Positions Yet"
                    description="Add your first position to start tracking your portfolio."
                    action={
                      <ProbityButton
                        variant="primary"
                        onClick={() => {
                          setEditingPositionId(null);
                          setIsDrawerOpen(true);
                        }}
                      >
                        Add Position
                      </ProbityButton>
                    }
                  />
                </div>
              }
              error={(msg) => (
                <ErrorState message={msg} onRetry={detailState.refetch} />
              )}
            >
              {(detail) => (
                <section className={styles['positions-section']}>
                  <div className={styles['section-header']}>Positions</div>
                  <PortfolioDetailTable
                    positions={detail.positions}
                    onEdit={handleEditPosition}
                    onDelete={handleDeletePosition}
                  />
                </section>
              )}
            </DataBoundary>
          </div>
        </main>
      </div>

      {/* Add Position Drawer */}
      <AddPositionDrawer
        open={isDrawerOpen}
        onClose={() => {
          setIsDrawerOpen(false);
          setEditingPositionId(null);
        }}
        portfolioId={portfolioId}
        editingPositionId={editingPositionId}
      />
    </div>
  );
};

export default PortfolioDetailPage;
