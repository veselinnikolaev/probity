export interface Position {
  id: string;
  ticker: string;
  name: string;
  assetType: 'STOCK' | 'ETF' | 'CRYPTO' | 'BOND';
  // Market data
  price: number;
  change: number;
  changePercent: number;
  // Risk
  riskScore: number;
  riskLevel: 'LOW' | 'MODERATE' | 'HIGH';
  // Formatted strings (used in dashboard DataTable)
  volume: string;
  positionValue: string;
  sector: string;
  // Required computed fields (now always returned by backend)
  quantity: number;
  avgBuyPrice: number;
  currentPrice: number;
  positionValueRaw: number;
  weight: number;
  volatility: number;
  volatilityContribution: number;
  // Optional (only on risk analysis page)
  dailyReturn?: number;
  riskContribution?: number;
}

export interface AssetSearchResult {
  ticker: string;
  name: string;
  type: 'STOCK' | 'ETF' | 'CRYPTO' | 'BOND';
  sector: string;
  currentPrice: number | null;
}
