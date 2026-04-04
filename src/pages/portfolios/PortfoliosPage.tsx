import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { usePortfolio } from '../../hooks/usePortfolio';
import { PortfolioCard, EmptyPortfolioCard } from '../../components/probity/PortfolioCard';
import { CreatePortfolioModal } from '../../components/probity/CreatePortfolioModal';
import { ProbityButton } from '../../components/probity/ProbityButton';
import { SidebarNavItem } from '../../components/probity/SidebarNavItem';
import styles from './portfolios.module.css';
import { portfolioService } from "../../api/portfolioService";

const PortfoliosPage: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { portfolios, loading, refetch } = usePortfolio('', '30d');

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
      refetch();
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

  if (loading) {
    return (
      <div className={styles['portfolios-layout']}>
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
        <div className={styles['main-wrapper']}>
          <header className={styles['topbar']}>
            <span className={styles['notification-bell']}>🔔</span>
            <div className={styles['user-avatar']} onClick={handleLogout}>
              {user?.username?.charAt(0).toUpperCase()}
            </div>
          </header>
          <main className={styles['content-area']}>
            <div className={styles['loading-state']}>Loading portfolios…</div>
          </main>
        </div>
      </div>
    );
  }

  return (
    <div className={styles['portfolios-layout']}>
      <aside className={styles['sidebar']}>
        <div className={styles['sidebar-logo']}>Probity</div>
        <nav className={styles['sidebar-nav']}>
          {navItems.map(item => (
              <SidebarNavItem
                  key={item.id} label={item.label} icon={item.icon}
                  active={activeNav === item.id}
                  onClick={() => { setActiveNav(item.id);
                    navigate(`/${item.id}`); }}
              />
          ))}
        </nav>
      </aside>

      <div className={styles['main-wrapper']}>
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

        <main className={styles['content-area']}>
          <div className={styles['portfolios-grid']}>
            {portfolios?.map(portfolio => (
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
