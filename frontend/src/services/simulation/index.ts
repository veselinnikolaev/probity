import api from '@/services/api';
import {
  SampledPaths,
  SimulationRequest,
  SimulationResult,
  SimulationStatus,
} from '@/types/simulation';

/** Default sample size the UI asks the paths endpoint for (Q9b; backend default is 100). */
export const DEFAULT_PATHS_SAMPLE_SIZE = 100;

/** Poll interval while waiting for an async simulation to finish (M4a). */
export const SIMULATION_POLL_INTERVAL_MS = 1500;

/** Give up waiting for an async simulation after this long (M4a). */
export const SIMULATION_POLL_TIMEOUT_MS = 120_000;

/**
 * Run a new Monte Carlo simulation against the backend.
 * POST /api/v1/simulations/run
 *
 * The backend derives μ and σ from real market data when
 * assumedReturnPercent / assumedVolatilityPercent are omitted.
 */
export async function runSimulation(request: SimulationRequest): Promise<SimulationResult> {
  const payload = {
    ...request,
    confidenceLevel: request.confidenceLevel / 100,  // 95 → 0.95
  };
  const response = await api.post<SimulationResult>('/simulations/run', payload);
  return response.data;
}

/**
 * Start an asynchronous Monte Carlo simulation.
 * POST /api/v1/simulations/run-async → 202 Accepted with a status envelope.
 *
 * M4a: the sync endpoint blocks a request thread for the whole run; the async
 * path returns immediately (PENDING) and a worker completes it, so the page can
 * show progress and survive long runs. The caller then polls `fetchSimulationStatus`.
 */
export async function runSimulationAsync(
  request: SimulationRequest,
  signal?: AbortSignal,
): Promise<SimulationStatus> {
  const payload = {
    ...request,
    confidenceLevel: request.confidenceLevel / 100,  // 95 → 0.95
  };
  const response = await api.post<SimulationStatus>('/simulations/run-async', payload, { signal });
  return response.data;
}

/**
 * Fetch the current status of an async simulation.
 * GET /api/v1/simulations/{id}/status
 */
export async function fetchSimulationStatus(
  simulationId: string,
  signal?: AbortSignal,
): Promise<SimulationStatus> {
  const response = await api.get<SimulationStatus>(`/simulations/${simulationId}/status`, { signal });
  return response.data;
}

export interface WaitForSimulationOptions {
  /** Milliseconds between status polls. */
  intervalMs?: number;
  /** Abort waiting (and the in-flight requests) after this many milliseconds. */
  timeoutMs?: number;
  /** Caller-owned cancellation, e.g. component unmount or a new run. */
  signal?: AbortSignal;
}

/** Map a COMPLETED status envelope onto the SimulationResult the rest of the app consumes. */
function statusToResult(status: SimulationStatus): SimulationResult {
  const r = status.result;
  if (!r) {
    throw new Error('Simulation completed without a result payload');
  }
  return {
    id: status.id,
    portfolioId: status.portfolioId,
    createdAt: status.createdAt,
    parameters: r.parameters,
    currentPortfolioValue: r.currentPortfolioValue,
    percentileSeries: r.percentileSeries,
    statistics: r.statistics,
    outcomes: r.outcomes,
    distribution: r.distribution,
  };
}

/**
 * Poll GET /{id}/status until the simulation reaches a terminal state (M4a).
 *
 * Resolves with the finished result on COMPLETED, rejects on FAILED, on timeout, and
 * as soon as `signal` aborts (the in-flight request is aborted too, so late responses
 * never land after a cancel).
 */
export async function pollSimulationUntilDone(
  simulationId: string,
  options: WaitForSimulationOptions = {},
): Promise<SimulationResult> {
  const intervalMs = options.intervalMs ?? SIMULATION_POLL_INTERVAL_MS;
  const timeoutMs = options.timeoutMs ?? SIMULATION_POLL_TIMEOUT_MS;
  const deadline = Date.now() + timeoutMs;

  const abortFromTimeout = new AbortController();
  const timer = setTimeout(() => abortFromTimeout.abort(), timeoutMs);
  const onExternalAbort = () => abortFromTimeout.abort();
  options.signal?.addEventListener('abort', onExternalAbort);

  try {
    for (;;) {
      if (options.signal?.aborted) throw abortError();
      const status = await fetchSimulationStatus(simulationId, abortFromTimeout.signal);
      if (status.status === 'COMPLETED') return statusToResult(status);
      if (status.status === 'FAILED') throw new Error('Simulation failed');
      if (Date.now() >= deadline) throw new Error('Simulation timed out');
      await delay(intervalMs, abortFromTimeout.signal);
    }
  } catch (error) {
    // Aborting the internal controller is how both cancellation and the deadline stop
    // an in-flight request. A caller cancel is an AbortError; a deadline hit is a
    // timeout — never surface the internal AbortError as a cancel the caller never did.
    if (isAbortError(error)) {
      if (options.signal?.aborted) throw abortError();
      throw new Error('Simulation timed out');
    }
    throw error;
  } finally {
    clearTimeout(timer);
    options.signal?.removeEventListener('abort', onExternalAbort);
  }
}

function abortError(): DOMException {
  return new DOMException('Aborted', 'AbortError');
}

function isAbortError(error: unknown): boolean {
  // A real axios cancellation rejects with CanceledError (name 'CanceledError'); a
  // plain AbortController consumer sees 'AbortError'. Treat both as a cancellation.
  if (error instanceof DOMException) return error.name === 'AbortError';
  const name = (error as { name?: string } | null)?.name;
  const code = (error as { code?: string } | null)?.code;
  return name === 'AbortError' || name === 'CanceledError' || code === 'ERR_CANCELED';
}

/** Run an async simulation and wait for its result (M4a). */
export async function runSimulationAndWait(
  request: SimulationRequest,
  options: WaitForSimulationOptions = {},
): Promise<SimulationResult> {
  const started = await runSimulationAsync(request, options.signal);
  return pollSimulationUntilDone(started.id, options);
}

/** Sleep that rejects as soon as `signal` aborts, so a cancel never waits out the interval. */
function delay(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(abortError());
      return;
    }
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort);
      resolve();
    }, ms);
    const onAbort = () => {
      clearTimeout(timer);
      reject(abortError());
    };
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

/**
 * Fetch a bounded, deterministic sample of a simulation's paths.
 * GET /api/v1/simulations/{id}/paths?n={n}
 *
 * Q9b: the result/status/list bodies no longer carry paths, so a chart draws
 * this sample. The backend always includes the global min/max paths, so a
 * y-range derived from the sample is never clipped.
 */
export async function fetchSimulationPaths(
  simulationId: string,
  n: number = DEFAULT_PATHS_SAMPLE_SIZE,
): Promise<SampledPaths> {
  const response = await api.get<SampledPaths>(`/simulations/${simulationId}/paths`, {
    params: { n },
  });
  return response.data;
}
