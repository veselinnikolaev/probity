import React from 'react';
import { PercentileSeries } from '../../types/simulation';

interface PercentileTableProps {
  percentileSeries: PercentileSeries[];
  currentValue: number;
}

export function PercentileTable({
  percentileSeries,
  currentValue,
}: PercentileTableProps) {
  const sorted = [...percentileSeries].sort((a, b) => a.percentile - b.percentile);

  return (
    <div style={{ width: '100%', overflow: 'auto' }}>
      <table
        style={{
          width: '100%',
          borderCollapse: 'collapse',
          fontFamily: 'var(--font-body)',
          fontSize: '13px',
        }}
      >
        <thead>
          <tr style={{ borderBottom: '1px solid var(--pb-border)', backgroundColor: 'var(--pb-surface-elevated)' }}>
            <th
              style={{
                padding: '12px 16px',
                textAlign: 'left',
                fontWeight: 500,
                color: 'var(--pb-text-secondary)',
                textTransform: 'uppercase',
                fontSize: '11px',
                letterSpacing: '0.06em',
              }}
            >
              Percentile
            </th>
            <th
              style={{
                padding: '12px 16px',
                textAlign: 'right',
                fontWeight: 500,
                color: 'var(--pb-text-secondary)',
                textTransform: 'uppercase',
                fontSize: '11px',
                letterSpacing: '0.06em',
              }}
            >
              Portfolio Value
            </th>
            <th
              style={{
                padding: '12px 16px',
                textAlign: 'right',
                fontWeight: 500,
                color: 'var(--pb-text-secondary)',
                textTransform: 'uppercase',
                fontSize: '11px',
                letterSpacing: '0.06em',
              }}
            >
              Gain / Loss
            </th>
            <th
              style={{
                padding: '12px 16px',
                textAlign: 'right',
                fontWeight: 500,
                color: 'var(--pb-text-secondary)',
                textTransform: 'uppercase',
                fontSize: '11px',
                letterSpacing: '0.06em',
              }}
            >
              Return %
            </th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((row, idx) => {
            const gainLoss = row.finalValue - currentValue;
            const returnPercent = (gainLoss / currentValue) * 100;
            const isNegative = gainLoss < 0;

            return (
              <tr
                key={row.percentile}
                style={{
                  borderBottom: '1px solid var(--pb-border)',
                  backgroundColor: idx % 2 === 0 ? 'transparent' : 'var(--pb-surface-elevated)',
                  transition: 'background-color 0.2s',
                }}
                onMouseEnter={(e) => {
                  (e.currentTarget as HTMLTableRowElement).style.backgroundColor =
                    'var(--pb-surface-elevated)';
                }}
                onMouseLeave={(e) => {
                  (e.currentTarget as HTMLTableRowElement).style.backgroundColor =
                    idx % 2 === 0 ? 'transparent' : 'var(--pb-surface-elevated)';
                }}
              >
                <td
                  style={{
                    padding: '12px 16px',
                    color: 'var(--pb-text-primary)',
                    fontWeight: 500,
                  }}
                >
                  {row.percentile}th
                </td>
                <td
                  style={{
                    padding: '12px 16px',
                    textAlign: 'right',
                    fontFamily: 'var(--font-mono)',
                    color: 'var(--pb-text-primary)',
                  }}
                >
                  ${Math.round(row.finalValue).toLocaleString()}
                </td>
                <td
                  style={{
                    padding: '12px 16px',
                    textAlign: 'right',
                    fontFamily: 'var(--font-mono)',
                    color: isNegative ? '#EF4444' : '#10B981',
                  }}
                >
                  {isNegative ? '−' : '+'}${Math.abs(Math.round(gainLoss)).toLocaleString()}
                </td>
                <td
                  style={{
                    padding: '12px 16px',
                    textAlign: 'right',
                    fontFamily: 'var(--font-mono)',
                    color: isNegative ? '#EF4444' : '#10B981',
                  }}
                >
                  {isNegative ? '−' : '+'}
                  {Math.abs(returnPercent).toFixed(1)}%
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
