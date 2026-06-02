import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { usePortfolios } from '@/hooks/portfolio';
import { PortfolioCard, EmptyPortfolioCard } from '@/components/probity/PortfolioCard';
import { CreatePortfolioModal } from '@/components/probity/CreatePortfolioModal';
import { ProbityButton } from '@/components/probity/ProbityButton';
import { DataBoundary } from '@/components/core/DataBoundary';
import { EmptyState } from '@/components/probity/EmptyState';
import { ErrorState } from '@/components/probity/ErrorState';
import styles from './portfolios.module.css';
import { portfolioService } from "@/services/portfolio";
import {useTopbarSlot} from "@/hooks/useTopbarSlot";
import { ROUTES } from '@/constants/routes';

const PortfoliosPage: React.FC = () => {
  const navigate = useNavigate();
  const portfoliosState = usePortfolios();

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isCreating, setIsCreating] = useState(false);

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

  useTopbarSlot(
        <div style={{ display: 'flex', alignItems: 'center', width: '100%' }}>
            <h1 className={styles['page-title']}>My Portfolios</h1>
            <div style={{ marginLeft: 'auto' }}>
                <ProbityButton variant="primary" onClick={() => setIsModalOpen(true)}>
                    New Portfolio
                </ProbityButton>
            </div>
        </div>, []
    );

    return (
        <>
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
                                    onClick={() => navigate(
                                        `${ROUTES.PORTFOLIO_DETAIL}?portfolio=${portfolio.id}`,
                                        { state: { name: portfolio.name } }
                                    )}
                                />
                            ))}
                            <EmptyPortfolioCard onClick={() => setIsModalOpen(true)} />
                        </div>
                    )}
                </DataBoundary>
            </main>

            <CreatePortfolioModal
                open={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                onSubmit={handleCreatePortfolio}
                loading={isCreating}
            />
        </>
    );
};

export default PortfoliosPage;
