import React, { useState, useMemo } from 'react';
import { Position } from '../../types/portfolio';
import { AssetTag } from './AssetTag';
import { RiskBadge } from './RiskBadge';

interface PortfolioDetailTableProps {
  positions: Position[];
  onEdit?: (positionId: string) => void;
  onDelete?: (positionId: string) => void;
}

type SortKey = keyof Position | null;
type SortDir = 'asc' | 'desc';

const COL_HEADERS = [
  { key: 'name', label: 'Asset Name', width: '18%' },
  { key: 'ticker', label: 'Ticker', width: '10%' },
  { key: 'assetType', label: 'Type', width: '9%', align: 'center' as const },
  { key: 'quantity', label: 'Quantity', width: '10%', align: 'right' as const },
  { key: 'avgBuyPrice', label: 'Avg Buy Price', width: '10%', align: 'right' as const },
  { key: 'currentPrice', label: 'Current Price', width: '10%', align: 'right' as const },
  { key: 'weight', label: 'Weight %', width: '9%', align: 'right' as const },
  { key: 'dailyReturn', label: 'Daily Return %', width: '10%', align: 'right' as const },
  { key: 'volatility', label: 'Volatility %', width: '10%', align: 'right' as const },
  { key: 'riskContribution', label: 'Risk Contribution %', width: '14%', align: 'right' as const },
];

function parseNumericValue(raw: string | number | undefined): number {
  if (raw === undefined || raw === null || raw === '—') return -Infinity;
  if (typeof raw === 'number') return raw;

  const cleaned = String(raw).replace(/[$,%]/g, '').trim();
  const suffixMap: Record<string, number> = { K: 1e3, M: 1e6, B: 1e9, T: 1e12 };
  const match = cleaned.match(/^([+-]?[\d.]+)\s*([KMBT])$/i);
  if (match) {
    const num = parseFloat(match[1]);
    const multiplier = suffixMap[match[2].toUpperCase()] ?? 1;
    return num * multiplier;
  }

  const parsed = parseFloat(cleaned);
  return isNaN(parsed) ? -Infinity : parsed;
}

const RISK_LEVEL_ORDER: Record<string, number> = {
  LOW: 0,
  MODERATE: 1,
  HIGH: 2,
};

function getSortValue(position: Position, key: SortKey): string | number {
  if (!key) return '';

  switch (key) {
    case 'quantity':
    case 'avgBuyPrice':
    case 'currentPrice':
    case 'weight':
    case 'dailyReturn':
    case 'volatility':
    case 'riskContribution':
      return parseNumericValue(position[key]);
    case 'riskLevel':
      return RISK_LEVEL_ORDER[position.riskLevel] ?? 0;
    default:
      return String(position[key] ?? '').toLowerCase();
  }
}

export function PortfolioDetailTable({
  positions,
  onEdit,
  onDelete,
}: PortfolioDetailTableProps) {
  const [hoveredId, setHoveredId] = useState<string | null>(null);
  const [sortKey, setSortKey] = useState<SortKey>(null);
  const [sortDir, setSortDir] = useState<SortDir>('asc');

  const handleSort = (key: string) => {
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key as SortKey);
      setSortDir('asc');
    }
  };

  const sortedPositions = useMemo(() => {
    if (!sortKey) return positions;
    return [...positions].sort((a, b) => {
      const aVal = getSortValue(a, sortKey);
      const bVal = getSortValue(b, sortKey);
      if (aVal < bVal) return sortDir === 'asc' ? -1 : 1;
      if (aVal > bVal) return sortDir === 'asc' ? 1 : -1;
      return 0;
    });
  }, [positions, sortKey, sortDir]);

  const maxRiskContribution = useMemo(() => {
    return Math.max(...sortedPositions.map((p) => p.riskContribution || 0));
  }, [sortedPositions]);

  const formatPrice = (value?: number) => {
    if (value === undefined) return '—';
    return `$${value.toFixed(2)}`;
  };

  const formatPercent = (value?: number) => {
    if (value === undefined) return '—';
    return `${value.toFixed(2)}%`;
  };

  const formatNumber = (value?: number) => {
    if (value === undefined) return '—';
    return value.toLocaleString('en-US', { maximumFractionDigits: 0 });
  };

  return (
    <div
      style={{
        backgroundColor: 'var(--pb-surface)',
        border: '1px solid var(--pb-border)',
        borderRadius: '8px',
        overflow: 'hidden',
      }}
    >
      <div style={{ overflowX: 'auto' }}>
        <table
          style={{
            width: '100%',
            borderCollapse: 'collapse',
            minWidth: '1200px',
          }}
        >
          <thead>
            <tr
              style={{
                backgroundColor: 'var(--pb-surface-elevated)',
                borderBottom: '1px solid var(--pb-border)',
              }}
            >
              {COL_HEADERS.map((col) => (
                <th
                  key={col.key}
                  onClick={() => handleSort(col.key)}
                  style={{
                    width: col.width,
                    padding: '12px',
                    textAlign: col.align ?? 'left',
                    fontFamily: 'var(--font-body)',
                    fontSize: '11px',
                    fontWeight: 500,
                    letterSpacing: '0.06em',
                    textTransform: 'uppercase',
                    color: sortKey === col.key ? '#60A5FA' : 'var(--pb-text-secondary)',
                    cursor: 'pointer',
                    whiteSpace: 'nowrap',
                    userSelect: 'none',
                    transition: 'color 0.12s',
                  }}
                  onMouseEnter={(e) => {
                    (e.currentTarget as HTMLElement).style.color = 'var(--pb-text-primary)';
                  }}
                  onMouseLeave={(e) => {
                    (e.currentTarget as HTMLElement).style.color =
                      sortKey === col.key ? '#60A5FA' : 'var(--pb-text-secondary)';
                  }}
                >
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                    {col.label}
                    {sortKey === col.key && (
                      <span style={{ fontSize: '9px', opacity: 0.8 }}>
                        {sortDir === 'asc' ? '▲' : '▼'}
                      </span>
                    )}
                  </span>
                </th>
              ))}
              <th
                style={{
                  width: '8%',
                  padding: '12px',
                  textAlign: 'center',
                  fontFamily: 'var(--font-body)',
                  fontSize: '11px',
                  fontWeight: 500,
                  letterSpacing: '0.06em',
                  textTransform: 'uppercase',
                  color: 'var(--pb-text-secondary)',
                  whiteSpace: 'nowrap',
                }}
              >
                Actions
              </th>
            </tr>
          </thead>
          <tbody>
            {sortedPositions.map((position) => {
              const isHovered = hoveredId === position.id;
              const dailyReturnValue = position.dailyReturn ?? 0;
              const isPositive = dailyReturnValue >= 0;

              return (
                <tr
                  key={position.id}
                  onMouseEnter={() => setHoveredId(position.id)}
                  onMouseLeave={() => setHoveredId(null)}
                  style={{
                    borderBottom: '1px solid var(--pb-border)',
                    backgroundColor: isHovered ? 'rgba(19, 27, 46, 0.8)' : 'transparent',
                    transition: 'background-color 0.1s',
                  }}
                >
                  {/* Asset Name */}
                  <td style={{ padding: '12px' }}>
                    <span
                      style={{
                        fontFamily: 'var(--font-body)',
                        fontSize: '13px',
                        fontWeight: 500,
                        color: 'var(--pb-text-primary)',
                      }}
                    >
                      {position.name}
                    </span>
                  </td>

                  {/* Ticker */}
                  <td style={{ padding: '12px' }}>
                    <span
                      style={{
                        fontFamily: 'var(--font-mono)',
                        fontSize: '13px',
                        fontWeight: 500,
                        color: 'var(--pb-text-primary)',
                        letterSpacing: '0.02em',
                      }}
                    >
                      {position.ticker}
                    </span>
                  </td>

                  {/* Type */}
                  <td style={{ padding: '12px', textAlign: 'center' }}>
                    <AssetTag type={position.assetType} size="sm" />
                  </td>

                  {/* Quantity */}
                  <td style={{ padding: '12px', textAlign: 'right' }}>
                    <span
                      style={{
                        fontFamily: 'var(--font-mono)',
                        fontSize: '13px',
                        color: 'var(--pb-text-primary)',
                      }}
                    >
                      {formatNumber(position.quantity)}
                    </span>
                  </td>

                  {/* Avg Buy Price */}
                  <td style={{ padding: '12px', textAlign: 'right' }}>
                    <span
                      style={{
                        fontFamily: 'var(--font-mono)',
                        fontSize: '13px',
                        color: 'var(--pb-text-secondary)',
                      }}
                    >
                      {formatPrice(position.avgBuyPrice)}
                    </span>
                  </td>

                  {/* Current Price */}
                  <td style={{ padding: '12px', textAlign: 'right' }}>
                    <span
                      style={{
                        fontFamily: 'var(--font-mono)',
                        fontSize: '13px',
                        color: 'var(--pb-text-primary)',
                      }}
                    >
                      {formatPrice(position.currentPrice)}
                    </span>
                  </td>

                  {/* Weight % */}
                  <td style={{ padding: '12px', textAlign: 'right' }}>
                    <span
                      style={{
                        fontFamily: 'var(--font-mono)',
                        fontSize: '13px',
                        color: 'var(--pb-text-secondary)',
                      }}
                    >
                      {formatPercent(position.weight)}
                    </span>
                  </td>

                  {/* Daily Return % */}
                  <td style={{ padding: '12px', textAlign: 'right' }}>
                    <span
                      style={{
                        fontFamily: 'var(--font-mono)',
                        fontSize: '12px',
                        fontWeight: 500,
                        color: isPositive ? '#10B981' : '#EF4444',
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '3px',
                      }}
                    >
                      <span style={{ fontSize: '9px' }}>{isPositive ? '▲' : '▼'}</span>
                      {formatPercent(position.dailyReturn)}
                    </span>
                  </td>

                  {/* Volatility % */}
                  <td style={{ padding: '12px', textAlign: 'right' }}>
                    <span
                      style={{
                        fontFamily: 'var(--font-mono)',
                        fontSize: '13px',
                        color: 'var(--pb-text-secondary)',
                      }}
                    >
                      {formatPercent(position.volatility)}
                    </span>
                  </td>

                  {/* Risk Contribution % with inline bar */}
                  <td style={{ padding: '12px', textAlign: 'right', position: 'relative' }}>
                    {/* Background bar */}
                    {position.riskContribution && maxRiskContribution > 0 && (
                      <div
                        style={{
                          position: 'absolute',
                          left: 0,
                          top: '50%',
                          transform: 'translateY(-50%)',
                          height: '24px',
                          width: `${(position.riskContribution / maxRiskContribution) * 100}%`,
                          backgroundColor: 'rgba(59, 130, 246, 0.1)',
                          border: '1px solid rgba(59, 130, 246, 0.2)',
                          borderRadius: '4px',
                          zIndex: 0,
                        }}
                      />
                    )}
                    {/* Text on top of bar */}
                    <span
                      style={{
                        fontFamily: 'var(--font-mono)',
                        fontSize: '13px',
                        color: 'var(--pb-text-primary)',
                        position: 'relative',
                        zIndex: 1,
                        fontWeight: 500,
                      }}
                    >
                      {formatPercent(position.riskContribution)}
                    </span>
                  </td>

                  {/* Actions */}
                  <td style={{ padding: '12px', textAlign: 'center' }}>
                    <div style={{ display: 'flex', gap: '8px', justifyContent: 'center' }}>
                      <button
                        onClick={() => onEdit?.(position.id)}
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          width: '28px',
                          height: '28px',
                          background: 'rgba(59, 130, 246, 0.1)',
                          border: '1px solid rgba(59, 130, 246, 0.2)',
                          borderRadius: '4px',
                          color: '#60A5FA',
                          fontSize: '14px',
                          cursor: 'pointer',
                          transition: 'all 0.12s',
                        }}
                        onMouseEnter={(e) => {
                          (e.currentTarget as HTMLElement).style.background = 'rgba(59, 130, 246, 0.2)';
                          (e.currentTarget as HTMLElement).style.borderColor = 'rgba(59, 130, 246, 0.4)';
                        }}
                        onMouseLeave={(e) => {
                          (e.currentTarget as HTMLElement).style.background = 'rgba(59, 130, 246, 0.1)';
                          (e.currentTarget as HTMLElement).style.borderColor = 'rgba(59, 130, 246, 0.2)';
                        }}
                        title="Edit position"
                      >
                        ✎
                      </button>
                      <button
                        onClick={() => onDelete?.(position.id)}
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          width: '28px',
                          height: '28px',
                          background: 'rgba(239, 68, 68, 0.1)',
                          border: '1px solid rgba(239, 68, 68, 0.2)',
                          borderRadius: '4px',
                          color: '#EF4444',
                          fontSize: '14px',
                          cursor: 'pointer',
                          transition: 'all 0.12s',
                        }}
                        onMouseEnter={(e) => {
                          (e.currentTarget as HTMLElement).style.background = 'rgba(239, 68, 68, 0.2)';
                          (e.currentTarget as HTMLElement).style.borderColor = 'rgba(239, 68, 68, 0.4)';
                        }}
                        onMouseLeave={(e) => {
                          (e.currentTarget as HTMLElement).style.background = 'rgba(239, 68, 68, 0.1)';
                          (e.currentTarget as HTMLElement).style.borderColor = 'rgba(239, 68, 68, 0.2)';
                        }}
                        title="Delete position"
                      >
                        ✕
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
