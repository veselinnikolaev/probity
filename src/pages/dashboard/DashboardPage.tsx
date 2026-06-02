import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { StatCard } from '@/components/probity/StatCard';
import { DropdownSelect } from '@/components/probity/DropdownSelect';
import { DataTable, DataRow } from '@/components/probity/DataTable';
import { DonutChart } from '@/components/probity/DonutChart';
import { LineChart } from '@/components/probity/LineChart';
import { AlertIcon } from '@/components/probity/AlertIcon';
import { DataBoundary } from '@/components/core/DataBoundary';
import { EmptyState } from '@/components/probity/EmptyState';
import { ErrorState } from '@/components/probity/ErrorState';
import { StatCardSkeleton, TableSkeleton } from '@/components/probity/SkeletonLoader';
import styles from './dashboard.module.css';
import { usePortfolios } from '@/hooks/portfolio';
import { usePortfolioDetail } from '@/hooks/portfolio';
import { useTopbarSlot } from '@/hooks/useTopbarSlot';
import { buildStatCards } from '@/utils/portfolioStats';
import { buildPortfolioOptions } from '@/utils/portfolioHelpers';
import { DATE_RANGE_OPTIONS } from '@/constants/portfolio';


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
  const portfolioOptions = useMemo(() => buildPortfolioOptions(portfoliosState.data || []), [portfoliosState.data]);

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
                        {useMemo(() => buildStatCards(detail.summary), [detail.summary]).map((card, idx) => (
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
