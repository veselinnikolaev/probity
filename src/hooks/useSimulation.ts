import { useState, useCallback, useEffect } from 'react';
import { runSimulation } from '../api/simulationService';
import { SimulationRequest, SimulationAsyncState } from '../types/simulation';

export function useSimulation(portfolioId: string) {
    const [state, setState] = useState<SimulationAsyncState>({ status: 'idle', data: null });
    const [lastRequest, setLastRequest] = useState<SimulationRequest | null>(null);

    // Reset when the user switches portfolios so stale results never show
    useEffect(() => {
        setState({ status: 'idle', data: null });
        setLastRequest(null);
    }, [portfolioId]);

    const run = useCallback(async (request: SimulationRequest) => {
        setState({ status: 'loading', data: null });
        setLastRequest(request);
        try {
            const result = await runSimulation(request);
            setState({ status: 'success', data: result });
            return result;
        } catch (error) {
            const message = error instanceof Error ? error.message : 'Failed to run simulation';
            setState({ status: 'error', data: null, error: message });
        }
    }, []);

    const refetch = useCallback(() => {
        if (lastRequest) run(lastRequest);
    }, [lastRequest, run]);

    return { state, run, refetch };
}