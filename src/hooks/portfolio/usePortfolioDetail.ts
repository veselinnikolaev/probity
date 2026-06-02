import { useState, useEffect, useCallback } from 'react';
import { portfolioService } from '@/services/portfolio';
import { AsyncState, createLoadingState, createSuccessState, createErrorState } from '@/types/shared';
import type {
  PortfolioSummary,
  Position,
  CompositionEntry,
  RiskAlert,
  VolatilityPoint
} from '@/types/portfolio';

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
  const [state, setState] = useState<AsyncState<PortfolioDetail>>(createLoadingState<PortfolioDetail>());

  const fetch = useCallback(async () => {
    if (!portfolioId) {
      setState(createLoadingState<PortfolioDetail>());
      return;
    }
    setState(createLoadingState<PortfolioDetail>());
    try {
      const [summary, positions, composition, volatility, alerts] = await Promise.all([
        portfolioService.getSummary(portfolioId, dateRange),
        portfolioService.getPositions(portfolioId),
        portfolioService.getComposition(portfolioId),
        portfolioService.getVolatility(portfolioId, dateRange),
        portfolioService.getAlerts(portfolioId),
      ]);
      setState(createSuccessState({ summary, positions, composition, volatility, alerts }));
    } catch (e: any) {
      setState(createErrorState(e?.response?.data?.message ?? 'Failed to load portfolio details'));
    }
  }, [portfolioId, dateRange]);

  useEffect(() => { fetch(); }, [fetch]);
  return { ...state, refetch: fetch };
}
