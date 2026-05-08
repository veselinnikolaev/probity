/**
 * Monte Carlo Simulation Types
 * Structured for backend integration
 */

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
  allPaths: SimulatedPath[];
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
