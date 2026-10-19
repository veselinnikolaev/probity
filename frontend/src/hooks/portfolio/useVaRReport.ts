import { useState, useEffect, useCallback } from 'react';
import { portfolioService } from '@/services/portfolio';
import { AsyncState, createLoadingState, createSuccessState, createErrorState } from '@/types/shared';
import type { VaRReport } from '@/types/portfolio';

export function useVaRReport(
    portfolioId: string,
    confidenceLevel: number,
    timeHorizonDays: number,
): AsyncState<VaRReport> & { refetch: () => void } {
    const [state, setState] = useState<AsyncState<VaRReport>>(createLoadingState<VaRReport>());

    const fetch = useCallback(async () => {
        if (!portfolioId) { setState(createLoadingState<VaRReport>()); return; }
        setState(createLoadingState<VaRReport>());
        try {
            const data = await portfolioService.getVaRReport(
                portfolioId, confidenceLevel, timeHorizonDays,
            );
            setState(createSuccessState(data));
        } catch (e: any) {
            setState(createErrorState(e?.response?.data?.message ?? 'Failed to load VaR report'));
        }
    }, [portfolioId, confidenceLevel, timeHorizonDays]);

    useEffect(() => { fetch(); }, [fetch]);
    return { ...state, refetch: fetch };
}
