export interface Portfolio {
    id: string;
    name: string;
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

export interface CompositionEntry {
    label: string;
    value: number;
    color: string;
}

export interface VolatilityPoint {
    date: string;
    value: number;
}

export interface RiskAlert {
    id: string;
    message: string;
    severity: 'high' | 'moderate' | 'low';
    timestamp: string;
    affectedAssets: string[];
}

export interface CreatePortfolioRequest {
    name: string;
    description?: string;
}

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

export interface CorrelationMatrix {
    assets: string[];
    matrix: number[][];
}

export interface AssetSearchResult {
    ticker: string;
    name: string;
    type: 'STOCK' | 'ETF' | 'CRYPTO' | 'BOND';
    sector: string;
    currentPrice: number | null;
}