import { useState, useEffect, useCallback } from 'react';
import { portfolioService } from '../api/portfolioService';
import { AsyncState } from '../types/async-state';
import type { Portfolio } from '../types/portfolio';

/**
 * Fetch all portfolios with full AsyncState management.
 * Enforces loading, error, and empty states.
 */
export function usePortfolios(): AsyncState<Portfolio[]> & { refetch: () => void } {
  const [state, setState] = useState<AsyncState<Portfolio[]>>({
    isLoading: true,
    error: null,
  });

  const fetch = useCallback(async () => {
    setState({ isLoading: true, error: null });

    try {
      const portfolios = await portfolioService.getPortfolios();
      setState({ data: portfolios, isLoading: false, error: null });
    } catch (e: any) {
      const errorMsg = e?.response?.data?.message ?? 'Failed to load portfolios';
      setState({ isLoading: false, error: errorMsg });
    }
  }, []);

  useEffect(() => {
    fetch();
  }, [fetch]);

  return { ...state, refetch: fetch };
}

export default usePortfolios;
