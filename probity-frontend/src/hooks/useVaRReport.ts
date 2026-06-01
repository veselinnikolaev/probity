import { useState, useEffect, useCallback } from 'react';
import { portfolioService } from '../api/portfolioService';
import { AsyncState } from '../types/async-state';
import type { VaRReport } from '../types/portfolio';

export function useVaRReport(
    portfolioId: string,
    confidenceLevel: number,
    timeHorizonDays: number,
): AsyncState<VaRReport> & { refetch: () => void } {
    const [state, setState] = useState<AsyncState<VaRReport>>({
        isLoading: true, error: null,
    });

    const fetch = useCallback(async () => {
        if (!portfolioId) { setState({ isLoading: false, error: null }); return; }
        setState({ isLoading: true, error: null });
        try {
            const data = await portfolioService.getVaRReport(
                portfolioId, confidenceLevel, timeHorizonDays,
            );
            setState({ data, isLoading: false, error: null });
        } catch (e: any) {
            setState({
                isLoading: false,
                error: e?.response?.data?.message ?? 'Failed to load VaR report',
            });
        }
    }, [portfolioId, confidenceLevel, timeHorizonDays]);

    useEffect(() => { fetch(); }, [fetch]);
    return { ...state, refetch: fetch };
}