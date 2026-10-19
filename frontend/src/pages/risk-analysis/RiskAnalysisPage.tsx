import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { StatCard } from '@/components/probity/StatCard';
import { DropdownSelect } from '@/components/probity/DropdownSelect';
import { DataTable, DataRow } from '@/components/probity/DataTable';
import { AlertIcon } from '@/components/probity/AlertIcon';
import { CorrelationHeatmap } from '@/components/probity/CorrelationHeatmap';
import { DataBoundary } from '@/components/core/DataBoundary';
import { EmptyState } from '@/components/probity/EmptyState';
import { ErrorState } from '@/components/probity/ErrorState';
import { StatCardSkeleton, TableSkeleton } from '@/components/probity/SkeletonLoader';
import styles from './risk-analysis.module.css';
import { usePortfolios } from '@/hooks/portfolio';
import { usePortfolioRiskDetail } from '@/hooks/portfolio';
import { useTopbarSlot } from '@/hooks/useTopbarSlot';
import { buildPortfolioOptions } from '@/utils/portfolioHelpers';
import { DATE_RANGE_OPTIONS } from '@/constants/portfolio';

// ── Risk Analysis Page ───────────────────────────────────────────────────────
const RiskAnalysisPage: React.FC = () => {
    const navigate = useNavigate();
    const [selectedPortfolio, setSelectedPortfolio] = useState('');
    const [selectedDateRange, setSelectedDateRange] = useState('90d');
    const [alertFilter, setAlertFilter] = useState<'all' | 'high' | 'moderate' | 'low'>('all');

    // Fetch all portfolios
    const portfoliosState = usePortfolios();

    // Fetch detail data for selected portfolio
    const detailState = usePortfolioRiskDetail(selectedPortfolio, selectedDateRange);

    useEffect(() => {
        if (portfoliosState.data && portfoliosState.data.length > 0 && !selectedPortfolio) {
            setSelectedPortfolio(portfoliosState.data[0].id);
        }
    }, [portfoliosState.data, selectedPortfolio]);

    // ── Derived data ─────────────────────────────────────────────────────────
    const portfolioOptions = useMemo(() => buildPortfolioOptions(portfoliosState.data || []), [portfoliosState.data]);

    useTopbarSlot(
        <>
            {portfolioOptions.length > 0 && (
                <DropdownSelect
                    options={portfolioOptions}
                    value={selectedPortfolio}
                    onChange={(val) => setSelectedPortfolio(val)}
                    width="240px"
                />
            )}
            {portfolioOptions.length > 0 && (
                <DropdownSelect
                    options={DATE_RANGE_OPTIONS}
                    value={selectedDateRange}
                    onChange={setSelectedDateRange}
                    width="180px"
                />
            )}
        </>,
        [portfolioOptions, selectedPortfolio, selectedDateRange]
    );

    // ── Main Render ───────────────────────────────────────────────────────────
    return (
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
                            <div className={styles['empty-state-container']}>
                                <EmptyState
                                    icon="💼"
                                    title="Welcome to Probity"
                                    description="You don't have any portfolios set up yet."
                                    action={
                                        <button
                                            className={styles['primary-button']}
                                            onClick={() => navigate('/portfolios/new')}
                                        >
                                            Create Your First Portfolio
                                        </button>
                                    }
                                />
                            </div>
                        }
                        error={(msg) => (
                            <div className={styles['error-state']}>
                                <ErrorState message={msg} onRetry={portfoliosState.refetch} />
                            </div>
                        )}
                    >
                        {() => (
                            <div className={styles['content-zones']}>
                                {/* SECTION 1: RISK OVERVIEW */}
                                <section className={styles['risk-overview']}>
                                    <h2 className={styles['section-title']}>Risk Overview</h2>
                                    <DataBoundary
                                        state={detailState}
                                        isEmpty={(data) => !data}
                                        loading={
                                            <>
                                                {[...Array(4)].map((_, i) => (
                                                    <StatCardSkeleton key={i} />
                                                ))}
                                            </>
                                        }
                                        empty={
                                            <EmptyState
                                                title="Portfolio Empty"
                                                description="Add assets to see your portfolio metrics"
                                                size="sm"
                                            />
                                        }
                                        error={(msg) => (
                                            <ErrorState message={msg} onRetry={detailState.refetch} size="sm" />
                                        )}
                                    >
                                        {(detail) => (
                                            <div className={styles['metric-cards-grid']}>
                                                <StatCard
                                                    label="Portfolio Volatility"
                                                    value={`${detail.summary.volatility.toFixed(1)}%`}
                                                    suffix="annualized"
                                                    delta={`${detail.summary.volatilityDelta >= 0 ? '+' : ''}${detail.summary.volatilityDelta.toFixed(1)}%`}
                                                    deltaType={detail.summary.volatilityDelta <= 0 ? 'down' : 'up'}
                                                    riskLevel={detail.summary.volatility > 25 ? 'HIGH' : detail.summary.volatility > 15 ? 'MODERATE' : 'LOW'}
                                                    sparklineData={detail.summary.sparklines?.volatility || []}
                                                    invertSentiment={true}
                                                />
                                                <StatCard
                                                    label="Beta vs S&P 500"
                                                    value={detail.riskMetrics.beta.toFixed(2)}
                                                    delta={`${detail.riskMetrics.betaDelta >= 0 ? '+' : ''}${detail.riskMetrics.betaDelta.toFixed(2)}`}
                                                    deltaType={detail.riskMetrics.betaDelta >= 0 ? 'up' : 'down'}
                                                    riskLevel={detail.riskMetrics.beta > 1.5 ? 'HIGH' : detail.riskMetrics.beta > 0.8 ? 'MODERATE' : 'LOW'}
                                                    sparklineData={detail.riskMetrics.betaSparkline}
                                                />
                                                <StatCard
                                                    label="Max Drawdown"
                                                    value={`${detail.riskMetrics.maxDrawdown.toFixed(1)}%`}
                                                    delta={`${detail.riskMetrics.maxDrawdownDelta >= 0 ? '+' : ''}${detail.riskMetrics.maxDrawdownDelta.toFixed(1)}%`}
                                                    deltaType={detail.riskMetrics.maxDrawdownDelta <= 0 ? 'down' : 'up'}
                                                    riskLevel={detail.riskMetrics.maxDrawdown > 30 ? 'HIGH' : detail.riskMetrics.maxDrawdown > 15 ? 'MODERATE' : 'LOW'}
                                                    sparklineData={detail.riskMetrics.maxDrawdownSparkline}
                                                    invertSentiment={true}
                                                />
                                                <StatCard
                                                    label="Concentration Index"
                                                    value={detail.riskMetrics.concentrationIndex.toFixed(2)}
                                                    delta={`${detail.riskMetrics.concentrationDelta >= 0 ? '+' : ''}${detail.riskMetrics.concentrationDelta.toFixed(2)}`}
                                                    deltaType={detail.riskMetrics.concentrationDelta <= 0 ? 'down' : 'up'}
                                                    riskLevel={detail.riskMetrics.concentrationIndex > 0.5 ? 'HIGH' : detail.riskMetrics.concentrationIndex > 0.25 ? 'MODERATE' : 'LOW'}
                                                    sparklineData={detail.riskMetrics.concentrationSparkline}
                                                    invertSentiment={true}
                                                />
                                            </div>
                                        )}
                                    </DataBoundary>
                                </section>

                                {/* SECTION 2: VOLATILITY BREAKDOWN */}
                                <section className={styles['volatility-breakdown']}>
                                    <h2 className={styles['section-title']}>Volatility Breakdown</h2>
                                    <div className={styles['volatility-content']}>
                                        {/* Left: Stacked bar chart */}
                                        <div className={styles['volatility-chart-container']}>
                                            <div className={styles['subsection-header']}>Asset Contribution to Portfolio Volatility</div>
                                            <DataBoundary
                                                state={detailState}
                                                isEmpty={(data) => !data.positions || data.positions.length === 0}
                                                loading={<div style={{ padding: '40px', textAlign: 'center' }}>Loading chart…</div>}
                                                empty={
                                                    <EmptyState
                                                        title="No Data"
                                                        description="Your portfolio needs positions"
                                                        size="sm"
                                                    />
                                                }
                                                error={(msg) => (
                                                    <ErrorState message={msg} onRetry={detailState.refetch} size="sm" />
                                                )}
                                            >
                                                {(detail) => (
                                                    <div className={styles['stacked-bar-chart']}>
                                                        {/* Placeholder for stacked bar chart */}
                                                        <svg className={styles['chart-svg']} viewBox="0 0 600 200">
                                                            {/* Render a simple stacked bar chart */}
                                                            {detail.positions.slice(0, 8).map((pos, idx) => {
                                                                const positionWidth = (600 / detail.positions.slice(0, 8).length);
                                                                const maxContribution = Math.max(
                                                                    ...detail.positions.slice(0, 8).map(p => p.volatilityContribution ?? 0),
                                                                    1  // prevent division by zero
                                                                );
                                                                const contribution = pos.volatilityContribution ?? 0;
                                                                const height = (contribution / maxContribution) * 150;
                                                                return (
                                                                    <g key={idx}>
                                                                        <rect
                                                                            x={idx * positionWidth + 10}
                                                                            y={180 - height}
                                                                            width={positionWidth - 20}
                                                                            height={height}
                                                                            fill={`hsl(${(idx * 45) % 360}, 70%, 55%)`}
                                                                            stroke="var(--pb-border)"
                                                                            strokeWidth="1"
                                                                        />
                                                                        <text
                                                                            x={idx * positionWidth + positionWidth / 2}
                                                                            y="195"
                                                                            textAnchor="middle"
                                                                            fontSize="11"
                                                                            fill="var(--pb-text-secondary)"
                                                                            fontFamily="var(--font-mono)"
                                                                        >
                                                                            {pos.ticker}
                                                                        </text>
                                                                    </g>
                                                                );
                                                            })}
                                                        </svg>
                                                    </div>
                                                )}
                                            </DataBoundary>
                                        </div>

                                        {/* Right: Volatility table */}
                                        <div className={styles['volatility-table-container']}>
                                            <div className={styles['subsection-header']}>Volatility Metrics</div>
                                            <DataBoundary
                                                state={detailState}
                                                isEmpty={(data) => !data.positions || data.positions.length === 0}
                                                loading={<TableSkeleton rows={5} />}
                                                empty={
                                                    <EmptyState
                                                        title="No Data"
                                                        description="Add assets to your portfolio"
                                                        size="sm"
                                                    />
                                                }
                                                error={(msg) => (
                                                    <ErrorState message={msg} onRetry={detailState.refetch} size="sm" />
                                                )}
                                            >
                                                {(detail) => {
                                                    const volatilityRows: DataRow[] = detail.positions.map(p => ({
                                                        id: p.id,
                                                        ticker: p.ticker,
                                                        name: p.name,
                                                        volatility: `${(p.volatility || 0).toFixed(1)}%`,
                                                        weight: `${(p.weight || 0).toFixed(1)}%`,
                                                        contribution: `${(p.volatilityContribution || 0).toFixed(2)}%`,
                                                        assetType: p.assetType,
                                                        price: `$${p.price.toFixed(2)}`,
                                                        change: `${p.change >= 0 ? '+' : ''}$${Math.abs(p.change).toFixed(2)}`,
                                                        changePercent: `${p.changePercent >= 0 ? '+' : ''}${p.changePercent.toFixed(2)}%`,
                                                        riskScore: p.riskScore,
                                                        riskLevel: p.riskLevel,
                                                        volume: p.volume,
                                                        marketCap: p.positionValue,
                                                    }));

                                                    return <DataTable rows={volatilityRows} />;
                                                }}
                                            </DataBoundary>
                                        </div>
                                    </div>
                                </section>

                                {/* SECTION 3: CORRELATION HEATMAP */}
                                <section className={styles['correlation-section']}>
                                    <h2 className={styles['section-title']}>Correlation Heatmap</h2>
                                    <div className={styles['correlation-description']}>
                                        Blue indicates negative correlation, Red indicates positive correlation. Hover over cells to see asset pairs.
                                    </div>
                                    <DataBoundary
                                        state={detailState}
                                        isEmpty={(data) => !data.positions || data.positions.length === 0}
                                        loading={<div style={{ padding: '40px', textAlign: 'center' }}>Loading heatmap…</div>}
                                        empty={
                                            <EmptyState
                                                title="No Data"
                                                description="Add multiple assets to see correlations"
                                                size="sm"
                                            />
                                        }
                                        error={(msg) => (
                                            <ErrorState message={msg} onRetry={detailState.refetch} size="sm" />
                                        )}
                                    >
                                        {(detail) => {
                                            return (
                                                <CorrelationHeatmap
                                                    assets={detail.correlation.assets}
                                                    correlations={detail.correlation.matrix}
                                                />
                                            );
                                        }}
                                    </DataBoundary>
                                </section>

                                {/* SECTION 4: RISK ALERTS */}
                                <section className={styles['alerts-section']}>
                                    <h2 className={styles['section-title']}>Risk Alerts</h2>
                                    <div className={styles['alert-filters']}>
                                        {(['all', 'high', 'moderate', 'low'] as const).map(filter => (
                                            <button
                                                key={filter}
                                                className={`${styles['filter-button']} ${alertFilter === filter ? styles['filter-active'] : ''}`}
                                                onClick={() => setAlertFilter(filter)}
                                            >
                                                {filter === 'all' ? 'All' : filter.charAt(0).toUpperCase() + filter.slice(1)}
                                            </button>
                                        ))}
                                    </div>
                                    <DataBoundary
                                        state={detailState}
                                        isEmpty={(data) => !data.alerts || data.alerts.length === 0}
                                        loading={<div style={{ padding: '20px', textAlign: 'center' }}>Loading alerts…</div>}
                                        empty={
                                            <EmptyState
                                                title="No Alerts"
                                                description="Your portfolio is looking good"
                                                size="sm"
                                            />
                                        }
                                        error={(msg) => (
                                            <ErrorState message={msg} onRetry={detailState.refetch} size="sm" />
                                        )}
                                    >
                                        {(detail) => {
                                            const filteredAlerts = alertFilter === 'all'
                                                ? detail.alerts
                                                : detail.alerts.filter(a => a.severity === alertFilter);

                                            return (
                                                <div className={styles['alert-list']}>
                                                    {filteredAlerts.map(alert => (
                                                        <div key={alert.id} className={styles['alert-item']}>
                                                            <AlertIcon severity={alert.severity} />
                                                            <div className={styles['alert-content']}>
                                                                <div className={styles['alert-message']}>{alert.message}</div>
                                                                <div className={styles['alert-affected-assets']}>
                                                                    {alert.affectedAssets?.length > 0 && (
                                                                        <>
                                                                            <span className={styles['assets-label']}>Affected:</span>
                                                                            {alert.affectedAssets.map((asset, idx) => (
                                                                                <span key={idx} className={styles['asset-tag']}>{asset}</span>
                                                                            ))}
                                                                        </>
                                                                    )}
                                                                </div>
                                                            </div>
                                                            <div className={styles['alert-meta']}>
                                                                <span className={styles['alert-time']}>{alert.timestamp}</span>
                                                                <span className={`${styles['severity-badge']} ${styles[`severity-${alert.severity}`]}`}>
                                  {alert.severity}
                                </span>
                                                            </div>
                                                        </div>
                                                    ))}
                                                </div>
                                            );
                                        }}
                                    </DataBoundary>
                                </section>
                            </div>
                        )}
                    </DataBoundary>
                </main>
    );
};

export default RiskAnalysisPage;
