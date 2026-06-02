import { useState, useEffect, useCallback } from 'react';
import { portfolioService } from '@/services/portfolio';
import { AsyncState, createLoadingState, createSuccessState, createErrorState } from '@/types/shared';
import type {
    Portfolio, PortfolioSummary, Position,
    CompositionEntry, RiskAlert, VolatilityPoint,
} from '@/types/portfolio';

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
    const [portfolios, setPortfolios]     = useState<AsyncState<Portfolio[]>>(createLoadingState<Portfolio[]>());
    const [summary, setSummary]           = useState<AsyncState<PortfolioSummary>>(createLoadingState<PortfolioSummary>());
    const [positions, setPositions]       = useState<AsyncState<Position[]>>(createLoadingState<Position[]>());
    const [composition, setComposition]   = useState<AsyncState<CompositionEntry[]>>(createLoadingState<CompositionEntry[]>());
    const [volatility, setVolatility]     = useState<AsyncState<VolatilityPoint[]>>(createLoadingState<VolatilityPoint[]>());
    const [alerts, setAlerts]             = useState<AsyncState<RiskAlert[]>>(createLoadingState<RiskAlert[]>());

    const fetchAll = useCallback(async () => {
        // Reset to loading state
        setPortfolios(createLoadingState<Portfolio[]>());
        setSummary(createLoadingState<PortfolioSummary>());
        setPositions(createLoadingState<Position[]>());
        setComposition(createLoadingState<CompositionEntry[]>());
        setVolatility(createLoadingState<VolatilityPoint[]>());
        setAlerts(createLoadingState<RiskAlert[]>());

        try {
            // 1. Fetch the master list of portfolios
            const portfolioList = await portfolioService.getPortfolios();
            setPortfolios(createSuccessState(portfolioList));

            // 2. Guard: only fetch detail data if we have an ID
            if (portfolioId) {
                try {
                    const sumData = await portfolioService.getSummary(portfolioId, dateRange);
                    setSummary(createSuccessState(sumData));
                } catch (e: any) {
                    setSummary(createErrorState(e?.response?.data?.message ?? 'Failed to load summary'));
                }

                try {
                    const posData = await portfolioService.getPositions(portfolioId);
                    setPositions(createSuccessState(posData));
                } catch (e: any) {
                    setPositions(createErrorState(e?.response?.data?.message ?? 'Failed to load positions'));
                }

                try {
                    const compData = await portfolioService.getComposition(portfolioId);
                    setComposition(createSuccessState(compData));
                } catch (e: any) {
                    setComposition(createErrorState(e?.response?.data?.message ?? 'Failed to load composition'));
                }

                try {
                    const volData = await portfolioService.getVolatility(portfolioId, dateRange);
                    setVolatility(createSuccessState(volData));
                } catch (e: any) {
                    setVolatility(createErrorState(e?.response?.data?.message ?? 'Failed to load volatility'));
                }

                try {
                    const alertData = await portfolioService.getAlerts(portfolioId);
                    setAlerts(createSuccessState(alertData));
                } catch (e: any) {
                    setAlerts(createErrorState(e?.response?.data?.message ?? 'Failed to load alerts'));
                }
            }
        } catch (e: any) {
            const errorMsg = e?.response?.data?.message ?? 'Failed to load portfolios';
            setPortfolios(createErrorState(errorMsg));
        }
    }, [portfolioId, dateRange]);

    useEffect(() => {
        fetchAll();
    }, [fetchAll]);

    return { portfolios, summary, positions, composition, volatility, alerts, refetch: fetchAll };
}

export default usePortfolio;
