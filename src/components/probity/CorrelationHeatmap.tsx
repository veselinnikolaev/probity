import React, { useState } from 'react';
import styles from '@/pages/risk-analysis/risk-analysis.module.css';

interface CorrelationHeatmapProps {
  assets: string[];
  correlations: number[][];
}

export function CorrelationHeatmap({ assets, correlations }: CorrelationHeatmapProps) {
  const [hoveredCell, setHoveredCell] = useState<{ row: number; col: number } | null>(null);

  const getColor = (value: number): string => {
    // Blue-to-red diverging scale
    // -1 (negative correlation) = blue, 0 = white, 1 (positive correlation) = red
    if (value < -0.7) return '#1e3a8a';
    if (value < -0.4) return '#3b82f6';
    if (value < -0.1) return '#93c5fd';
    if (value < 0.1) return '#f3f4f6';
    if (value < 0.4) return '#fca5a5';
    if (value < 0.7) return '#ef4444';
    return '#991b1b';
  };

  return (
    <div className={styles['heatmap-wrapper']}>
      <div className={styles['heatmap-container']}>
        {/* Header row */}
        <div className={styles['heatmap-header-row']}>
          <div className={styles['heatmap-corner']} />
          {assets.map((asset, i) => (
            <div key={i} className={styles['heatmap-header-cell']}>
              <span className={styles['heatmap-header-text']}>{asset}</span>
            </div>
          ))}
        </div>

        {/* Data rows */}
        {assets.map((asset, rowIdx) => (
          <div key={rowIdx} className={styles['heatmap-data-row']}>
            <div className={styles['heatmap-row-label']}>
              <span className={styles['heatmap-label-text']}>{asset}</span>
            </div>
            {correlations[rowIdx].map((value, colIdx) => (
              <div
                key={`${rowIdx}-${colIdx}`}
                className={styles['heatmap-cell']}
                style={{
                  backgroundColor: getColor(value),
                  cursor: 'pointer',
                  opacity: hoveredCell ? (hoveredCell.row === rowIdx || hoveredCell.col === colIdx ? 1 : 0.5) : 1,
                  transition: 'opacity 0.12s',
                }}
                onMouseEnter={() => setHoveredCell({ row: rowIdx, col: colIdx })}
                onMouseLeave={() => setHoveredCell(null)}
                title={`${asset} vs ${assets[colIdx]}: ${value.toFixed(2)}`}
              >
                <span className={styles['heatmap-cell-value']}>{value.toFixed(2)}</span>
              </div>
            ))}
          </div>
        ))}
      </div>

      {/* Legend */}
      <div className={styles['heatmap-legend']}>
        <div className={styles['legend-item']}>
          <div className={styles['legend-color']} style={{ backgroundColor: '#1e3a8a' }} />
          <span>Negative Correlation</span>
        </div>
        <div className={styles['legend-item']}>
          <div className={styles['legend-color']} style={{ backgroundColor: '#f3f4f6' }} />
          <span>No Correlation</span>
        </div>
        <div className={styles['legend-item']}>
          <div className={styles['legend-color']} style={{ backgroundColor: '#991b1b' }} />
          <span>Positive Correlation</span>
        </div>
      </div>
    </div>
  );
}
