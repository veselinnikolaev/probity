import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { StatCard } from '../../components/probity/StatCard';
import { DataTable, DataRow } from '../../components/probity/DataTable';
import { LineChart } from '../../components/probity/LineChart';
import { SidebarNavItem } from '../../components/probity/SidebarNavItem';
import { DropdownSelect } from '../../components/probity/DropdownSelect';
import { DataBoundary } from '../../components/core/DataBoundary';
import { EmptyState } from '../../components/probity/EmptyState';
import { ErrorState } from '../../components/probity/ErrorState';
import { StatCardSkeleton, TableSkeleton } from '../../components/probity/SkeletonLoader';
import styles from './var-report.module.css';
import { usePortfolios } from '../../hooks/usePortfolios';

// ── VaR Report Page ───────────────────────────────────────────────────────
const VaRReportPage: React.FC = () => {
    const { user, logout } = useAuth();
    const navigate = useNavigate();
    const [activeNav, setActiveNav] = useState('var');
    const [selectedPortfolio, setSelectedPortfolio] = useState('');
    const [confidenceLevel, setConfidenceLevel] = useState('95');
    const [timeHorizon, setTimeHorizon] = useState('1d');

    // Fetch all portfolios
    const portfoliosState = usePortfolios();

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

    const navItems = [
        { label: 'Dashboard',   icon: '📊', id: 'dashboard'   },
        { label: 'Portfolios',  icon: '💼', id: 'portfolios'  },
        { label: 'Risk Analysis', icon: '⚠️', id: 'risk'      },
        { label: 'VaR Report',  icon: '📈', id: 'var'         },
        { label: 'Monte Carlo', icon: '🎲', id: 'monte'       },
        { label: 'Correlation', icon: '🔗', id: 'correlation' },
        { label: 'Settings',    icon: '⚙️', id: 'settings'   },
    ];

    // Mock VaR data
    const varData = {
        valueAtRisk: -4820,
        methodology: {
            formula: 'VaR = μ + Z × σ',
            portfolioVolatility: 18.5,
            zscore: 1.645,
            description: 'Parametric VaR uses normal distribution assumption'
        },
        historicalSparkline: [
            3200, 3400, 3100, 3500, 3300, 3250, 4100, 4500, 4200, 3800,
            3600, 3400, 3200, 3800, 4100, 4300, 4500, 3900, 3600, 3400,
            3500, 3700, 3900, 4200, 4400, 4100, 3800, 3600, 3400, 3200
        ],
        assetVaRTable: [
            { ticker: 'AAPL', weight: 25.3, individualVar: -1221, contribution: -1045, percentOfTotal: 21.7 },
            { ticker: 'MSFT', weight: 18.2, individualVar: -856, contribution: -742, percentOfTotal: 15.4 },
            { ticker: 'TSLA', weight: 15.1, individualVar: -1024, contribution: -891, percentOfTotal: 18.5 },
            { ticker: 'NVDA', weight: 12.4, individualVar: -892, contribution: -768, percentOfTotal: 15.9 },
            { ticker: 'BND', weight: 19.2, individualVar: -341, contribution: -234, percentOfTotal: 4.9 },
            { ticker: 'GLD', weight: 9.8, individualVar: -156, contribution: -140, percentOfTotal: 2.9 },
        ],
        stressEvents: [
            { date: '30d ago', label: 'Market Correction', var: 5200 },
            { date: '22d ago', label: 'Fed Rate Hike', var: 4800 },
            { date: '14d ago', label: 'Earnings Season', var: 3600 },
            { date: '7d ago', label: 'Geopolitical Event', var: 4400 },
            { date: 'Today', label: 'Current', var: 3200 },
        ]
    };

    // Mock return distribution
    const returnDistribution = [
        { range: '-10% to -8%', frequency: 2, isLoss: true },
        { range: '-8% to -6%', frequency: 5, isLoss: true },
        { range: '-6% to -4%', frequency: 12, isLoss: true },
        { range: '-4% to -2%', frequency: 24, isLoss: true },
        { range: '-2% to 0%', frequency: 38, isLoss: true },
        { range: '0% to 2%', frequency: 45, isLoss: false },
        { range: '2% to 4%', frequency: 32, isLoss: false },
        { range: '4% to 6%', frequency: 18, isLoss: false },
        { range: '6% to 8%', frequency: 8, isLoss: false },
        { range: '8% to 10%', frequency: 3, isLoss: false },
    ];

    // ── Shared UI ─────────────────────────────────────────────────────────────
    const Sidebar = (
        <aside className={styles['sidebar']}>
            <div className={styles['sidebar-logo']}>Probity</div>
            <nav className={styles['sidebar-nav']}>
                {navItems.map(item => (
                    <SidebarNavItem
                        key={item.id}
                        label={item.label}
                        icon={item.icon}
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
                {portfolioOptions.length > 0 && (
                    <DropdownSelect
                        options={portfolioOptions}
                        value={selectedPortfolio}
                        onChange={(val) => { setSelectedPortfolio(val); }}
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

    // ── Main Render ───────────────────────────────────────────────────────────
    return (
        <div className={styles['var-report-layout']}>
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
                                {/* SECTION 1: HERO ── VALUE AT RISK ── */}
                                <section className={styles['hero-section']}>
                                    <div className={styles['hero-content']}>
                                        <div className={styles['hero-label']}>Value at Risk (95%)</div>
                                        <div className={styles['hero-value']}>${Math.abs(varData.valueAtRisk).toLocaleString()}</div>
                                        <div className={styles['hero-subtitle']}>
                                            Maximum expected loss over 1 trading day at 95% confidence
                                        </div>

                                        {/* Toggle Controls */}
                                        <div className={styles['controls-section']}>
                                            <div className={styles['control-group']}>
                                                <label className={styles['control-label']}>Confidence Level</label>
                                                <div className={styles['toggle-buttons']}>
                                                    {['90', '95', '99'].map(level => (
                                                        <button
                                                            key={level}
                                                            className={`${styles['toggle-button']} ${confidenceLevel === level ? styles['toggle-active'] : ''}`}
                                                            onClick={() => setConfidenceLevel(level)}
                                                        >
                                                            {level}%
                                                        </button>
                                                    ))}
                                                </div>
                                            </div>

                                            <div className={styles['control-group']}>
                                                <label className={styles['control-label']}>Time Horizon</label>
                                                <div className={styles['toggle-buttons']}>
                                                    {[
                                                        { value: '1d', label: '1 Day' },
                                                        { value: '5d', label: '5 Days' },
                                                        { value: '1m', label: '1 Month' }
                                                    ].map(horizon => (
                                                        <button
                                                            key={horizon.value}
                                                            className={`${styles['toggle-button']} ${timeHorizon === horizon.value ? styles['toggle-active'] : ''}`}
                                                            onClick={() => setTimeHorizon(horizon.value)}
                                                        >
                                                            {horizon.label}
                                                        </button>
                                                    ))}
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </section>

                                {/* SECTION 2: METHODOLOGY ── 2 COLUMNS ── */}
                                <section className={styles['methodology-section']}>
                                    <h2 className={styles['section-title']}>Methodology</h2>

                                    <div className={styles['methodology-grid']}>
                                        {/* Left: Parametric VaR */}
                                        <div className={styles['methodology-card']}>
                                            <div className={styles['methodology-header']}>Parametric VaR</div>
                                            <div className={styles['methodology-body']}>
                                                <div className={styles['formula-display']}>
                                                    <div className={styles['formula-label']}>Formula</div>
                                                    <div className={styles['formula-text']}>VaR = μ + Z × σ</div>
                                                </div>

                                                <div className={styles['methodology-inputs']}>
                                                    <div className={styles['input-row']}>
                                                        <span className={styles['input-label']}>Portfolio Volatility (σ)</span>
                                                        <span className={styles['input-value']}>{varData.methodology.portfolioVolatility}%</span>
                                                    </div>
                                                    <div className={styles['input-row']}>
                                                        <span className={styles['input-label']}>Z-Score (95%)</span>
                                                        <span className={styles['input-value']}>{varData.methodology.zscore}</span>
                                                    </div>
                                                    <div className={styles['input-row']}>
                                                        <span className={styles['input-label']}>Expected Return (μ)</span>
                                                        <span className={styles['input-value']}>0.08%</span>
                                                    </div>
                                                </div>

                                                <div className={styles['methodology-result']}>
                                                    <div className={styles['result-label']}>Calculated VaR</div>
                                                    <div className={styles['result-value']}>-${Math.abs(varData.valueAtRisk).toLocaleString()}</div>
                                                </div>

                                                <div className={styles['methodology-note']}>
                                                    Parametric VaR assumes returns follow a normal distribution and is computationally efficient.
                                                </div>
                                            </div>
                                        </div>

                                        {/* Right: Return Distribution Histogram */}
                                        <div className={styles['methodology-card']}>
                                            <div className={styles['methodology-header']}>Return Distribution</div>
                                            <div className={styles['histogram-container']}>
                                                <svg className={styles['histogram-svg']} viewBox="0 0 400 280">
                                                    {/* Grid lines */}
                                                    {[0, 25, 50, 75, 100].map((y) => (
                                                        <line
                                                            key={`grid-${y}`}
                                                            x1="40"
                                                            y1={250 - (y / 100) * 200}
                                                            x2="380"
                                                            y2={250 - (y / 100) * 200}
                                                            stroke="var(--pb-border)"
                                                            strokeWidth="1"
                                                            strokeDasharray="3,3"
                                                        />
                                                    ))}

                                                    {/* Y-axis label */}
                                                    <text x="15" y="255" fontSize="10" fill="var(--pb-text-secondary)" fontFamily="var(--font-mono)">0</text>
                                                    <text x="8" y="45" fontSize="10" fill="var(--pb-text-secondary)" fontFamily="var(--font-mono)">50</text>

                                                    {/* Bars */}
                                                    {returnDistribution.map((item, idx) => {
                                                        const maxFreq = Math.max(...returnDistribution.map(d => d.frequency));
                                                        const barHeight = (item.frequency / maxFreq) * 200;
                                                        const barX = 50 + (idx * 33);
                                                        const barColor = item.isLoss ? '#EF4444' : '#3B82F6';
                                                        const barOpacity = item.isLoss ? 0.7 : 0.5;

                                                        return (
                                                            <rect
                                                                key={`bar-${idx}`}
                                                                x={barX}
                                                                y={250 - barHeight}
                                                                width="28"
                                                                height={barHeight}
                                                                fill={barColor}
                                                                opacity={barOpacity}
                                                                stroke="var(--pb-border)"
                                                                strokeWidth="0.5"
                                                            />
                                                        );
                                                    })}

                                                    {/* VaR threshold line */}
                                                    <line
                                                        x1="175"
                                                        y1="50"
                                                        x2="175"
                                                        y2="250"
                                                        stroke="#EF4444"
                                                        strokeWidth="2"
                                                        strokeDasharray="4,4"
                                                    />
                                                    <text x="180" y="35" fontSize="10" fill="#EF4444" fontFamily="var(--font-mono)" fontWeight="500">VaR</text>

                                                    {/* Loss tail shading */}
                                                    <rect
                                                        x="50"
                                                        y="50"
                                                        width="125"
                                                        height="200"
                                                        fill="#EF4444"
                                                        opacity="0.08"
                                                    />

                                                    {/* X-axis */}
                                                    <line x1="40" y1="250" x2="380" y2="250" stroke="var(--pb-border)" strokeWidth="1" />

                                                    {/* X-axis labels */}
                                                    <text x="45" y="270" fontSize="9" fill="var(--pb-text-secondary)" fontFamily="var(--font-mono)">-10%</text>
                                                    <text x="165" y="270" fontSize="9" fill="var(--pb-text-secondary)" fontFamily="var(--font-mono)">0%</text>
                                                    <text x="355" y="270" fontSize="9" fill="var(--pb-text-secondary)" fontFamily="var(--font-mono)">+10%</text>
                                                </svg>
                                            </div>
                                        </div>
                                    </div>
                                </section>

                                {/* SECTION 3: VaR BY ASSET ── TABLE ── */}
                                <section className={styles['var-by-asset-section']}>
                                    <h2 className={styles['section-title']}>VaR by Asset</h2>
                                    <div className={styles['asset-table-wrapper']}>
                                        <table className={styles['asset-table']}>
                                            <thead>
                                                <tr>
                                                    <th>Asset</th>
                                                    <th>Weight %</th>
                                                    <th>Individual VaR</th>
                                                    <th>Contribution to Portfolio VaR</th>
                                                    <th>% of Total VaR</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {varData.assetVaRTable.map((row, idx) => (
                                                    <tr key={idx}>
                                                        <td className={styles['asset-name']}>
                                                            <span className={styles['ticker']}>{row.ticker}</span>
                                                        </td>
                                                        <td className={styles['numeric']}>{row.weight.toFixed(1)}%</td>
                                                        <td className={styles['numeric']}>-${Math.abs(row.individualVar).toLocaleString()}</td>
                                                        <td>
                                                            <div className={styles['contribution-cell']}>
                                                                <div className={styles['contribution-bar-container']}>
                                                                    <div
                                                                        className={styles['contribution-bar']}
                                                                        style={{
                                                                            width: `${(Math.abs(row.contribution) / 1045) * 100}%`
                                                                        }}
                                                                    />
                                                                </div>
                                                                <span className={styles['contribution-value']}>-${Math.abs(row.contribution).toLocaleString()}</span>
                                                            </div>
                                                        </td>
                                                        <td className={styles['numeric']}>{row.percentOfTotal.toFixed(1)}%</td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                </section>

                                {/* SECTION 4: HISTORICAL STRESS ── TIMELINE CHART ── */}
                                <section className={styles['historical-stress-section']}>
                                    <h2 className={styles['section-title']}>Historical Stress</h2>
                                    <div className={styles['stress-description']}>
                                        Rolling 30-day VaR showing peaks during volatile market periods
                                    </div>

                                    <div className={styles['timeline-chart-container']}>
                                        <LineChart
                                            data={varData.historicalSparkline}
                                            width={1000}
                                            height={320}
                                            lineColor="#EF4444"
                                            dateRange="30d"
                                        />
                                    </div>

                                    {/* Stress event markers */}
                                    <div className={styles['stress-events']}>
                                        <div className={styles['events-header']}>Marked Events</div>
                                        <div className={styles['events-list']}>
                                            {varData.stressEvents.map((event, idx) => (
                                                <div key={idx} className={styles['event-item']}>
                                                    <div className={styles['event-marker']}>
                                                        <div className={styles['event-dot']} />
                                                    </div>
                                                    <div className={styles['event-info']}>
                                                        <span className={styles['event-label']}>{event.label}</span>
                                                        <span className={styles['event-date']}>{event.date}</span>
                                                    </div>
                                                    <span className={styles['event-var']}>VaR: ${event.var.toLocaleString()}</span>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                </section>
                            </div>
                        )}
                    </DataBoundary>
                </main>
            </div>
        </div>
    );
};

export default VaRReportPage;
