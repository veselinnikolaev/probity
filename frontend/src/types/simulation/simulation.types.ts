// ── Request / Input ──────────────────────────────────────────────────────────
export interface SimulationRequest {
  portfolioId: string;
  numberOfSimulations: number;
  timeHorizonDays: number;
  confidenceLevel: number;
  assumedReturnPercent?: number;
  assumedVolatilityPercent?: number;
}

// ── Core Simulation Data ─────────────────────────────────────────────────────
export interface PortfolioPath {
  dayIndex: number;
  value: number;
}

export interface SimulatedPath {
  pathId: number;
  values: PortfolioPath[];
}

export interface PercentileSeries {
  percentile: number; // 5, 10, 25, 50, 75, 90, 95
  values: PortfolioPath[];
  finalValue: number;
}

// ── Distribution Data ───────────────────────────────────────────────────────
export interface DistributionBucket {
  rangeMin: number;
  rangeMax: number;
  count: number;
  percentage: number;
}

// ── Simulation Result ───────────────────────────────────────────────────────
// Q9b: the result body no longer carries the per-path array. It is summary +
// percentile bands + distribution; the paths come from the sampled endpoint.
export interface SimulationResult {
  id: string;
  portfolioId: string;
  createdAt: string;
  parameters: {
    numberOfSimulations: number;
    timeHorizonDays: number;
    confidenceLevel: number;
  };
  currentPortfolioValue: number;
  percentileSeries: PercentileSeries[];
  statistics: {
    expectedFinalValue: number;
    medianFinalValue: number;
    stdDeviation: number;
    minValue: number;
    maxValue: number;
  };
  outcomes: {
    probabilityOf10PercentLoss: number;
    probabilityOf20PercentLoss: number;
    valueAtRisk95: number;
    conditionalValueAtRisk95: number;
  };
  distribution: DistributionBucket[];
}

// ── Sampled Paths ───────────────────────────────────────────────────────────
// Response of GET /api/v1/simulations/{id}/paths?n=N (Q9b).
export interface SampledPaths {
  id: string;
  /** Number of paths the simulation stored, before sampling. */
  totalPaths: number;
  /** Number of paths in `paths` — the actual sample size. */
  sampleSize: number;
  /** Deterministic sample that always includes the global min/max paths. */
  paths: SimulatedPath[];
}

// ── Async Status ─────────────────────────────────────────────────────────────
// Response of POST /api/v1/simulations/run-async (202) and
// GET /api/v1/simulations/{id}/status. Mirrors the backend SimulationStatus enum.
export type SimulationStatusName = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

/**
 * Status payload for an async simulation. `result` is only populated by the backend
 * once `status === 'COMPLETED'`, and its shape matches SimulationResult minus the
 * identity fields the status envelope already carries.
 */
export interface SimulationStatus {
  id: string;
  portfolioId: string;
  status: SimulationStatusName;
  createdAt: string;
  updatedAt: string;
  result?: SimulationResultStatusPayload | null;
}

export interface SimulationResultStatusPayload {
  currentPortfolioValue: number;
  parameters: {
    numberOfSimulations: number;
    timeHorizonDays: number;
    confidenceLevel: number;
  };
  statistics: {
    expectedFinalValue: number;
    medianFinalValue: number;
    stdDeviation: number;
    minValue: number;
    maxValue: number;
  };
  outcomes: {
    probabilityOf10PercentLoss: number;
    probabilityOf20PercentLoss: number;
    valueAtRisk95: number;
    conditionalValueAtRisk95: number;
  };
  percentileSeries: PercentileSeries[];
  distribution: DistributionBucket[];
}

// ── API Responses ───────────────────────────────────────────────────────────
export interface SimulationResponse {
  success: boolean;
  data?: SimulationResult;
  error?: {
    code: string;
    message: string;
  };
}

// ── Async State ──────────────────────────────────────────────────────────────
export type SimulationAsyncState =
  | { status: 'idle'; data: null }
  | { status: 'loading'; data: null }
  | { status: 'success'; data: SimulationResult }
  | { status: 'error'; data: null; error: string };
