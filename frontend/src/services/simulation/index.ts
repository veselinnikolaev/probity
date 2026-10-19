import api from '@/services/api';
import { SimulationRequest, SimulationResult } from '@/types/simulation';

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
