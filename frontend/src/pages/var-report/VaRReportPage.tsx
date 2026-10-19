import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useVaRReport } from '@/hooks/portfolio';
import { LineChart } from '@/components/probity/LineChart';
import { DropdownSelect } from '@/components/probity/DropdownSelect';
import { DataBoundary } from '@/components/core/DataBoundary';
import { EmptyState } from '@/components/probity/EmptyState';
import { ErrorState } from '@/components/probity/ErrorState';
import { StatCardSkeleton } from '@/components/probity/SkeletonLoader';
import styles from './var-report.module.css';
import { usePortfolios } from '@/hooks/portfolio';
import type { VaRReport } from '@/types/portfolio';
import {useTopbarSlot} from "@/hooks/useTopbarSlot";
import { VAR_CONFIDENCE_LEVELS, TIME_HORIZONS } from '@/constants/preferences';

const VaRReportPage: React.FC = () => {
    const navigate = useNavigate();
    const [selectedPortfolio, setSelectedPortfolio] = useState('');
    const [confidenceLevel, setConfidenceLevel] = useState<"90" | "95" | "99">(VAR_CONFIDENCE_LEVELS[1].value);
    const [timeHorizon, setTimeHorizon] = useState<'1d' | '1w' | '1m' | '1y'>(TIME_HORIZONS[0].value);

    const portfoliosState = usePortfolios();

    useEffect(() => {
        if (portfoliosState.data && portfoliosState.data.length > 0 && !selectedPortfolio) {
            setSelectedPortfolio(portfoliosState.data[0].id);
        }
    }, [portfoliosState.data, selectedPortfolio]);

    const confidenceLevelNum = parseFloat(confidenceLevel) / 100;
    const timeHorizonDays = timeHorizon === '1d' ? 1 : timeHorizon === '1w' ? 5 : 21;

    const varReportState = useVaRReport(selectedPortfolio, confidenceLevelNum, timeHorizonDays);

    const portfolioOptions = (portfoliosState.data || []).map(p => ({
        value: p.id,
        label: p.name,
        description: `$${p.totalValue.toLocaleString()}`,
    }));

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
        </>,
        [portfolioOptions, selectedPortfolio]
    );

    // Derived from live data — used to scale contribution bars
    const maxContribution = (varReportState.data?.assetBreakdown ?? [])
        .reduce((max, row) => Math.max(max, Math.abs(row.contribution)), 1);

    return (
                <main className={styles['content-area']}>
                    <DataBoundary
                        state={portfoliosState}
                        isEmpty={(data) => data.length === 0}
                        loading={
                            <div className={styles['loading-state']}>Loading portfolios…</div>
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
                            <DataBoundary
                                state={varReportState}
                                isEmpty={() => false}
                                loading={<StatCardSkeleton />}
                                empty={<></>}
                                error={(msg) => (
                                    <ErrorState message={msg} onRetry={varReportState.refetch} />
                                )}
                            >
                                {(varData: VaRReport) => (
                                    <div className={styles['content-zones']}>

                                        {/* SECTION 1: HERO ── VALUE AT RISK ── */}
                                        <section className={styles['hero-section']}>
                                            <div className={styles['hero-content']}>
                                                <div className={styles['hero-label']}>
                                                    Value at Risk ({confidenceLevel}%)
                                                </div>
                                                <div className={styles['hero-value']}>
                                                    ${Math.abs(varData.valueAtRisk).toLocaleString(
                                                    undefined,
                                                    { maximumFractionDigits: 0 }
                                                )}
                                                </div>
                                                <div className={styles['hero-subtitle']}>
                                                    Maximum expected loss over{' '}
                                                    {timeHorizon === '1d' ? '1 trading day'
                                                        : timeHorizon === '1w' ? '5 trading days'
                                                            : timeHorizon === '1m' ? '1 month'
                                                                : '1 year'}{' '}
                                                    at {confidenceLevel}% confidence
                                                </div>

                                                <div className={styles['controls-section']}>
                                                    <div className={styles['control-group']}>
                                                        <label className={styles['control-label']}>
                                                            Confidence Level
                                                        </label>
                                                        <div className={styles['toggle-buttons']}>
                                                            {VAR_CONFIDENCE_LEVELS.map(level => (
                                                                <button
                                                                    key={level.value}
                                                                    className={`${styles['toggle-button']} ${confidenceLevel === level.value ? styles['toggle-active'] : ''}`}
                                                                    onClick={() => setConfidenceLevel(level.value)}
                                                                >
                                                                    {level.label}
                                                                </button>
                                                            ))}
                                                        </div>
                                                    </div>

                                                    <div className={styles['control-group']}>
                                                        <label className={styles['control-label']}>
                                                            Time Horizon
                                                        </label>
                                                        <div className={styles['toggle-buttons']}>
                                                            {TIME_HORIZONS.map(horizon => (
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
                                                    <div className={styles['methodology-header']}>
                                                        Parametric VaR
                                                    </div>
                                                    <div className={styles['methodology-body']}>
                                                        <div className={styles['formula-display']}>
                                                            <div className={styles['formula-label']}>Formula</div>
                                                            <div className={styles['formula-text']}>
                                                                {varData.methodology.formula}
                                                            </div>
                                                        </div>

                                                        <div className={styles['methodology-inputs']}>
                                                            <div className={styles['input-row']}>
                                                                <span className={styles['input-label']}>
                                                                    Portfolio Volatility (σ)
                                                                </span>
                                                                <span className={styles['input-value']}>
                                                                    {varData.methodology.portfolioVolatility.toFixed(2)}%
                                                                </span>
                                                            </div>
                                                            <div className={styles['input-row']}>
                                                                <span className={styles['input-label']}>
                                                                    Z-Score ({confidenceLevel}%)
                                                                </span>
                                                                <span className={styles['input-value']}>
                                                                    {varData.methodology.zscore.toFixed(3)}
                                                                </span>
                                                            </div>
                                                            <div className={styles['input-row']}>
                                                                <span className={styles['input-label']}>
                                                                    Expected Return (μ)
                                                                </span>
                                                                <span className={styles['input-value']}>
                                                                    {varData.methodology.expectedReturn.toFixed(4)}%
                                                                </span>
                                                            </div>
                                                        </div>

                                                        <div className={styles['methodology-result']}>
                                                            <div className={styles['result-label']}>
                                                                Calculated VaR
                                                            </div>
                                                            <div className={styles['result-value']}>
                                                                -${Math.abs(varData.valueAtRisk).toLocaleString(
                                                                undefined,
                                                                { maximumFractionDigits: 0 }
                                                            )}
                                                            </div>
                                                        </div>

                                                        <div className={styles['methodology-note']}>
                                                            Parametric VaR assumes returns follow a normal
                                                            distribution and is computationally efficient.
                                                        </div>
                                                    </div>
                                                </div>

                                                {/* Right: Return Distribution Histogram */}
                                                <div className={styles['methodology-card']}>
                                                    <div className={styles['methodology-header']}>
                                                        Return Distribution
                                                    </div>
                                                    <div className={styles['histogram-container']}>
                                                        <svg
                                                            className={styles['histogram-svg']}
                                                            viewBox="0 0 400 280"
                                                        >
                                                            {/* Grid lines */}
                                                            {[0, 25, 50, 75, 100].map((y) => (
                                                                <line
                                                                    key={`grid-${y}`}
                                                                    x1="40" y1={250 - (y / 100) * 200}
                                                                    x2="380" y2={250 - (y / 100) * 200}
                                                                    stroke="var(--pb-border)"
                                                                    strokeWidth="1"
                                                                    strokeDasharray="3,3"
                                                                />
                                                            ))}

                                                            <text x="15" y="255" fontSize="10"
                                                                  fill="var(--pb-text-secondary)"
                                                                  fontFamily="var(--font-mono)">0</text>
                                                            <text x="8" y="45" fontSize="10"
                                                                  fill="var(--pb-text-secondary)"
                                                                  fontFamily="var(--font-mono)">
                                                                {Math.max(...varData.returnDistribution.map(d => d.frequency))}
                                                            </text>

                                                            {/* Bars */}
                                                            {varData.returnDistribution.map((item, idx) => {
                                                                const maxFreq = Math.max(
                                                                    ...varData.returnDistribution.map(d => d.frequency)
                                                                );
                                                                const barHeight = maxFreq > 0
                                                                    ? (item.frequency / maxFreq) * 200 : 0;
                                                                const barX = 50 + (idx * 33);
                                                                return (
                                                                    <rect
                                                                        key={`bar-${idx}`}
                                                                        x={barX}
                                                                        y={250 - barHeight}
                                                                        width="28"
                                                                        height={barHeight}
                                                                        fill={item.isLoss ? '#EF4444' : '#3B82F6'}
                                                                        opacity={item.isLoss ? 0.7 : 0.5}
                                                                        stroke="var(--pb-border)"
                                                                        strokeWidth="0.5"
                                                                    />
                                                                );
                                                            })}

                                                            {/* VaR threshold line — sits at the 5th bucket boundary (0%) */}
                                                            <line
                                                                x1="175" y1="50" x2="175" y2="250"
                                                                stroke="#EF4444" strokeWidth="2"
                                                                strokeDasharray="4,4"
                                                            />
                                                            <text x="180" y="35" fontSize="10"
                                                                  fill="#EF4444"
                                                                  fontFamily="var(--font-mono)"
                                                                  fontWeight="500">VaR</text>

                                                            {/* Loss tail shading */}
                                                            <rect x="50" y="50" width="125" height="200"
                                                                  fill="#EF4444" opacity="0.08" />

                                                            {/* X-axis */}
                                                            <line x1="40" y1="250" x2="380" y2="250"
                                                                  stroke="var(--pb-border)" strokeWidth="1" />
                                                            <text x="45" y="270" fontSize="9"
                                                                  fill="var(--pb-text-secondary)"
                                                                  fontFamily="var(--font-mono)">-10%</text>
                                                            <text x="165" y="270" fontSize="9"
                                                                  fill="var(--pb-text-secondary)"
                                                                  fontFamily="var(--font-mono)">0%</text>
                                                            <text x="355" y="270" fontSize="9"
                                                                  fill="var(--pb-text-secondary)"
                                                                  fontFamily="var(--font-mono)">+10%</text>
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
                                                    {varData.assetBreakdown.map((row, idx) => (
                                                        <tr key={idx}>
                                                            <td className={styles['asset-name']}>
                                                                    <span className={styles['ticker']}>
                                                                        {row.ticker}
                                                                    </span>
                                                            </td>
                                                            <td className={styles['numeric']}>
                                                                {row.weight.toFixed(1)}%
                                                            </td>
                                                            <td className={styles['numeric']}>
                                                                -${Math.abs(row.individualVar).toLocaleString(
                                                                undefined,
                                                                { maximumFractionDigits: 0 }
                                                            )}
                                                            </td>
                                                            <td>
                                                                <div className={styles['contribution-cell']}>
                                                                    <div className={styles['contribution-bar-container']}>
                                                                        <div
                                                                            className={styles['contribution-bar']}
                                                                            style={{
                                                                                width: `${(Math.abs(row.contribution) / maxContribution) * 100}%`
                                                                            }}
                                                                        />
                                                                    </div>
                                                                    <span className={styles['contribution-value']}>
                                                                            -${Math.abs(row.contribution).toLocaleString(
                                                                        undefined,
                                                                        { maximumFractionDigits: 0 }
                                                                    )}
                                                                        </span>
                                                                </div>
                                                            </td>
                                                            <td className={styles['numeric']}>
                                                                {row.percentOfTotal.toFixed(1)}%
                                                            </td>
                                                        </tr>
                                                    ))}
                                                    </tbody>
                                                </table>
                                            </div>
                                        </section>

                                        {/* SECTION 4: HISTORICAL VaR SPARKLINE ── */}
                                        <section className={styles['historical-stress-section']}>
                                            <h2 className={styles['section-title']}>Historical VaR</h2>
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
                                        </section>

                                    </div>
                                )}
                            </DataBoundary>
                        )}
                    </DataBoundary>
                </main>
    );
};

export default VaRReportPage;