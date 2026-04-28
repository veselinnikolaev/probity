import { useState, useEffect, useCallback } from 'react';
import { portfolioService } from '../api/portfolioService';
import { AsyncState } from '../types/async-state';
import type {
  PortfolioSummary,
  Position,
  CompositionEntry,
  RiskAlert,
  VolatilityPoint
} from '../types/portfolio';

export interface PortfolioDetail {
  summary: PortfolioSummary;
  positions: Position[];
  composition: CompositionEntry[];
  volatility: VolatilityPoint[];
  alerts: RiskAlert[];
}

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
      const [summary, positions, composition, volatility, alerts] = await Promise.all([
        portfolioService.getSummary(portfolioId, dateRange),
        portfolioService.getPositions(portfolioId),
        portfolioService.getComposition(portfolioId),
        portfolioService.getVolatility(portfolioId, dateRange),
        portfolioService.getAlerts(portfolioId),
      ]);
      setState({
        data: { summary, positions, composition, volatility, alerts },
        isLoading: false,
        error: null,
      });
    } catch (e: any) {
      setState({
        isLoading: false,
        error: e?.response?.data?.message ?? 'Failed to load portfolio details',
      });
    }
  }, [portfolioId, dateRange]);

  useEffect(() => { fetch(); }, [fetch]);
  return { ...state, refetch: fetch };
}