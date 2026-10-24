import { useState, useCallback, useEffect, useRef } from 'react';
import { runSimulationAndWait } from '@/services/simulation';
import { SimulationRequest, SimulationAsyncState } from '@/types/simulation';

/**
 * Run Monte Carlo simulations against the async backend endpoints (M4a).
 *
 * `run` starts the simulation (202) and polls its status until it finishes; the
 * promise settles with the completed result, rejects on FAILED/timeout, and is
 * abandoned mid-flight if the hook unmounts or a second run starts — in both
 * cases the in-flight requests are aborted so a stale response can never land
 * after a newer one.
 */
export function useSimulation(portfolioId: string) {
    const [state, setState] = useState<SimulationAsyncState>({ status: 'idle', data: null });
    const [lastRequest, setLastRequest] = useState<SimulationRequest | null>(null);
    // Aborts the run currently in flight; replaced on every run and on unmount.
    const inFlight = useRef<AbortController | null>(null);

    const cancelInFlight = useCallback(() => {
        inFlight.current?.abort();
        inFlight.current = null;
    }, []);

    // Reset when the user switches portfolios so stale results never show.
    useEffect(() => {
        setState({ status: 'idle', data: null });
        setLastRequest(null);
    }, [portfolioId]);

    // Abort any polling when the hook unmounts.
    useEffect(() => cancelInFlight, [cancelInFlight]);

    const run = useCallback(async (request: SimulationRequest) => {
        // A new run supersedes whatever was in flight; cancelling also means the
        // superseded promise rejects with AbortError and must not touch state.
        cancelInFlight();
        const controller = new AbortController();
        inFlight.current = controller;

        setState({ status: 'loading', data: null });
        setLastRequest(request);
        try {
            const result = await runSimulationAndWait(request, { signal: controller.signal });
            if (controller.signal.aborted) return;
            setState({ status: 'success', data: result });
            return result;
        } catch (error) {
            if (controller.signal.aborted) return;
            const message = error instanceof Error ? error.message : 'Failed to run simulation';
            setState({ status: 'error', data: null, error: message });
        } finally {
            if (inFlight.current === controller) inFlight.current = null;
        }
    }, [cancelInFlight]);

    const refetch = useCallback(() => {
        if (lastRequest) run(lastRequest);
    }, [lastRequest, run]);

    return { state, run, refetch };
}
