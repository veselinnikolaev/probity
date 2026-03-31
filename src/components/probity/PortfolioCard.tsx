import React, { useState } from 'react';
import { Sparkline } from './Sparkline';
import { RiskBadge } from './RiskBadge';

interface PortfolioCardProps {
  id: string;
  name: string;
  createdAt: string;
  positionsCount: number;
  totalValue: number;
  volatility: number;
  sharpeRatio: number;
  sparklineData: number[];
  onClick?: () => void;
}

interface EmptyCardProps {
  onClick?: () => void;
}

export const PortfolioCard: React.FC<PortfolioCardProps> = ({
  id,
  name,
  createdAt,
  positionsCount,
  totalValue,
  volatility,
  sharpeRatio,
  sparklineData,
  onClick,
}) => {
  const [isHovered, setIsHovered] = useState(false);

  const formattedDate = new Date(createdAt).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });

  const volatilityRisk: 'LOW' | 'MODERATE' | 'HIGH' =
    volatility < 15 ? 'LOW' : volatility < 25 ? 'MODERATE' : 'HIGH';

  return (
    <div
      className={`portfolio-card ${isHovered ? 'portfolio-card--hovered' : ''}`}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onClick={onClick}
      style={{ cursor: onClick ? 'pointer' : 'default' }}
    >
      <div className="portfolio-card__header">
        <h3 className="portfolio-card__title">{name}</h3>
        <span className="portfolio-card__date">{formattedDate}</span>
      </div>

      <div className="portfolio-card__meta">
        <div className="portfolio-card__meta-item">
          <span className="portfolio-card__meta-label">Positions</span>
          <span className="portfolio-card__meta-value">{positionsCount}</span>
        </div>
        <div className="portfolio-card__meta-item">
          <span className="portfolio-card__meta-label">Sharpe Ratio</span>
          <span className="portfolio-card__meta-value">{sharpeRatio.toFixed(2)}</span>
        </div>
      </div>

      <div className="portfolio-card__value">
        <span className="portfolio-card__value-label">Total Value</span>
        <span className="portfolio-card__value-amount">
          ${totalValue.toLocaleString('en-US', { maximumFractionDigits: 0 })}
        </span>
      </div>

      <div className="portfolio-card__footer">
        <div className="portfolio-card__sparkline-wrapper">
          <Sparkline
            data={sparklineData}
            width={80}
            height={24}
            color="#3B82F6"
            showArea={false}
          />
        </div>
        <RiskBadge level={volatilityRisk} size="sm" />
      </div>
    </div>
  );
};

export const EmptyPortfolioCard: React.FC<EmptyCardProps> = ({ onClick }) => {
  const [isHovered, setIsHovered] = useState(false);

  return (
    <div
      className={`portfolio-card portfolio-card--empty ${
        isHovered ? 'portfolio-card--empty-hovered' : ''
      }`}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onClick={onClick}
      style={{ cursor: 'pointer' }}
    >
      <div className="portfolio-card__empty-content">
        <div className="portfolio-card__empty-icon">➕</div>
        <h3 className="portfolio-card__empty-title">Add your first position</h3>
        <p className="portfolio-card__empty-description">
          Create a portfolio and start building your investment strategy
        </p>
      </div>
    </div>
  );
};
