import { useState, useEffect, useCallback } from 'react';
import { portfolioService } from '../api/portfolioService';
import { AsyncState } from '../types/async-state';
import type {
    PortfolioSummary,
    Position,
    RiskMetrics,
    RiskAlert,
    CorrelationMatrix
} from '../types/portfolio';

export interface PortfolioRiskDetail {
    summary: PortfolioSummary;
    positions: Position[];
    alerts: RiskAlert[];
    riskMetrics: RiskMetrics;
    correlation: CorrelationMatrix;
}

export function usePortfolioRiskDetail(
    portfolioId: string,
    dateRange: string
): AsyncState<PortfolioRiskDetail> & { refetch: () => void } {
    const [state, setState] = useState<AsyncState<PortfolioRiskDetail>>({
        isLoading: true,
        error: null,
    });

    const fetch = useCallback(async () => {
        if (!portfolioId) {
            setState({ isLoading: false, error: null });
            return;
        }
        setState({ isLoading: true, error: null });
        try {
            const [summary, positions, alerts, riskMetrics, correlation] = await Promise.all([
                portfolioService.getSummary(portfolioId, dateRange),
                portfolioService.getPositions(portfolioId),
                portfolioService.getAlerts(portfolioId),
                portfolioService.getRiskMetrics(portfolioId, dateRange),
                portfolioService.getCorrelation(portfolioId, dateRange),
            ]);
            setState({
                data: { summary, positions, alerts, riskMetrics, correlation },
                isLoading: false,
                error: null,
            });
        } catch (e: any) {
            setState({
                isLoading: false,
                error: e?.response?.data?.message ?? 'Failed to load risk data',
            });
        }
    }, [portfolioId, dateRange]);

    useEffect(() => { fetch(); }, [fetch]);
    return { ...state, refetch: fetch };
}