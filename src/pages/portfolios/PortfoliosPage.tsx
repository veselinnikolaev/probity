import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { usePortfolios } from '../../hooks/usePortfolios';
import { PortfolioCard, EmptyPortfolioCard } from '../../components/probity/PortfolioCard';
import { CreatePortfolioModal } from '../../components/probity/CreatePortfolioModal';
import { ProbityButton } from '../../components/probity/ProbityButton';
import { SidebarNavItem } from '../../components/probity/SidebarNavItem';
import { DataBoundary } from '../../components/core/DataBoundary';
import { EmptyState } from '../../components/probity/EmptyState';
import { ErrorState } from '../../components/probity/ErrorState';
import styles from './portfolios.module.css';
import { portfolioService } from "../../api/portfolioService";

const PortfoliosPage: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const portfoliosState = usePortfolios();

  const [activeNav, setActiveNav] = useState('portfolios');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isCreating, setIsCreating] = useState(false);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const handleCreatePortfolio = async (data: { name: string; description?: string }) => {
    setIsCreating(true);
    try {
      await portfolioService.createPortfolio(data);
      setIsModalOpen(false);
      portfoliosState.refetch();
    } catch (error) {
      throw new Error('Failed to create portfolio');
    } finally {
      setIsCreating(false);
    }
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
        <h1 className={styles['page-title']}>My Portfolios</h1>
      </div>
      <div className={styles['topbar-right']}>
        <ProbityButton
          variant="primary"
          onClick={() => setIsModalOpen(true)}
        >
          New Portfolio
        </ProbityButton>
        <span className={styles['notification-bell']}>🔔</span>
        <div className={styles['user-avatar']} onClick={handleLogout}>
          {user?.username?.charAt(0).toUpperCase()}
        </div>
      </div>
    </header>
  );

  return (
    <div className={styles['portfolios-layout']}>
      {Sidebar}

      <div className={styles['main-wrapper']}>
        {Topbar}

        <main className={styles['content-area']}>
          <DataBoundary
            state={portfoliosState}
            isEmpty={(data) => data.length === 0}
            loading={
              <div className={styles['loading-state']}>
                Loading portfolios…
              </div>
            }
            empty={
              <div className={styles['portfolios-grid']}>
                <EmptyState
                  icon="💼"
                  title="No Portfolios Yet"
                  description="Create your first portfolio to get started"
                  action={
                    <ProbityButton
                      variant="primary"
                      onClick={() => setIsModalOpen(true)}
                    >
                      Create Portfolio
                    </ProbityButton>
                  }
                />
              </div>
            }
            error={(msg) => (
              <div className={styles['portfolios-grid']}>
                <ErrorState
                  message={msg}
                  onRetry={portfoliosState.refetch}
                />
              </div>
            )}
          >
            {(portfolios) => (
              <div className={styles['portfolios-grid']}>
                {portfolios.map(portfolio => (
                  <PortfolioCard
                    key={portfolio.id}
                    id={portfolio.id}
                    name={portfolio.name}
                    createdAt={portfolio.createdAt}
                    positionsCount={portfolio.positionsCount || 0}
                    totalValue={portfolio.totalValue || 0}
                    volatility={portfolio.volatility}
                    sharpeRatio={portfolio.sharpeRatio}
                    sparklineData={portfolio.sparklineData || []}
                    onClick={() => navigate(`/dashboard?portfolio=${portfolio.id}`)}
                  />
                ))}
                <EmptyPortfolioCard onClick={() => setIsModalOpen(true)} />
              </div>
            )}
          </DataBoundary>
        </main>
      </div>

      <CreatePortfolioModal
        open={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSubmit={handleCreatePortfolio}
        loading={isCreating}
      />
    </div>
  );
};

export default PortfoliosPage;
