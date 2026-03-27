import { useState, useEffect, useCallback } from 'react';
import { portfolioApi } from '../api/portfolioApi';
import type {
    Portfolio, PortfolioSummary, Position,
    CompositionEntry, RiskAlert,
} from '../types/portfolio';

interface PortfolioData {
    portfolios: Portfolio[];
    summary: PortfolioSummary | null;
    positions: Position[];
    composition: CompositionEntry[];
    volatility: number[];          // unwrapped .value[] for LineChart
    alerts: RiskAlert[];
    loading: boolean;
    error: string | null;
    refetch: () => void;
}

export function usePortfolio(portfolioId: string, dateRange: string): PortfolioData {
    const [portfolios, setPortfolios]     = useState<Portfolio[]>([]);
    const [summary, setSummary]           = useState<PortfolioSummary | null>(null);
    const [positions, setPositions]       = useState<Position[]>([]);
    const [composition, setComposition]   = useState<CompositionEntry[]>([]);
    const [volatility, setVolatility]     = useState<number[]>([]);
    const [alerts, setAlerts]             = useState<RiskAlert[]>([]);
    const [loading, setLoading]           = useState(true);
    const [error, setError]               = useState<string | null>(null);

    const fetchAll = useCallback(async () => {
        if (!portfolioId) return;
        setLoading(true);
        setError(null);
        try {
            const [lists, sum, pos, comp, vol, alrt] = await Promise.all([
                portfolioApi.getPortfolios(),
                portfolioApi.getSummary(portfolioId, dateRange),
                portfolioApi.getPositions(portfolioId),
                portfolioApi.getComposition(portfolioId),
                portfolioApi.getVolatility(portfolioId, dateRange),
                portfolioApi.getAlerts(portfolioId),
            ]);
            setPortfolios(lists);
            setSummary(sum);
            setPositions(pos);
            setComposition(comp);
            setVolatility(vol.map(p => p.value));
            setAlerts(alrt);
        } catch (e: any) {
            setError(e?.response?.data?.message ?? 'Failed to load portfolio data');
        } finally {
            setLoading(false);
        }
    }, [portfolioId, dateRange]);      // ← re-fetches on EVERY dropdown change

    useEffect(() => { fetchAll(); }, [fetchAll]);

    return { portfolios, summary, positions, composition, volatility, alerts, loading, error, refetch: fetchAll };
}