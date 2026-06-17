import React, { useState } from 'react';
import { Sparkline } from './Sparkline';
import { RiskBadge } from './RiskBadge';
import { ProbityButton } from './ProbityButton';
import { Eye, Pencil, Trash2 } from 'lucide-react';

interface PortfolioCardProps {
  id: string;
  name: string;
  description?: string;
  createdAt: string;
  positionsCount: number;
  totalValue: number;
  volatility: number;
  sharpeRatio: number;
  sparklineData: number[];
  onClick?: () => void;
  onView?: () => void;
  onEdit?: () => void;
  onDelete?: () => void;
}

interface EmptyCardProps {
  onClick?: () => void;
}

export const PortfolioCard: React.FC<PortfolioCardProps> = ({
  id,
  name,
  description,
  createdAt,
  positionsCount,
  totalValue,
  volatility,
  sharpeRatio,
  sparklineData,
  onClick,
  onView,
  onEdit,
  onDelete,
}) => {
  const [isHovered, setIsHovered] = useState(false);

  const formattedDate = new Date(createdAt).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });

  const safeVolatility = volatility || 0;
  const volatilityRisk: 'LOW' | 'MODERATE' | 'HIGH' =
      safeVolatility < 15 ? 'LOW' : safeVolatility < 25 ? 'MODERATE' : 'HIGH';

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

      {description !== undefined && (
        <div
          className="portfolio-card__description"
          style={{
            fontFamily: 'var(--font-body)',
            fontSize: '14px',
            color: description ? 'var(--pb-text-secondary)' : 'var(--pb-text-muted)',
            fontStyle: description ? 'normal' : 'italic',
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            marginTop: '8px',
          }}
        >
          {description || 'No description'}
        </div>
      )}

      <div className="portfolio-card__meta">
        <div className="portfolio-card__meta-item">
          <span className="portfolio-card__meta-label">Positions</span>
          <span className="portfolio-card__meta-value">{positionsCount}</span>
        </div>
        <div className="portfolio-card__meta-item">
          <span className="portfolio-card__meta-label">Sharpe Ratio</span>
          <span className="portfolio-card__meta-value">
            {sharpeRatio != null ? sharpeRatio.toFixed(2) : 'N/A'}
          </span>
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
          {sparklineData && sparklineData.length > 0 ? (
              <Sparkline
                  data={sparklineData}
                  width={80}
                  height={24}
                  color="#3B82F6"
                  showArea={false}
              />
          ) : (
              <span className="portfolio-card__no-data">No chart data</span>
          )}
        </div>
        <RiskBadge level={volatilityRisk} size="sm" />
      </div>

      <div
        style={{
          borderTop: '1px solid var(--pb-border)',
          paddingTop: '12px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <ProbityButton
          variant="primary"
          size="sm"
          iconLeft={<Eye size={14} />}
          onClick={(e) => {
            e.stopPropagation();
            onView?.();
          }}
        >
          View
        </ProbityButton>
        <div style={{ display: 'flex', gap: '8px' }}>
          <ProbityButton
            variant="ghost"
            size="sm"
            iconLeft={<Pencil size={14} />}
            onClick={(e) => {
              e.stopPropagation();
              onEdit?.();
            }}
          >
            Edit
          </ProbityButton>
          <ProbityButton
            variant="danger"
            size="sm"
            iconLeft={<Trash2 size={14} />}
            onClick={(e) => {
              e.stopPropagation();
              onDelete?.();
            }}
          >
            Delete
          </ProbityButton>
        </div>
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
