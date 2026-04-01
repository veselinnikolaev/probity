import { useState, useEffect, useCallback } from 'react';
import { portfolioService } from '../api/portfolioService';
import type {
    Portfolio, PortfolioSummary, Position,
    CompositionEntry, RiskAlert,
} from '../types/portfolio';

interface PortfolioData {
    portfolios: Portfolio[];
    summary: PortfolioSummary | null;
    positions: Position[];
    composition: CompositionEntry[];
    volatility: number[];
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
        setLoading(true);
        setError(null);
        try {
            // 1. Fetch the master list of portfolios first (this doesn't require an ID!)
            const lists = await portfolioService.getPortfolios();
            setPortfolios(lists);

            // 2. Guard here instead! Only fetch detailed data if we have an active ID
            if (portfolioId) {
                const [sum, pos, comp, vol, alrt] = await Promise.all([
                    portfolioService.getSummary(portfolioId, dateRange),
                    portfolioService.getPositions(portfolioId),
                    portfolioService.getComposition(portfolioId),
                    portfolioService.getVolatility(portfolioId, dateRange),
                    portfolioService.getAlerts(portfolioId),
                ]);

                setSummary(sum);
                setPositions(pos);
                setComposition(comp);
                setVolatility(vol.map(p => p.value));
                setAlerts(alrt);
            }
        } catch (e: any) {
            setError(e?.response?.data?.message ?? 'Failed to load portfolio data');
        } finally {
            // 3. This finally block will ALWAYS run, unfreezing your loading state
            setLoading(false);
        }
    }, [portfolioId, dateRange]);

    useEffect(() => {
        fetchAll();
    }, [fetchAll]);

    return { portfolios, summary, positions, composition, volatility, alerts, loading, error, refetch: fetchAll };
}