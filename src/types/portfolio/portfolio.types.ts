export interface Portfolio {
  id: string;
  name: string;
  description?: string;
  totalValue: number;
  createdAt: string;
  positionsCount: number;
  volatility: number;
  sharpeRatio: number;
  sparklineData: number[];
}

export interface PortfolioSummary {
  totalValue: number;
  totalValueDelta: number;
  dailyReturn: number;
  dailyReturnDelta: number;
  volatility: number;
  volatilityDelta: number;
  sharpeRatio: number;
  sharpeRatioDelta: number;
  var95: number;
  var95Delta: number;
  sparklines: {
    totalValue: number[];
    dailyReturn: number[];
    volatility: number[];
    sharpeRatio: number[];
    var95: number[];
  };
}

export interface CreatePortfolioRequest {
  name: string;
  description?: string;
}
