import React, { useState } from 'react';

interface DonutChartProps {
  data: Array<{ label: string; value: number; color: string }>;
  width?: number;
  height?: number;
  onSegmentClick?: (label: string | null) => void;
}

export function DonutChart({
                             data,
                             width = 240,
                             height = 240,
                             onSegmentClick,
                           }: DonutChartProps) {
  const [hoveredIdx, setHoveredIdx] = useState<number | null>(null);
  const [selectedIdx, setSelectedIdx] = useState<number | null>(null);

  if (!data.length) return null;

  const radius = Math.min(width, height) / 2 - 20;
  const innerRadius = radius * 0.58;
  const cx = width / 2;
  const cy = height / 2;
  const total = data.reduce((s, d) => s + d.value, 0);

  let currentAngle = -Math.PI / 2;

  const segments = data.map((item, idx) => {
    const sliceAngle = (item.value / total) * 2 * Math.PI;
    const startAngle = currentAngle;
    const endAngle = currentAngle + sliceAngle;
    const midAngle = startAngle + sliceAngle / 2;

    const isHovered  = hoveredIdx === idx;
    const isSelected = selectedIdx === idx;
    const isActive   = isHovered || isSelected;

    // Push active segment outward
    const expansion = isActive ? 7 : 0;
    const ox = Math.cos(midAngle) * expansion;
    const oy = Math.sin(midAngle) * expansion;

    const r  = radius + (isActive ? 4 : 0);
    const ir = innerRadius;
    const laf = sliceAngle > Math.PI ? 1 : 0;

    const x1  = cx + ox + r  * Math.cos(startAngle);
    const y1  = cy + oy + r  * Math.sin(startAngle);
    const x2  = cx + ox + r  * Math.cos(endAngle);
    const y2  = cy + oy + r  * Math.sin(endAngle);
    const ix1 = cx + ox + ir * Math.cos(startAngle);
    const iy1 = cy + oy + ir * Math.sin(startAngle);
    const ix2 = cx + ox + ir * Math.cos(endAngle);
    const iy2 = cy + oy + ir * Math.sin(endAngle);

    const pathD = `M ${ix1} ${iy1} L ${x1} ${y1} A ${r} ${r} 0 ${laf} 1 ${x2} ${y2} L ${ix2} ${iy2} A ${ir} ${ir} 0 ${laf} 0 ${ix1} ${iy1} Z`;

    currentAngle = endAngle;
    return { item, idx, pathD, midAngle, isActive };
  });

  const handleClick = (idx: number) => {
    const next = selectedIdx === idx ? null : idx;
    setSelectedIdx(next);
    onSegmentClick?.(next !== null ? data[next].label : null);
  };

  const activeItem =
      selectedIdx !== null ? data[selectedIdx] :
          hoveredIdx  !== null ? data[hoveredIdx]  : null;

  return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '14px' }}>
        <svg
            width={width} height={height}
            viewBox={`0 0 ${width} ${height}`}
            style={{ overflow: 'visible', cursor: 'pointer' }}
        >
          {segments.map(seg => (
              <path
                  key={seg.idx}
                  d={seg.pathD}
                  fill={seg.item.color}
                  stroke="var(--pb-surface)"
                  strokeWidth="2"
                  opacity={
                    selectedIdx !== null && selectedIdx !== seg.idx ? 0.35 : 0.92
                  }
                  style={{ transition: 'all 0.18s ease' }}
                  onMouseEnter={() => setHoveredIdx(seg.idx)}
                  onMouseLeave={() => setHoveredIdx(null)}
                  onClick={() => handleClick(seg.idx)}
              />
          ))}

          {/* Center display */}
          <text
              x={cx} y={cy - 9}
              textAnchor="middle" fontSize="15" fontWeight="600"
              fill={activeItem ? activeItem.color : 'var(--pb-text-primary)'}
              fontFamily="var(--font-mono)"
              style={{ transition: 'fill 0.15s' }}
          >
            {activeItem ? `${activeItem.value.toFixed(0)}%` : `${total.toFixed(0)}%`}
          </text>
          <text
              x={cx} y={cy + 10}
              textAnchor="middle" fontSize="11"
              fill="var(--pb-text-secondary)"
              fontFamily="var(--font-mono)"
          >
            {activeItem ? activeItem.label : 'Total'}
          </text>
          {selectedIdx !== null && (
              <text
                  x={cx} y={cy + 26}
                  textAnchor="middle" fontSize="10"
                  fill="var(--pb-text-secondary)"
                  fontFamily="var(--font-mono)"
                  opacity="0.6"
              >
                click to clear
              </text>
          )}
        </svg>

        {/* Legend — also clickable */}
        <div style={{
          display: 'flex', flexWrap: 'wrap', gap: '6px 14px',
          justifyContent: 'center', maxWidth: `${width}px`,
        }}>
          {data.map((item, idx) => (
              <div
                  key={idx}
                  onClick={() => handleClick(idx)}
                  style={{
                    display: 'flex', alignItems: 'center', gap: '5px',
                    cursor: 'pointer', fontSize: '11px',
                    color: 'var(--pb-text-secondary)',
                    fontFamily: 'var(--font-mono)',
                    opacity: selectedIdx !== null && selectedIdx !== idx ? 0.35 : 1,
                    transition: 'opacity 0.15s',
                    userSelect: 'none',
                  }}
              >
            <span style={{
              width: '8px', height: '8px', borderRadius: '2px',
              background: item.color, flexShrink: 0,
            }} />
                {item.label}
              </div>
          ))}
        </div>
      </div>
  );
}