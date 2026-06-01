import { useState, useEffect, useCallback } from 'react';
import { portfolioService } from '../api/portfolioService';
import { AsyncState } from '../types/async-state';
import type {
    Portfolio, PortfolioSummary, Position,
    CompositionEntry, RiskAlert, VolatilityPoint,
} from '../types/portfolio';

/**
 * @deprecated Use `usePortfolios()` and `usePortfolioDetail()` instead.
 * This hook is kept for backward compatibility but should be migrated to the new AsyncState pattern.
 */
interface PortfolioData {
    portfolios: AsyncState<Portfolio[]>;
    summary: AsyncState<PortfolioSummary>;
    positions: AsyncState<Position[]>;
    composition: AsyncState<CompositionEntry[]>;
    volatility: AsyncState<VolatilityPoint[]>;
    alerts: AsyncState<RiskAlert[]>;
    refetch: () => void;
}

export function usePortfolio(portfolioId: string, dateRange: string): PortfolioData {
    const [portfolios, setPortfolios]     = useState<AsyncState<Portfolio[]>>({ isLoading: true });
    const [summary, setSummary]           = useState<AsyncState<PortfolioSummary>>({ isLoading: true });
    const [positions, setPositions]       = useState<AsyncState<Position[]>>({ isLoading: true });
    const [composition, setComposition]   = useState<AsyncState<CompositionEntry[]>>({ isLoading: true });
    const [volatility, setVolatility]     = useState<AsyncState<VolatilityPoint[]>>({ isLoading: true });
    const [alerts, setAlerts]             = useState<AsyncState<RiskAlert[]>>({ isLoading: true });

    const fetchAll = useCallback(async () => {
        // Reset to loading state
        setPortfolios({ isLoading: true });
        setSummary({ isLoading: true });
        setPositions({ isLoading: true });
        setComposition({ isLoading: true });
        setVolatility({ isLoading: true });
        setAlerts({ isLoading: true });

        try {
            // 1. Fetch the master list of portfolios
            const portfolioList = await portfolioService.getPortfolios();
            setPortfolios({ data: portfolioList, isLoading: false, error: null });

            // 2. Guard: only fetch detail data if we have an ID
            if (portfolioId) {
                try {
                    const sumData = await portfolioService.getSummary(portfolioId, dateRange);
                    setSummary({ data: sumData, isLoading: false, error: null });
                } catch (e: any) {
                    setSummary({
                        isLoading: false,
                        error: e?.response?.data?.message ?? 'Failed to load summary',
                    });
                }

                try {
                    const posData = await portfolioService.getPositions(portfolioId);
                    setPositions({ data: posData, isLoading: false, error: null });
                } catch (e: any) {
                    setPositions({
                        isLoading: false,
                        error: e?.response?.data?.message ?? 'Failed to load positions',
                    });
                }

                try {
                    const compData = await portfolioService.getComposition(portfolioId);
                    setComposition({ data: compData, isLoading: false, error: null });
                } catch (e: any) {
                    setComposition({
                        isLoading: false,
                        error: e?.response?.data?.message ?? 'Failed to load composition',
                    });
                }

                try {
                    const volData = await portfolioService.getVolatility(portfolioId, dateRange);
                    setVolatility({ data: volData, isLoading: false, error: null });
                } catch (e: any) {
                    setVolatility({
                        isLoading: false,
                        error: e?.response?.data?.message ?? 'Failed to load volatility',
                    });
                }

                try {
                    const alertData = await portfolioService.getAlerts(portfolioId);
                    setAlerts({ data: alertData, isLoading: false, error: null });
                } catch (e: any) {
                    setAlerts({
                        isLoading: false,
                        error: e?.response?.data?.message ?? 'Failed to load alerts',
                    });
                }
            }
        } catch (e: any) {
            const errorMsg = e?.response?.data?.message ?? 'Failed to load portfolios';
            setPortfolios({ isLoading: false, error: errorMsg });
        }
    }, [portfolioId, dateRange]);

    useEffect(() => {
        fetchAll();
    }, [fetchAll]);

    return { portfolios, summary, positions, composition, volatility, alerts, refetch: fetchAll };
}

export default usePortfolio;
