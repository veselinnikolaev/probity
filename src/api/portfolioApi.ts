import api from './api';
import type {
    Portfolio, PortfolioSummary, Position,
    CompositionEntry, VolatilityPoint, RiskAlert,
} from '../types/portfolio';

export const portfolioApi = {
    getPortfolios: () =>
        api.get<Portfolio[]>('/portfolios').then(r => r.data),

    getSummary: (id: string, range: string) =>
        api.get<PortfolioSummary>(`/portfolios/${id}/summary`, { params: { range } }).then(r => r.data),

    getPositions: (id: string) =>
        api.get<Position[]>(`/portfolios/${id}/positions`).then(r => r.data),

    getComposition: (id: string) =>
        api.get<CompositionEntry[]>(`/portfolios/${id}/composition`).then(r => r.data),

    getVolatility: (id: string, range: string) =>
        api.get<VolatilityPoint[]>(`/portfolios/${id}/volatility`, { params: { range } }).then(r => r.data),

    getAlerts: (id: string) =>
        api.get<RiskAlert[]>(`/portfolios/${id}/alerts`).then(r => r.data),
};