export interface RiskMetrics {
  beta: number;
  betaDelta: number;
  maxDrawdown: number;
  maxDrawdownDelta: number;
  concentrationIndex: number;
  concentrationDelta: number;
  maxDrawdownSparkline: number[];
  betaSparkline: number[];
  concentrationSparkline: number[];
}

export interface RiskAlert {
  id: string;
  message: string;
  severity: 'high' | 'moderate' | 'low';
  timestamp: string;
  affectedAssets: string[];
}

export interface CorrelationMatrix {
  assets: string[];
  matrix: number[][];
}

export interface CompositionEntry {
  label: string;
  value: number;
  color: string;
}

export interface VolatilityPoint {
  date: string;
  value: number;
}
