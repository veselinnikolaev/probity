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
    price: number;
    change: number;
    changePercent: number;
    riskScore: number;
    riskLevel: 'LOW' | 'MODERATE' | 'HIGH';
    volume: string;
    marketCap: string;
    sector: string;       // for donut drill-down
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
    icon: string;
}
