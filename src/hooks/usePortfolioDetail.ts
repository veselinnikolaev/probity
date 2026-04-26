import { useState, useEffect, useCallback } from 'react';
import { portfolioService } from '../api/portfolioService';
import { AsyncState } from '../types/async-state';
import type { PortfolioSummary, Position, CompositionEntry, RiskAlert, VolatilityPoint, RiskMetrics } from '../types/portfolio';

/**
 * Combined portfolio detail data for a specific portfolio.
 * All fields are required when data is present.
 */
export interface PortfolioDetail {
  summary: PortfolioSummary;
  positions: Position[];
  composition: CompositionEntry[];
  volatility: VolatilityPoint[];
  alerts: RiskAlert[];
  riskMetrics: RiskMetrics;
}

/**
 * Fetch all portfolio detail data for a specific portfolio with full AsyncState management.
 * Enforces loading, error, and empty states for all related data.
 */
export function usePortfolioDetail(
  portfolioId: string,
  dateRange: string
): AsyncState<PortfolioDetail> & { refetch: () => void } {
  const [state, setState] = useState<AsyncState<PortfolioDetail>>({
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
      const [summary, positions, composition, volatility, alerts, riskMetrics] = await Promise.all([
        portfolioService.getSummary(portfolioId, dateRange),
        portfolioService.getPositions(portfolioId),
        portfolioService.getComposition(portfolioId),
        portfolioService.getVolatility(portfolioId, dateRange),
        portfolioService.getAlerts(portfolioId),
        portfolioService.getRiskMetrics(portfolioId, dateRange),
      ]);

      setState({
        data: { summary, positions, composition, volatility, alerts, riskMetrics },
        isLoading: false,
        error: null,
      });
    } catch (e: any) {
      const errorMsg = e?.response?.data?.message ?? 'Failed to load portfolio details';
      setState({ isLoading: false, error: errorMsg });
    }
  }, [portfolioId, dateRange]);

  useEffect(() => {
    fetch();
  }, [fetch]);

  return { ...state, refetch: fetch };
}

export default usePortfolioDetail;
