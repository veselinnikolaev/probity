import React, { useState, useMemo } from 'react';
import { useSearchParams, useLocation } from 'react-router-dom';
import { usePortfolioDetail } from '../../hooks/usePortfolioDetail';
import { StatCard } from '../../components/probity/StatCard';
import { ProbityButton } from '../../components/probity/ProbityButton';
import { PortfolioDetailTable } from '../../components/probity/PortfolioDetailTable';
import { AddPositionDrawer } from '../../components/probity/AddPositionDrawer';
import { DataBoundary } from '../../components/core/DataBoundary';
import { EmptyState } from '../../components/probity/EmptyState';
import { ErrorState } from '../../components/probity/ErrorState';
import { StatCardSkeleton, TableSkeleton } from '../../components/probity/SkeletonLoader';
import { portfolioService } from '../../api/portfolioService';
import styles from './portfolio-detail.module.css';
import type { StatCardProps } from '../../components/probity/StatCard';
import {useTopbarSlot} from "../../hooks/useTopbarSlot";

const PortfolioDetailPage: React.FC = () => {
  const { state: routerState } = useLocation();
  const [searchParams] = useSearchParams();
  const portfolioId = searchParams.get('portfolio') || '';

  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [editingPositionId, setEditingPositionId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const detailState = usePortfolioDetail(portfolioId, '30d');

  const editingPosition = useMemo(() => {
    if (!editingPositionId || !detailState.data) return null;
    const pos = detailState.data.positions.find(p => p.id === editingPositionId);
    if (!pos) return null;
    return {
      ticker: pos.ticker,
      name: pos.name,
      type: pos.assetType as 'STOCK' | 'ETF' | 'CRYPTO' | 'BOND',
      currentPrice: pos.currentPrice ?? pos.price,
      quantity: pos.quantity ?? 0,
    };
  }, [editingPositionId, detailState.data]);

  const handleEditPosition = (positionId: string) => {
    setEditingPositionId(positionId);
    setIsDrawerOpen(true);
  };

  const handleDeletePosition = async (positionId: string) => {
    if (!window.confirm('Remove this position from your portfolio?')) return;
    setDeletingId(positionId);
    try {
      await portfolioService.deletePosition(portfolioId, positionId);
      detailState.refetch();
    } catch (error) {
      console.error('Failed to delete position:', error);
    } finally {
      setDeletingId(null);
    }
  };

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

    useTopbarSlot(
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', width: '100%' }}>
            <h1 className={styles['portfolio-title']}>
                {routerState?.name || 'Portfolio'}
            </h1>
            <div style={{ marginLeft: 'auto' }}>
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
            </div>
        </div>, []
    );

    return (
        <>
            <main className={styles['content-area']}>
                <div className={styles['content-zones']}>
                    <section className={styles['summary-strip']}>
                        <DataBoundary
                            state={detailState}
                            isEmpty={(data) => !data}
                            loading={<>{[...Array(5)].map((_, i) => <StatCardSkeleton key={i}/>)}</>}
                            empty={<EmptyState
                                title="Portfolio Empty"
                                description="Add positions to see metrics"
                                size="sm"/>}
                            error={(msg) => (
                                <ErrorState message={msg} onRetry={detailState.refetch} size="sm"/>
                            )}
                        >
                            {(detail) => buildStatCards(detail.summary, detail.positions).map((card, idx) => (
                                <StatCard key={idx} {...card} />
                            ))}
                        </DataBoundary>
                    </section>

                    <DataBoundary
                        state={detailState}
                        isEmpty={(data) => data.positions.length === 0}
                        loading={<TableSkeleton rows={3}/>}
                        empty={<div className={styles['empty-state-container']}>
                            <EmptyState
                                icon="📊"
                                title="No Positions Yet"
                                description="Add your first position to start tracking your portfolio."
                                action={<ProbityButton
                                    variant="primary"
                                    onClick={() => {
                                        setEditingPositionId(null);
                                        setIsDrawerOpen(true);
                                    }}
                                >
                                    Add Position
                                </ProbityButton>}/>
                        </div>}
                        error={(msg) => (
                            <ErrorState message={msg} onRetry={detailState.refetch}/>
                        )}
                    >
                        {(detail) => (
                            <section className={styles['positions-section']}>
                                <div className={styles['section-header']}>Positions</div>
                                <PortfolioDetailTable
                                    positions={detail.positions}
                                    deletingId={deletingId}
                                    onEdit={handleEditPosition}
                                    onDelete={handleDeletePosition}/>
                            </section>
                        )}
                    </DataBoundary>
                </div>
            </main>
            <AddPositionDrawer
                open={isDrawerOpen}
                onClose={() => {
                    setIsDrawerOpen(false);
                    setEditingPositionId(null);
                }}
                onSuccess={detailState.refetch}
                portfolioId={portfolioId}
                editingPositionId={editingPositionId}
                editingPosition={editingPosition}/></>
  );
};

export default PortfolioDetailPage;
