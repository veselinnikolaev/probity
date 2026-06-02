import React, { useState, useMemo } from 'react';
import { useSearchParams, useLocation } from 'react-router-dom';
import { usePortfolioDetail } from '@/hooks/portfolio';
import { StatCard } from '@/components/probity/StatCard';
import { ProbityButton } from '@/components/probity/ProbityButton';
import { PortfolioDetailTable } from '@/components/probity/PortfolioDetailTable';
import { AddPositionDrawer } from '@/components/probity/AddPositionDrawer';
import { DataBoundary } from '@/components/core/DataBoundary';
import { EmptyState } from '@/components/probity/EmptyState';
import { ErrorState } from '@/components/probity/ErrorState';
import { StatCardSkeleton, TableSkeleton } from '@/components/probity/SkeletonLoader';
import { portfolioService } from '@/services/portfolio';
import { useMutation } from '@/hooks/useMutation';
import styles from './portfolio-detail.module.css';
import { useTopbarSlot } from '@/hooks/useTopbarSlot';
import { buildStatCards } from '@/utils/portfolioStats';
import { DATE_RANGE_OPTIONS } from '@/constants/portfolio';

const PortfolioDetailPage: React.FC = () => {
  const { state: routerState } = useLocation();
  const [searchParams] = useSearchParams();
  const portfolioId = searchParams.get('portfolio') || '';

  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [editingPositionId, setEditingPositionId] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const detailState = usePortfolioDetail(portfolioId, DATE_RANGE_OPTIONS[1].value);

  const deleteMutation = useMutation<void, string>(async (positionId) => {
    await portfolioService.deletePosition(portfolioId, positionId);
    detailState.refetch();
  });

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
    setDeleteError(null);
    try {
      await deleteMutation.execute(positionId);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to delete position';
      setDeleteError(errorMessage);
    }
  };


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
                            {(detail) => useMemo(() => buildStatCards(detail.summary, detail.positions.length), [detail.summary, detail.positions.length]).map((card, idx) => (
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
                                {deleteError && (
                                    <div style={{
                                        padding: '12px',
                                        backgroundColor: 'rgba(239,68,68,0.08)',
                                        border: '1px solid rgba(239,68,68,0.25)',
                                        borderRadius: 6,
                                        marginBottom: 16,
                                        fontSize: 13,
                                        color: '#EF4444',
                                        fontFamily: 'var(--font-body)',
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'center',
                                    }}>
                                        <span>{deleteError}</span>
                                        <button
                                            onClick={() => setDeleteError(null)}
                                            style={{
                                                background: 'transparent',
                                                border: 'none',
                                                color: '#EF4444',
                                                cursor: 'pointer',
                                                fontSize: 18,
                                                padding: 4,
                                            }}
                                        >
                                            ✕
                                        </button>
                                    </div>
                                )}
                                <PortfolioDetailTable
                                    positions={detail.positions}
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
