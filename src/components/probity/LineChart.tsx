import React, { useRef, useState, useCallback } from 'react';

interface LineChartProps {
  data: number[];
  width?: number;
  height?: number;
  lineColor?: string;
  gridColor?: string;
  dateRange?: string;
}

interface TooltipState {
  visible: boolean;
  x: number;
  y: number;
  value: number;
  index: number;
}

export function LineChart({
                            data,
                            width = 400,
                            height = 240,
                            lineColor = '#3B82F6',
                            gridColor = 'rgba(30, 45, 69, 0.3)',
                            dateRange = '90d',
                          }: LineChartProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const [tooltip, setTooltip] = useState<TooltipState>({
    visible: false, x: 0, y: 0, value: 0, index: 0,
  });
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  if (data.length < 2) return null;

  const pad = { top: 16, right: 16, bottom: 28, left: 48 };
  const gW = width - pad.left - pad.right;
  const gH = height - pad.top - pad.bottom;

  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;

  const gridCount = 4;
  const gridPts = Array.from({ length: gridCount + 1 }, (_, i) => ({
    y: pad.top + (gH / gridCount) * i,
    value: max - (range / gridCount) * i,
  }));

  const points = data.map((v, i) => ({
    x: pad.left + (gW / (data.length - 1)) * i,
    y: pad.top + gH - ((v - min) / range) * gH,
    value: v,
    idx: i,
  }));

  const pathD = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ');
  const areaD = `${pathD} L ${points.at(-1)!.x} ${pad.top + gH} L ${pad.left} ${pad.top + gH} Z`;

  const handleMouseMove = useCallback((e: React.MouseEvent<SVGSVGElement>) => {
    const rect = svgRef.current?.getBoundingClientRect();
    if (!rect) return;
    // Scale mouse X to SVG coordinate space (handles responsive width)
    const scaleX = width / rect.width;
    const mouseX = (e.clientX - rect.left) * scaleX;
    let nearest = points[0];
    let minDist = Math.abs(points[0].x - mouseX);
    for (const p of points) {
      const d = Math.abs(p.x - mouseX);
      if (d < minDist) { minDist = d; nearest = p; }
    }
    setHoveredIndex(nearest.idx);
    setTooltip({ visible: true, x: nearest.x, y: nearest.y, value: nearest.value, index: nearest.idx });
  }, [points, width]);

  const handleMouseLeave = useCallback(() => {
    setTooltip(t => ({ ...t, visible: false }));
    setHoveredIndex(null);
  }, []);

  // Keep tooltip box inside SVG
  const ttW = 86, ttH = 38;
  const ttX = Math.min(Math.max(tooltip.x - ttW / 2, pad.left), width - ttW - 4);
  const ttY = tooltip.y - ttH - 12 < pad.top ? tooltip.y + 12 : tooltip.y - ttH - 12;

  // Day label for x-axis
  const dayLabel = (idx: number) => {
    const days = data.length;
    if (idx === 0) return 'Day 1';
    if (idx === days - 1) return `Day ${days}`;
    return `Day ${idx + 1}`;
  };

  return (
      <svg
          ref={svgRef}
          viewBox={`0 0 ${width} ${height}`}
          style={{ width: '100%', cursor: 'crosshair', overflow: 'visible' }}
          onMouseMove={handleMouseMove}
          onMouseLeave={handleMouseLeave}
      >
        {/* Area */}
        <path d={areaD} fill={lineColor} opacity="0.07" />

        {/* Grid */}
        {gridPts.map((pt, i) => (
            <g key={i}>
              <line
                  x1={pad.left} y1={pt.y}
                  x2={pad.left + gW} y2={pt.y}
                  stroke={gridColor} strokeWidth="1" strokeDasharray="4,4"
              />
              <text
                  x={pad.left - 6} y={pt.y + 4}
                  textAnchor="end" fontSize="10"
                  fill="var(--pb-text-secondary)"
                  fontFamily="var(--font-mono)"
              >
                {pt.value.toFixed(1)}%
              </text>
            </g>
        ))}

        {/* Axes */}
        <line x1={pad.left} y1={pad.top} x2={pad.left} y2={pad.top + gH} stroke="var(--pb-border)" strokeWidth="1" />
        <line x1={pad.left} y1={pad.top + gH} x2={pad.left + gW} y2={pad.top + gH} stroke="var(--pb-border)" strokeWidth="1" />

        {/* Line */}
        <path d={pathD} fill="none" stroke={lineColor} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />

        {/* Hover crosshair */}
        {tooltip.visible && (
            <line
                x1={tooltip.x} y1={pad.top}
                x2={tooltip.x} y2={pad.top + gH}
                stroke={lineColor} strokeWidth="1" strokeDasharray="3,3" opacity="0.45"
            />
        )}

        {/* Points */}
        {points.map(p => (
            <circle
                key={p.idx}
                cx={p.x} cy={p.y}
                r={hoveredIndex === p.idx ? 5 : 2.5}
                fill={lineColor}
                opacity={hoveredIndex === null || hoveredIndex === p.idx ? 1 : 0.25}
                style={{ transition: 'r 0.1s ease, opacity 0.1s ease' }}
            />
        ))}

        {/* Tooltip */}
        {tooltip.visible && (
            <g>
              <rect
                  x={ttX} y={ttY} width={ttW} height={ttH}
                  rx="4"
                  fill="var(--pb-surface)"
                  stroke={lineColor} strokeWidth="1"
              />
              <text
                  x={ttX + ttW / 2} y={ttY + 13}
                  textAnchor="middle" fontSize="10"
                  fill="var(--pb-text-secondary)"
                  fontFamily="var(--font-mono)"
              >
                {dayLabel(tooltip.index)}
              </text>
              <text
                  x={ttX + ttW / 2} y={ttY + 28}
                  textAnchor="middle" fontSize="12" fontWeight="600"
                  fill={lineColor}
                  fontFamily="var(--font-mono)"
              >
                {tooltip.value.toFixed(2)}%
              </text>
            </g>
        )}
      </svg>
  );
}