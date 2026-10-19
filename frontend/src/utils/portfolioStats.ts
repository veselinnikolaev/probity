import type { StatCardProps } from '@/components/probity/StatCard';
import type { PortfolioSummary } from '@/types/portfolio';

export function buildStatCards(summary: PortfolioSummary, positionsCount?: number): StatCardProps[] {
  const cards: StatCardProps[] = [
    {
      label: 'Total Value',
      value: summary.totalValue.toLocaleString('en-US', { maximumFractionDigits: 0 }),
      delta: `${summary.totalValueDelta >= 0 ? '+' : ''}${summary.totalValueDelta.toFixed(1)}%`,
      deltaType: summary.totalValueDelta >= 0 ? 'up' : 'down',
      sparklineData: summary.sparklines?.totalValue || [],
      prefix: '$',
      invertSentiment: false,
    },
    {
      label: 'Daily Return',
      value: summary.dailyReturn.toLocaleString('en-US', { maximumFractionDigits: 2 }),
      delta: `${summary.dailyReturnDelta >= 0 ? '+' : ''}${summary.dailyReturnDelta.toFixed(2)}%`,
      deltaType: summary.dailyReturnDelta >= 0 ? 'up' : 'down',
      sparklineData: summary.sparklines?.dailyReturn || [],
      prefix: '$',
      invertSentiment: false,
    },
    {
      label: 'Portfolio Volatility',
      value: `${summary.volatility.toFixed(1)}%`,
      delta: `${summary.volatilityDelta >= 0 ? '+' : ''}${summary.volatilityDelta.toFixed(1)}%`,
      deltaType: summary.volatilityDelta <= 0 ? 'down' : 'up',
      sparklineData: summary.sparklines?.volatility || [],
      invertSentiment: true,
    },
    {
      label: 'Sharpe Ratio',
      value: summary.sharpeRatio.toFixed(2),
      delta: `${summary.sharpeRatioDelta >= 0 ? '+' : ''}${summary.sharpeRatioDelta.toFixed(2)}`,
      deltaType: summary.sharpeRatioDelta >= 0 ? 'up' : 'down',
      sparklineData: summary.sparklines?.sharpeRatio || [],
      invertSentiment: false,
    },
    {
      label: 'VaR (95%)',
      value: summary.var95.toLocaleString('en-US', { maximumFractionDigits: 0 }),
      delta: `${summary.var95Delta >= 0 ? '+' : ''}${summary.var95Delta.toFixed(1)}%`,
      deltaType: summary.var95Delta <= 0 ? 'down' : 'up',
      sparklineData: summary.sparklines?.var95 || [],
      prefix: '$',
      invertSentiment: true,
    },
  ];

  // If positionsCount is provided, replace Daily Return with Positions count (for PortfolioDetailPage)
  if (positionsCount !== undefined) {
    cards[1] = {
      label: 'Positions',
      value: positionsCount.toString(),
      delta: '',
      sparklineData: [],
      invertSentiment: false,
    };
  }

  return cards;
}
