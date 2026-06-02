import { useState, useEffect, useCallback } from 'react';
import { portfolioService } from '@/services/portfolio';
import { AsyncState, createLoadingState, createSuccessState, createErrorState } from '@/types/shared';
import type {
    PortfolioSummary,
    Position,
    RiskMetrics,
    RiskAlert,
    CorrelationMatrix
} from '@/types/portfolio';

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
    const [state, setState] = useState<AsyncState<PortfolioRiskDetail>>(createLoadingState<PortfolioRiskDetail>());

    const fetch = useCallback(async () => {
        if (!portfolioId) {
            setState(createLoadingState<PortfolioRiskDetail>());
            return;
        }
        setState(createLoadingState<PortfolioRiskDetail>());
        try {
            const [summary, positions, alerts, riskMetrics, correlation] = await Promise.all([
                portfolioService.getSummary(portfolioId, dateRange),
                portfolioService.getPositions(portfolioId),
                portfolioService.getAlerts(portfolioId),
                portfolioService.getRiskMetrics(portfolioId, dateRange),
                portfolioService.getCorrelation(portfolioId, dateRange),
            ]);
            setState(createSuccessState({ summary, positions, alerts, riskMetrics, correlation }));
        } catch (e: any) {
            setState(createErrorState(e?.response?.data?.message ?? 'Failed to load risk data'));
        }
    }, [portfolioId, dateRange]);

    useEffect(() => { fetch(); }, [fetch]);
    return { ...state, refetch: fetch };
}
