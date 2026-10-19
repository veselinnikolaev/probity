import { useState, useEffect, useCallback } from 'react';
import { portfolioService } from '@/services/portfolio';
import { AsyncState, createLoadingState, createSuccessState, createErrorState } from '@/types/shared';
import type { Portfolio } from '@/types/portfolio';

/**
 * Fetch all portfolios with full AsyncState management.
 * Enforces loading, error, and empty states.
 */
export function usePortfolios(): AsyncState<Portfolio[]> & { refetch: () => void } {
  const [state, setState] = useState<AsyncState<Portfolio[]>>(createLoadingState<Portfolio[]>());

  const fetch = useCallback(async () => {
    setState(createLoadingState<Portfolio[]>());

    try {
      const portfolios = await portfolioService.getPortfolios();
      setState(createSuccessState(portfolios));
    } catch (e: any) {
      const errorMsg = e?.response?.data?.message ?? 'Failed to load portfolios';
      setState(createErrorState(errorMsg));
    }
  }, []);

  useEffect(() => {
    fetch();
  }, [fetch]);

  return { ...state, refetch: fetch };
}

export default usePortfolios;
