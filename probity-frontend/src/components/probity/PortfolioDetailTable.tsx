import React, { useState, useMemo } from 'react';
import { Position } from '../../types/portfolio';
import { AssetTag } from './AssetTag';

interface PortfolioDetailTableProps {
  positions: Position[];
  deletingId?: string | null;
  onEdit?: (positionId: string) => void;
  onDelete?: (positionId: string) => void;
}

type SortKey = keyof Position | null;
type SortDir = 'asc' | 'desc';

const COL_HEADERS = [
  { key: 'name',             label: 'Asset Name',          width: '16%' },
  { key: 'ticker',           label: 'Ticker',              width: '8%'  },
  { key: 'assetType',        label: 'Type',                width: '8%',  align: 'center' as const },
  { key: 'quantity',         label: 'Quantity',            width: '9%',  align: 'right'  as const },
  { key: 'avgBuyPrice',      label: 'Avg Buy Price',       width: '10%', align: 'right'  as const },
  { key: 'currentPrice',     label: 'Current Price',       width: '10%', align: 'right'  as const },
  { key: 'positionValueRaw', label: 'Position Value',      width: '10%', align: 'right'  as const },
  { key: 'weight',           label: 'Weight %',            width: '8%',  align: 'right'  as const },
  { key: 'dailyReturn',      label: 'Daily Return %',      width: '9%',  align: 'right'  as const },
  { key: 'volatility',       label: 'Volatility %',        width: '9%',  align: 'right'  as const },
  { key: 'riskContribution', label: 'Risk Contribution %', width: '13%', align: 'right'  as const },
];

const RISK_LEVEL_ORDER: Record<string, number> = { LOW: 0, MODERATE: 1, HIGH: 2 };

function getSortValue(position: Position, key: SortKey): string | number {
  if (!key) return '';
  const v = (position as any)[key];
  if (typeof v === 'number') return v;
  if (typeof v === 'string') {
    const cleaned = v.replace(/[$,%]/g, '').trim();
    const n = parseFloat(cleaned);
    return isNaN(n) ? cleaned.toLowerCase() : n;
  }
  if (key === 'riskLevel') return RISK_LEVEL_ORDER[position.riskLevel] ?? 0;
  return '';
}

// ── Delta arrow: green for positive, red for negative, neutral dash for zero ─
function DeltaCell({ value, suffix = '%' }: { value?: number; suffix?: string }) {
  if (value === undefined || value === null) {
    return <span style={{ color: 'var(--pb-text-secondary)', fontFamily: 'var(--font-mono)', fontSize: 13 }}>—</span>;
  }

  const abs = Math.abs(value);
  const formatted = `${abs.toFixed(2)}${suffix}`;

  if (value === 0) {
    return (
        <span style={{ color: 'var(--pb-text-secondary)', fontFamily: 'var(--font-mono)', fontSize: 12, fontWeight: 500 }}>
        — {formatted}
      </span>
    );
  }

  const positive = value > 0;
  return (
      <span style={{
        color: positive ? '#10B981' : '#EF4444',
        fontFamily: 'var(--font-mono)',
        fontSize: 12,
        fontWeight: 500,
        display: 'inline-flex',
        alignItems: 'center',
        gap: 3,
      }}>
      <span style={{ fontSize: 9 }}>{positive ? '▲' : '▼'}</span>
        {positive ? '+' : '-'}{formatted}
    </span>
  );
}

export function PortfolioDetailTable({ positions, onEdit, onDelete }: PortfolioDetailTableProps) {
  const [hoveredId, setHoveredId]   = useState<string | null>(null);
  const [sortKey, setSortKey]       = useState<SortKey>(null);
  const [sortDir, setSortDir]       = useState<SortDir>('asc');

  const handleSort = (key: string) => {
    if (sortKey === key) setSortDir(d => d === 'asc' ? 'desc' : 'asc');
    else { setSortKey(key as SortKey); setSortDir('asc'); }
  };

  const sortedPositions = useMemo(() => {
    if (!sortKey) return positions;
    return [...positions].sort((a, b) => {
      const av = getSortValue(a, sortKey);
      const bv = getSortValue(b, sortKey);
      if (av < bv) return sortDir === 'asc' ? -1 : 1;
      if (av > bv) return sortDir === 'asc' ? 1 : -1;
      return 0;
    });
  }, [positions, sortKey, sortDir]);

  const maxRisk = useMemo(
      () => Math.max(...sortedPositions.map(p => p.riskContribution ?? 0), 0),
      [sortedPositions],
  );

  const fmtPrice   = (v?: number) => v === undefined ? '—' : `$${v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  const fmtPct     = (v?: number) => v === undefined ? '—' : `${v.toFixed(2)}%`;
  const fmtQty     = (v?: number) => v === undefined ? '—' : v.toLocaleString('en-US', { maximumFractionDigits: 4 });
  const fmtValue   = (v?: number) => {
    if (v === undefined) return '—';
    if (v >= 1_000_000) return `$${(v / 1_000_000).toFixed(2)}M`;
    if (v >= 1_000)     return `$${(v / 1_000).toFixed(2)}K`;
    return `$${v.toFixed(2)}`;
  };

  const thStyle = (key: string, align: 'left' | 'right' | 'center' = 'left'): React.CSSProperties => ({
    padding: '12px',
    textAlign: align,
    fontFamily: 'var(--font-body)',
    fontSize: 11,
    fontWeight: 500,
    letterSpacing: '0.06em',
    textTransform: 'uppercase',
    color: sortKey === key ? '#60A5FA' : 'var(--pb-text-secondary)',
    cursor: 'pointer',
    whiteSpace: 'nowrap',
    userSelect: 'none',
    transition: 'color 0.12s',
  });

  return (
      <div style={{ backgroundColor: 'var(--pb-surface)', border: '1px solid var(--pb-border)', borderRadius: 8, overflow: 'hidden' }}>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 1300 }}>
            <thead>
            <tr style={{ backgroundColor: 'var(--pb-surface-elevated)', borderBottom: '1px solid var(--pb-border)' }}>
              {COL_HEADERS.map(col => (
                  <th
                      key={col.key}
                      onClick={() => handleSort(col.key)}
                      style={{ ...thStyle(col.key, col.align), width: col.width }}
                      onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = 'var(--pb-text-primary)'; }}
                      onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = sortKey === col.key ? '#60A5FA' : 'var(--pb-text-secondary)'; }}
                  >
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                    {col.label}
                    {sortKey === col.key && <span style={{ fontSize: 9, opacity: 0.8 }}>{sortDir === 'asc' ? '▲' : '▼'}</span>}
                  </span>
                  </th>
              ))}
              <th style={{ width: '7%', padding: '12px', textAlign: 'center', fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 500, letterSpacing: '0.06em', textTransform: 'uppercase', color: 'var(--pb-text-secondary)', whiteSpace: 'nowrap' }}>
                Actions
              </th>
            </tr>
            </thead>
            <tbody>
            {sortedPositions.map(position => {
              const isHovered = hoveredId === position.id;
              return (
                  <tr
                      key={position.id}
                      onMouseEnter={() => setHoveredId(position.id)}
                      onMouseLeave={() => setHoveredId(null)}
                      style={{
                        borderBottom: '1px solid var(--pb-border)',
                        backgroundColor: isHovered ? 'rgba(19,27,46,0.8)' : 'transparent',
                        transition: 'background-color 0.1s',
                      }}
                  >
                    {/* Asset Name */}
                    <td style={{ padding: '12px' }}>
                    <span style={{ fontFamily: 'var(--font-body)', fontSize: 13, fontWeight: 500, color: 'var(--pb-text-primary)' }}>
                      {position.name}
                    </span>
                    </td>

                    {/* Ticker */}
                    <td style={{ padding: '12px' }}>
                    <span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, fontWeight: 500, color: 'var(--pb-text-primary)', letterSpacing: '0.02em' }}>
                      {position.ticker}
                    </span>
                    </td>

                    {/* Type */}
                    <td style={{ padding: '12px', textAlign: 'center' }}>
                      <AssetTag type={position.assetType} size="sm" />
                    </td>

                    {/* Quantity */}
                    <td style={{ padding: '12px', textAlign: 'right' }}>
                    <span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--pb-text-primary)' }}>
                      {fmtQty(position.quantity)}
                    </span>
                    </td>

                    {/* Avg Buy Price */}
                    <td style={{ padding: '12px', textAlign: 'right' }}>
                    <span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--pb-text-secondary)' }}>
                      {fmtPrice(position.avgBuyPrice)}
                    </span>
                    </td>

                    {/* Current Price */}
                    <td style={{ padding: '12px', textAlign: 'right' }}>
                    <span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--pb-text-primary)' }}>
                      {fmtPrice(position.currentPrice)}
                    </span>
                    </td>

                    {/* Position Value (raw number, nicely formatted) */}
                    <td style={{ padding: '12px', textAlign: 'right' }}>
                    <span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--pb-text-primary)', fontWeight: 500 }}>
                      {fmtValue(position.positionValueRaw)}
                    </span>
                    </td>

                    {/* Weight % */}
                    <td style={{ padding: '12px', textAlign: 'right' }}>
                    <span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--pb-text-secondary)' }}>
                      {fmtPct(position.weight)}
                    </span>
                    </td>

                    {/* Daily Return % — neutral at zero */}
                    <td style={{ padding: '12px', textAlign: 'right' }}>
                      <DeltaCell value={position.dailyReturn} />
                    </td>

                    {/* Volatility % */}
                    <td style={{ padding: '12px', textAlign: 'right' }}>
                    <span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--pb-text-secondary)' }}>
                      {fmtPct(position.volatility)}
                    </span>
                    </td>

                    {/* Risk Contribution % with inline bar */}
                    <td style={{ padding: '12px', textAlign: 'right', position: 'relative' }}>
                      {(position.riskContribution ?? 0) > 0 && maxRisk > 0 && (
                          <div style={{
                            position: 'absolute', left: 0, top: '50%', transform: 'translateY(-50%)',
                            height: 24,
                            width: `${((position.riskContribution ?? 0) / maxRisk) * 100}%`,
                            backgroundColor: 'rgba(59,130,246,0.1)',
                            border: '1px solid rgba(59,130,246,0.2)',
                            borderRadius: 4, zIndex: 0,
                          }} />
                      )}
                      <span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--pb-text-primary)', position: 'relative', zIndex: 1, fontWeight: 500 }}>
                      {fmtPct(position.riskContribution)}
                    </span>
                    </td>

                    {/* Actions */}
                    <td style={{ padding: '12px', textAlign: 'center' }}>
                      <div style={{ display: 'flex', gap: 8, justifyContent: 'center' }}>
                        <button
                            onClick={() => onEdit?.(position.id)}
                            style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: 28, height: 28, background: 'rgba(59,130,246,0.1)', border: '1px solid rgba(59,130,246,0.2)', borderRadius: 4, color: '#60A5FA', fontSize: 14, cursor: 'pointer', transition: 'all 0.12s' }}
                            onMouseEnter={e => { (e.currentTarget as HTMLElement).style.background = 'rgba(59,130,246,0.2)'; }}
                            onMouseLeave={e => { (e.currentTarget as HTMLElement).style.background = 'rgba(59,130,246,0.1)'; }}
                            title="Edit position"
                        >✎</button>
                        <button
                            onClick={() => onDelete?.(position.id)}
                            style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: 28, height: 28, background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.2)', borderRadius: 4, color: '#EF4444', fontSize: 14, cursor: 'pointer', transition: 'all 0.12s' }}
                            onMouseEnter={e => { (e.currentTarget as HTMLElement).style.background = 'rgba(239,68,68,0.2)'; }}
                            onMouseLeave={e => { (e.currentTarget as HTMLElement).style.background = 'rgba(239,68,68,0.1)'; }}
                            title="Delete position"
                        >✕</button>
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