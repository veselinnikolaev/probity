import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { StatCard } from '../../components/probity/StatCard';
import { RiskBadge, RiskLevel } from '../../components/probity/RiskBadge';
import { SidebarNavItem } from '../../components/probity/SidebarNavItem';
import { DropdownSelect } from '../../components/probity/DropdownSelect';
import { DataTable, DataRow } from '../../components/probity/DataTable';
import { DataBoundary } from '../../components/core/DataBoundary';
import { EmptyState } from '../../components/probity/EmptyState';
import { ErrorState } from '../../components/probity/ErrorState';
import { StatCardSkeleton, TableSkeleton } from '../../components/probity/SkeletonLoader';
import { Tooltip } from '../../components/probity/Tooltip';
import styles from './risk-analysis.module.css';
import { usePortfolios } from '../../hooks/usePortfolios';
import { usePortfolioRiskDetail  } from '../../hooks/usePortfolioRiskDetail';

// ── Alert icon component ─────────────────────────────────────────────────────
type AlertSeverity = 'high' | 'moderate' | 'low' | string;

function AlertIcon({ severity }: { severity: AlertSeverity }) {
    const baseStyle: React.CSSProperties = {
        width: 18,
        height: 18,
        flexShrink: 0,
        marginTop: 2,
    };

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

// ── Correlation Heatmap Component ────────────────────────────────────────────
interface CorrelationHeatmapProps {
    assets: string[];
    correlations: number[][];
}

function CorrelationHeatmap({ assets, correlations }: CorrelationHeatmapProps) {
    const [hoveredCell, setHoveredCell] = useState<{ row: number; col: number } | null>(null);

    const getColor = (value: number): string => {
        // Blue-to-red diverging scale
        // -1 (negative correlation) = blue, 0 = white, 1 (positive correlation) = red
        if (value < -0.7) return '#1e3a8a';
        if (value < -0.4) return '#3b82f6';
        if (value < -0.1) return '#93c5fd';
        if (value < 0.1) return '#f3f4f6';
        if (value < 0.4) return '#fca5a5';
        if (value < 0.7) return '#ef4444';
        return '#991b1b';
    };

    return (
        <div className={styles['heatmap-wrapper']}>
            <div className={styles['heatmap-container']}>
                {/* Header row */}
                <div className={styles['heatmap-header-row']}>
                    <div className={styles['heatmap-corner']} />
                    {assets.map((asset, i) => (
                        <div key={i} className={styles['heatmap-header-cell']}>
                            <span className={styles['heatmap-header-text']}>{asset}</span>
                        </div>
                    ))}
                </div>

                {/* Data rows */}
                {assets.map((asset, rowIdx) => (
                    <div key={rowIdx} className={styles['heatmap-data-row']}>
                        <div className={styles['heatmap-row-label']}>
                            <span className={styles['heatmap-label-text']}>{asset}</span>
                        </div>
                        {correlations[rowIdx].map((value, colIdx) => (
                            <div
                                key={`${rowIdx}-${colIdx}`}
                                className={styles['heatmap-cell']}
                                style={{
                                    backgroundColor: getColor(value),
                                    cursor: 'pointer',
                                    opacity: hoveredCell ? (hoveredCell.row === rowIdx || hoveredCell.col === colIdx ? 1 : 0.5) : 1,
                                    transition: 'opacity 0.12s',
                                }}
                                onMouseEnter={() => setHoveredCell({ row: rowIdx, col: colIdx })}
                                onMouseLeave={() => setHoveredCell(null)}
                                title={`${asset} vs ${assets[colIdx]}: ${value.toFixed(2)}`}
                            >
                                <span className={styles['heatmap-cell-value']}>{value.toFixed(2)}</span>
                            </div>
                        ))}
                    </div>
                ))}
            </div>

            {/* Legend */}
            <div className={styles['heatmap-legend']}>
                <div className={styles['legend-item']}>
                    <div className={styles['legend-color']} style={{ backgroundColor: '#1e3a8a' }} />
                    <span>Negative Correlation</span>
                </div>
                <div className={styles['legend-item']}>
                    <div className={styles['legend-color']} style={{ backgroundColor: '#f3f4f6' }} />
                    <span>No Correlation</span>
                </div>
                <div className={styles['legend-item']}>
                    <div className={styles['legend-color']} style={{ backgroundColor: '#991b1b' }} />
                    <span>Positive Correlation</span>
                </div>
            </div>
        </div>
    );
}

// ── Risk Analysis Page ───────────────────────────────────────────────────────
const RiskAnalysisPage: React.FC = () => {
    const { user, logout } = useAuth();
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

    const handleLogout = async () => {
        await logout();
        navigate('/login', { replace: true });
    };

    // ── Derived data ─────────────────────────────────────────────────────────
    const portfolioOptions = (portfoliosState.data || []).map(p => ({
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

    // ── Shared UI ─────────────────────────────────────────────────────────────
    const Topbar = (
        <header className={styles['topbar']}>
            <div className={styles['topbar-left']}>
                {portfolioOptions.length > 0 && (
                    <DropdownSelect
                        options={portfolioOptions}
                        value={selectedPortfolio}
                        onChange={(val) => { setSelectedPortfolio(val); }}
                        width="240px"
                    />
                )}
            </div>
            <div className={styles['topbar-center']}>
                {portfolioOptions.length > 0 && (
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
    return (
        <div className={styles['risk-analysis-layout']}>
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
                        {(portfolios) => (
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
            </div>
        </div>
    );
};

export default RiskAnalysisPage;
