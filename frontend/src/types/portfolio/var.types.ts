export interface VaRMethodology {
  formula: string;
  portfolioVolatility: number;
  zscore: number;
  expectedReturn: number;
}

export interface VaRReport {
  valueAtRisk: number;          // negative, e.g. -4820
  confidenceLevel: number;      // 0.90 | 0.95 | 0.99
  timeHorizonDays: number;      // 1 | 5 | 21
  methodology: VaRMethodology;
  historicalSparkline: number[];
  assetBreakdown: AssetVaR[];
  stressEvents: StressEvent[];
  returnDistribution: ReturnBucket[];
}

export interface AssetVaR {
  ticker: string;
  weight: number;
  individualVar: number;
  contribution: number;
  percentOfTotal: number;
}

export interface StressEvent {
  date: string;
  label: string;
  var: number;
}

export interface ReturnBucket {
  range: string;
  frequency: number;
  isLoss: boolean;
}
