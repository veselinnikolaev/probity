import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { StatCard } from '../../components/probity/StatCard';
import { DropdownSelect } from '../../components/probity/DropdownSelect';
import { DataTable, DataRow } from '../../components/probity/DataTable';
import { DonutChart } from '../../components/probity/DonutChart';
import { LineChart } from '../../components/probity/LineChart';
import { DataBoundary } from '../../components/core/DataBoundary';
import { EmptyState } from '../../components/probity/EmptyState';
import { ErrorState } from '../../components/probity/ErrorState';
import { StatCardSkeleton, TableSkeleton } from '../../components/probity/SkeletonLoader';
import styles from './dashboard.module.css';
import { usePortfolios } from '../../hooks/usePortfolios';
import { usePortfolioDetail } from '../../hooks/usePortfolioDetail';
import type { StatCardProps } from '../../components/probity/StatCard';
import {useTopbarSlot} from "../../hooks/useTopbarSlot";

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
// ─────────────────────────────────────────────────────────────────────────────

const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const [selectedPortfolio, setSelectedPortfolio] = useState('');
  const [selectedDateRange, setSelectedDateRange] = useState('90d');
  const [sectorFilter, setSectorFilter] = useState<string | null>(null);

  // Fetch all portfolios
  const portfoliosState = usePortfolios();

  // Fetch detail data for selected portfolio
  const detailState = usePortfolioDetail(selectedPortfolio, selectedDateRange);

  useEffect(() => {
    if (portfoliosState.data && portfoliosState.data.length > 0 && !selectedPortfolio) {
      setSelectedPortfolio(portfoliosState.data[0].id);
    }
  }, [portfoliosState.data, selectedPortfolio]);

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

  const buildStatCards = (summary: any): StatCardProps[] => [
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
      label: 'Daily Return',
      value: summary.dailyReturn.toLocaleString('en-US', { maximumFractionDigits: 2 }),
      delta: `${summary.dailyReturnDelta >= 0 ? '+' : ''}${summary.dailyReturnDelta.toFixed(2)}%`,
      deltaType: summary.dailyReturnDelta >= 0 ? 'up' : 'down',
      sparklineData: summary.sparklines?.dailyReturn || [],
      prefix: '$',
      invertSentiment: false,
    },
    {
      label: 'Portfolio Volatility',
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
      <>
        {portfolioOptions.length > 0 && (
            <DropdownSelect
                options={portfolioOptions}
                value={selectedPortfolio}
                onChange={(val) => { setSelectedPortfolio(val); setSectorFilter(null); }}
                width="240px"
            />
        )}
        {portfolioOptions.length > 0 && (
            <DropdownSelect
                options={dateRangeOptions}
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
                      onClick={() => navigate('/portfolios')}
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
                        description="Add assets to see your portfolio metrics"
                        size="sm"
                      />
                    }
                    error={(msg) => (
                      <ErrorState message={msg} onRetry={detailState.refetch} size="sm" />
                    )}
                  >
                    {(detail) => (
                      <>
                        {buildStatCards(detail.summary).map((card, idx) => (
                          <StatCard key={idx} {...card} />
                        ))}
                      </>
                    )}
                  </DataBoundary>
                </section>

                {/* POSITIONS AND COMPOSITION */}
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
                    <DataBoundary
                      state={detailState}
                      isEmpty={(data) => data.positions.length === 0}
                      loading={<TableSkeleton rows={5} />}
                      empty={
                        <EmptyState
                          title="No Positions"
                          description="Add assets to your portfolio"
                          size="sm"
                        />
                      }
                      error={(msg) => (
                        <ErrorState message={msg} onRetry={detailState.refetch} size="sm" />
                      )}
                    >
                      {(detail) => {
                        const filteredPositions: DataRow[] = (sectorFilter
                          ? detail.positions.filter(p => p.sector === sectorFilter)
                          : detail.positions
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
                          marketCap: p.positionValue,
                        }));

                        return <DataTable rows={filteredPositions} />;
                      }}
                    </DataBoundary>
                  </div>

                  <div className={styles['chart-container']}>
                    <div className={styles['chart-header']}>Portfolio Composition</div>
                    <div className={styles['chart-wrapper']}>
                      <DataBoundary
                        state={detailState}
                        isEmpty={(data) => data.composition.length === 0}
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
                          <DonutChart
                            data={detail.composition}
                            onSegmentClick={(label) => setSectorFilter(prev => prev === label ? null : label)}
                          />
                        )}
                      </DataBoundary>
                    </div>
                  </div>
                </section>

                {/* VOLATILITY AND ALERTS */}
                <section className={styles['bottom-row']}>
                  <div className={styles['volatility-chart-container']}>
                    <div className={styles['volatility-header']}>
                      Volatility Over Time ({selectedDateRange})
                    </div>
                    <div className={styles['volatility-wrapper']}>
                      <DataBoundary
                        state={detailState}
                        isEmpty={(data) => data.volatility.length === 0}
                        loading={<div style={{ padding: '40px', textAlign: 'center' }}>Loading chart…</div>}
                        empty={
                          <EmptyState
                            title="No Data"
                            description="Insufficient volatility history"
                            size="sm"
                          />
                        }
                        error={(msg) => (
                          <ErrorState message={msg} onRetry={detailState.refetch} size="sm" />
                        )}
                      >
                        {(detail) => (
                          <LineChart
                            data={detail.volatility.map(v => v.value)}
                            dateRange={selectedDateRange}
                          />
                        )}
                      </DataBoundary>
                    </div>
                  </div>

                  <div className={styles['alert-feed-container']}>
                    <div className={styles['alert-feed-header']}>Risk Alerts</div>
                    <DataBoundary
                      state={detailState}
                      isEmpty={(data) => data.alerts.length === 0}
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
                      {(detail) => (
                        <div className={styles['alert-feed']}>
                          {detail.alerts.map(alert => (
                            <div key={alert.id} className={styles['alert-item']}>
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
                      )}
                    </DataBoundary>
                  </div>
                </section>
              </div>
            )}
          </DataBoundary>
        </main>
  );
};

export default DashboardPage;
