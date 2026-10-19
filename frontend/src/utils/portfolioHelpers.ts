import type { Portfolio } from '@/types/portfolio';

export interface PortfolioOption {
  value: string;
  label: string;
  description: string;
}

export function buildPortfolioOptions(portfolios: Portfolio[]): PortfolioOption[] {
  return portfolios.map(p => ({
    value: p.id,
    label: p.name,
    description: `$${p.totalValue.toLocaleString()}`,
  }));
}
