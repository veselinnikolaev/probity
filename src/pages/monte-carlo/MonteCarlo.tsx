import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { usePortfolios } from '../../hooks/usePortfolios';
import { StatCard } from '../../components/probity/StatCard';
import { SidebarNavItem } from '../../components/probity/SidebarNavItem';
import { DropdownSelect } from '../../components/probity/DropdownSelect';
import { DataBoundary } from '../../components/core/DataBoundary';
import { EmptyState } from '../../components/probity/EmptyState';
import { ErrorState } from '../../components/probity/ErrorState';
import { StatCardSkeleton } from '../../components/probity/SkeletonLoader';
import { FanChart } from '../../components/probity/FanChart';
import { DistributionHistogram } from '../../components/probity/DistributionHistogram';
import { PercentileTable } from '../../components/probity/PercentileTable';
import { SimulationRequest, SimulationAsyncState } from '../../types/simulation';
import { runSimulation } from '../../api/simulationService';
import styles from './monte-carlo.module.css';

// ── Simulation State Hook ────────────────────────────────────────────────────
function useSimulation(portfolioId: string) {
  const [state, setState] = useState<SimulationAsyncState>({ status: 'idle', data: null });

  const run = useCallback(
    async (request: SimulationRequest, currentValue: number) => {
      setState({ status: 'loading', data: null });
      try {
        const result = await runSimulation(request, currentValue);
        setState({ status: 'success', data: result });
        return result;
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to run simulation';
        setState({ status: 'error', data: null, error: message });
      }
    },
    []
  );

  const refetch = useCallback((currentValue: number) => {
    if (state.status === 'success' && state.data) {
      const request: SimulationRequest = {
        portfolioId,
        numberOfSimulations: state.data.parameters.numberOfSimulations,
        timeHorizonDays: state.data.parameters.timeHorizonDays,
        confidenceLevel: state.data.parameters.confidenceLevel,
      };
      run(request, currentValue);
    }
  }, [state, portfolioId, run]);

  return { state, run, refetch };
}

// ── Main Component ───────────────────────────────────────────────────────────
const MonteCarloPage: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const portfoliosState = usePortfolios();

  // ── Component state ──────────────────────────────────────────────────────────
  const [activeNav, setActiveNav] = useState('monte');
  const [selectedPortfolio, setSelectedPortfolio] = useState('');
  const [numberOfSims, setNumberOfSims] = useState(10000);
  const [timeHorizon, setTimeHorizon] = useState(365);
  const [confidenceLevel, setConfidenceLevel] = useState(95);
  const [mockCurrentValue, setMockCurrentValue] = useState(100000);

  const simulationState = useSimulation(selectedPortfolio);

  // Auto-select first portfolio
  useEffect(() => {
    if (portfoliosState.data && portfoliosState.data.length > 0 && !selectedPortfolio) {
      setSelectedPortfolio(portfoliosState.data[0].id);
      setMockCurrentValue(portfoliosState.data[0].totalValue);
    }
  }, [portfoliosState.data, selectedPortfolio]);

  // ── Event handlers ───────────────────────────────────────────────────────────
  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const handleRunSimulation = async () => {
    const request: SimulationRequest = {
      portfolioId: selectedPortfolio,
      numberOfSimulations: numberOfSims,
      timeHorizonDays: timeHorizon,
      confidenceLevel,
    };
    await simulationState.run(request, mockCurrentValue);
  };

  const isLoading = simulationState.state.status === 'loading';
  const isRunning = simulationState.state.status === 'success';

  // ── Derived data ─────────────────────────────────────────────────────────────
  const portfolioOptions = (portfoliosState.data || []).map((p) => ({
    value: p.id,
    label: p.name,
    description: `$${p.totalValue.toLocaleString()}`,
  }));

  const timeHorizonOptions = [
    { value: '30', label: '30 days' },
    { value: '90', label: '90 days' },
    { value: '180', label: '180 days' },
    { value: '365', label: '365 days' },
  ];

  const navItems = [
    { label: 'Dashboard', icon: '📊', id: 'dashboard' },
    { label: 'Portfolios', icon: '💼', id: 'portfolios' },
    { label: 'Risk Analysis', icon: '⚠️', id: 'risk' },
    { label: 'VaR Report', icon: '📈', id: 'var' },
    { label: 'Monte Carlo', icon: '🎲', id: 'monte' },
    { label: 'Correlation', icon: '🔗', id: 'correlation' },
    { label: 'Settings', icon: '⚙️', id: 'settings' },
  ];

  // ── UI Components ────────────────────────────────────────────────────────────
  const Sidebar = (
    <aside className={styles['sidebar']}>
      <div className={styles['sidebar-logo']}>Probity</div>
      <nav className={styles['sidebar-nav']}>
        {navItems.map((item) => (
          <SidebarNavItem
            key={item.id}
            label={item.label}
            icon={item.icon}
            active={activeNav === item.id}
            onClick={() => {
              setActiveNav(item.id);
              navigate(`/${item.id}`);
            }}
          />
        ))}
      </nav>
    </aside>
  );

  const Topbar = (
    <header className={styles['topbar']}>
      <div className={styles['topbar-left']}>
        {portfolioOptions.length > 0 && (
          <DropdownSelect
            options={portfolioOptions}
            value={selectedPortfolio}
            onChange={(val) => {
              setSelectedPortfolio(val);
              const portfolio = portfoliosState.data?.find((p) => p.id === val);
              if (portfolio) setMockCurrentValue(portfolio.totalValue);
            }}
            width="240px"
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

  // ── Main render ──────────────────────────────────────────────────────────────
  return (
    <div className={styles['monte-carlo-layout']}>
      {Sidebar}
      <div className={styles['main-wrapper']}>
        {Topbar}
        <main className={styles['content-area']}>
          <DataBoundary
            state={portfoliosState}
            isEmpty={(data) => data.length === 0}
            loading={<div className={styles['loading-state']}>Loading portfolios…</div>}
            empty={
              <div className={styles['empty-state-container']}>
                <EmptyState
                  icon="💼"
                  title="Welcome to Probity"
                  description="You don't have any portfolios set up yet."
                  action={
                    <button
                      style={{
                        padding: '10px 20px',
                        background: 'var(--pb-accent)',
                        color: 'white',
                        border: 'none',
                        borderRadius: '6px',
                        fontSize: '13px',
                        fontWeight: 500,
                        cursor: 'pointer',
                        fontFamily: 'var(--font-body)',
                      }}
                      onClick={() => navigate('/portfolios')}
                    >
                      Create Your First Portfolio
                    </button>
                  }
                />
              </div>
            }
            error={(msg) => (
              <div style={{ padding: '20px' }}>
                <ErrorState message={msg} onRetry={portfoliosState.refetch} />
              </div>
            )}
          >
            {() => (
              <div className={styles['content-container']}>
                {/* ── SECTION 1: CONTROLS ── */}
                <section className={styles['controls-bar']}>
                  <div className={styles['control-group']}>
                    <label className={styles['control-label']}>Simulations</label>
                    <input
                      type="number"
                      className={styles['control-input']}
                      value={numberOfSims}
                      onChange={(e) => setNumberOfSims(Math.max(1, parseInt(e.target.value) || 1))}
                      min="1"
                      max="100000"
                      disabled={isLoading}
                    />
                  </div>

                  <div className={styles['control-group']}>
                    <label className={styles['control-label']}>Time Horizon</label>
                    <select
                      className={styles['control-input']}
                      value={timeHorizon}
                      onChange={(e) => setTimeHorizon(parseInt(e.target.value))}
                      disabled={isLoading}
                      style={{ cursor: isLoading ? 'not-allowed' : 'pointer' }}
                    >
                      {timeHorizonOptions.map((opt) => (
                        <option key={opt.value} value={opt.value} style={{ background: 'var(--pb-surface)' }}>
                          {opt.label}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className={styles['control-group']}>
                    <label className={styles['control-label']}>Confidence Level</label>
                    <div className={styles['segmented-control']}>
                      {[90, 95, 99].map((level) => (
                        <button
                          key={level}
                          className={`${styles['segmented-button']} ${confidenceLevel === level ? styles['active'] : ''}`}
                          onClick={() => setConfidenceLevel(level)}
                          disabled={isLoading}
                        >
                          {level}%
                        </button>
                      ))}
                    </div>
                  </div>

                  <div className={styles['control-action']}>
                    <button
                      className={styles['run-button']}
                      onClick={handleRunSimulation}
                      disabled={!selectedPortfolio || isLoading}
                    >
                      {isLoading ? '🔄 Running…' : '▶ Run Simulation'}
                    </button>
                    {isRunning && (
                      <div className={styles['last-run']}>
                        Last run: {new Date(simulationState.state.data?.createdAt || '').toLocaleTimeString()}
                      </div>
                    )}
                  </div>
                </section>

                {/* ── Conditional rendering based on simulation state ── */}
                {simulationState.state.status === 'idle' ? (
                  <div className={styles['empty-state-container']}>
                    <EmptyState
                      icon="🎲"
                      title="Ready for Simulation"
                      description="Configure parameters and run a Monte Carlo simulation to see potential portfolio outcomes."
                    />
                  </div>
                ) : simulationState.state.status === 'error' ? (
                  <ErrorState
                    message={simulationState.state.error}
                    onRetry={() => simulationState.refetch(mockCurrentValue)}
                  />
                ) : simulationState.state.status === 'loading' ? (
                  <>
                    <h2 className={styles['section-title']}>Simulation Results</h2>
                    <div className={styles['chart-container']}>
                      <div className={styles['loading-state']}>Running simulation…</div>
                    </div>
                    <h2 className={styles['section-title']}>Outcome Statistics</h2>
                    <div className={styles['outcomes-grid']}>
                      {[...Array(4)].map((_, i) => (
                        <StatCardSkeleton key={i} />
                      ))}
                    </div>
                  </>
                ) : (
                  simulationState.state.data && (
                    <>
                      {/* ── SECTION 2: FAN CHART ── */}
                      <section>
                        <h2 className={styles['section-title']}>Simulated Portfolio Paths</h2>
                        <div className={styles['chart-container']}>
                          <div className={styles['chart-wrapper']}>
                            <FanChart
                              allPaths={simulationState.state.data.allPaths}
                              percentileSeries={simulationState.state.data.percentileSeries}
                              currentValue={simulationState.state.data.currentPortfolioValue}
                              timeHorizonDays={simulationState.state.data.parameters.timeHorizonDays}
                              height={400}
                            />
                          </div>
                        </div>
                      </section>

                      {/* ── SECTION 3: OUTCOME STATISTICS ── */}
                      <section>
                        <h2 className={styles['section-title']}>Outcome Statistics</h2>
                        <div className={styles['outcomes-grid']}>
                          <StatCard
                            label="Probability of 10% Loss"
                            value={`${simulationState.state.data.outcomes.probabilityOf10PercentLoss.toFixed(1)}%`}
                            deltaType="neutral"
                          />
                          <StatCard
                            label="Probability of 20% Loss"
                            value={`${simulationState.state.data.outcomes.probabilityOf20PercentLoss.toFixed(1)}%`}
                            deltaType="neutral"
                          />
                          <StatCard
                            label="Median Final Value"
                            value={`$${Math.round(simulationState.state.data.statistics.medianFinalValue).toLocaleString()}`}
                            delta={`${(((simulationState.state.data.statistics.medianFinalValue - simulationState.state.data.currentPortfolioValue) / simulationState.state.data.currentPortfolioValue) * 100).toFixed(1)}%`}
                            deltaType={simulationState.state.data.statistics.medianFinalValue >= simulationState.state.data.currentPortfolioValue ? 'up' : 'down'}
                          />
                          <StatCard
                            label="Best Case (90th %ile)"
                            value={`$${Math.round(
                              simulationState.state.data.percentileSeries.find((p) => p.percentile === 90)?.finalValue || 0
                            ).toLocaleString()}`}
                            delta={`${(((simulationState.state.data.percentileSeries.find((p) => p.percentile === 90)?.finalValue || 0) - simulationState.state.data.currentPortfolioValue) / simulationState.state.data.currentPortfolioValue * 100).toFixed(1)}%`}
                            deltaType="up"
                          />
                        </div>
                      </section>

                      {/* ── SECTION 4: BOTTOM SECTION ── */}
                      <section>
                        <div className={styles['bottom-section']}>
                          {/* Left: Distribution */}
                          <div className={styles['bottom-column']}>
                            <div className={styles['chart-container']}>
                              <DistributionHistogram
                                distribution={simulationState.state.data.distribution}
                                currentValue={simulationState.state.data.currentPortfolioValue}
                                medianValue={simulationState.state.data.statistics.medianFinalValue}
                                height={300}
                              />
                            </div>
                          </div>

                          {/* Right: Percentile table */}
                          <div className={styles['bottom-column']}>
                            <div className={styles['chart-container']}>
                              <PercentileTable
                                percentileSeries={simulationState.state.data.percentileSeries}
                                currentValue={simulationState.state.data.currentPortfolioValue}
                              />
                            </div>
                          </div>
                        </div>
                      </section>
                    </>
                  )
                )}
              </div>
            )}
          </DataBoundary>
        </main>
      </div>
    </div>
  );
};

export default MonteCarloPage;
