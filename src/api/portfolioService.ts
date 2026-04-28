import api from './api';
import type {
    Portfolio, PortfolioSummary, Position,
    CompositionEntry, VolatilityPoint, RiskAlert,
    RiskMetrics, CorrelationMatrix,
    CreatePortfolioRequest
} from '../types/portfolio';

export const portfolioService = {
    getPortfolios: () =>
        api.get<Portfolio[]>('/portfolios').then(r => r.data),

    createPortfolio: (data: CreatePortfolioRequest) =>
        api.post<Portfolio>('/portfolios', data).then(r => r.data),

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

    getRiskMetrics: (id: string, range: string) =>
        api.get<RiskMetrics>(`/portfolios/${id}/risk-metrics`, { params: { range } }).then(r => r.data),

    getCorrelation: (id: string, range: string) =>
        api.get<CorrelationMatrix>(`/portfolios/${id}/correlation`, { params: { range } }).then(r => r.data),

    addPosition: (portfolioId: string, data: { ticker: string; quantity: number }) =>
        api.post(`/portfolios/${portfolioId}/positions`, data).then(r => r.data),

    updatePosition: (portfolioId: string, positionId: string, data: { quantity: number }) =>
        api.put(`/portfolios/${portfolioId}/positions/${positionId}`, data).then(r => r.data),

    deletePosition: (portfolioId: string, positionId: string) =>
        api.delete(`/portfolios/${portfolioId}/positions/${positionId}`).then(r => r.data),
};