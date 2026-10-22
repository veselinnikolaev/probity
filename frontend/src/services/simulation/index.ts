import api from '@/services/api';
import { SampledPaths, SimulationRequest, SimulationResult } from '@/types/simulation';

/** Default sample size the UI asks the paths endpoint for (Q9b; backend default is 100). */
export const DEFAULT_PATHS_SAMPLE_SIZE = 100;

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
 * Fetch a previously run simulation result by its ID.
 * GET /api/v1/simulations/{id}
 */
export async function fetchSimulation(simulationId: string): Promise<SimulationResult | null> {
  try {
    const response = await api.get<SimulationResult>(`/simulations/${simulationId}`);
    return response.data;
  } catch (error: any) {
    if (error?.response?.status === 404) return null;
    throw error;
  }
}

/**
 * List all simulations for a portfolio, most recent first.
 * GET /api/v1/simulations?portfolioId={portfolioId}
 */
export async function listSimulations(portfolioId: string): Promise<SimulationResult[]> {
  const response = await api.get<SimulationResult[]>('/simulations', {
    params: { portfolioId },
  });
  return response.data;
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
