import React, { useRef, useState, useCallback, useEffect } from 'react';

interface LineChartProps {
  data: (number | null)[];
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
                            width: defaultWidth = 600,
                            height: defaultHeight = 240,
                            lineColor = '#3B82F6',
                            gridColor = 'rgba(30, 45, 69, 0.3)',
                            dateRange = '90d',
                          }: LineChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);

  // Track the actual dynamic dimensions of the parent container
  const [dimensions, setDimensions] = useState({ width: defaultWidth, height: defaultHeight });

  const [tooltip, setTooltip] = useState<TooltipState>({
    visible: false, x: 0, y: 0, value: 0, index: 0,
  });
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  // ResizeObserver automatically recalculates the chart's bounds when the container resizes
  useEffect(() => {
    if (!containerRef.current) return;

    const observer = new ResizeObserver((entries) => {
      if (entries[0]) {
        const { width, height } = entries[0].contentRect;
        // Only update if the container has actual dimensions
        if (width > 0 && height > 0) {
          setDimensions({ width, height });
        }
      }
    });

    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  if (data.length < 2) return null;

  // Use the dynamic dimensions instead of the hardcoded props
  const { width, height } = dimensions;

  const pad = { top: 20, right: 24, bottom: 32, left: 52 };
  const gW = width - pad.left - pad.right;
  const gH = height - pad.top - pad.bottom;

  const validData = data.filter((v): v is number => v != null);
  const min = Math.min(...validData);
  const max = Math.max(...validData);
  const range = max - min || 1;

  const points = data.map((v, i) => ({
    x: pad.left + (gW / (data.length - 1)) * i,
    y: v != null ? pad.top + gH - ((v - min) / range) * gH : null,
    value: v,
    idx: i,
  }));

  const gridCount = 4;
  const gridPts = Array.from({ length: gridCount + 1 }, (_, i) => ({
    y: pad.top + (gH / gridCount) * i,
    value: max - (range / gridCount) * i,
  }));

  const pathD = points
      .map((p, i) => {
        if (p.y == null) return null;
        const prev = points[i - 1];
        const move = i === 0 || prev?.y == null ? 'M' : 'L';
        return `${move} ${p.x} ${p.y}`;
      })
      .filter(Boolean)
      .join(' ');  const areaD = `${pathD} L ${points.at(-1)!.x} ${pad.top + gH} L ${pad.left} ${pad.top + gH} Z`;

  const handleMouseMove = useCallback((e: React.MouseEvent<SVGSVGElement>) => {
    const rect = svgRef.current?.getBoundingClientRect();
    if (!rect) return;
    const scaleX = width / rect.width;
    const mouseX = (e.clientX - rect.left) * scaleX;

    // Only snap to points that have a valid y (non-null data)
    const validPoints = points.filter((p): p is typeof p & { y: number; value: number } =>
        p.y != null && p.value != null
    );
    if (validPoints.length === 0) return;

    let nearest = validPoints[0];
    let minDist = Math.abs(nearest.x - mouseX);
    for (const p of validPoints) {
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

  const ttW = 86, ttH = 38;
  const ttX = Math.min(Math.max(tooltip.x - ttW / 2, pad.left), width - ttW - pad.right);
  const ttY = tooltip.y - ttH - 12 < pad.top ? tooltip.y + 12 : tooltip.y - ttH - 12;

  const dayLabel = (idx: number) => {
    const days = data.length;
    if (idx === 0) return 'Day 1';
    if (idx === days - 1) return `Day ${days}`;
    return `Day ${idx + 1}`;
  };

  return (
      <div ref={containerRef} style={{ width: '100%', height: '100%', overflow: 'hidden', position: 'relative' }}>
        <svg
            ref={svgRef}
            viewBox={`0 0 ${width} ${height}`}
            // We use preserveAspectRatio="none" as a fallback, but because viewBox perfectly
            // matches the dynamic width/height, it prevents any stretching.
            preserveAspectRatio="none"
            style={{ width: '100%', height: '100%', cursor: 'crosshair', display: 'block' }}
            onMouseMove={handleMouseMove}
            onMouseLeave={handleMouseLeave}
        >
          <defs>
            <clipPath id="lc-plot-area">
              <rect x={pad.left} y={pad.top} width={gW} height={gH} />
            </clipPath>
          </defs>

          <path d={areaD} fill={lineColor} opacity="0.07" clipPath="url(#lc-plot-area)" />

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

          <line x1={pad.left} y1={pad.top} x2={pad.left} y2={pad.top + gH} stroke="var(--pb-border)" strokeWidth="1" />
          <line x1={pad.left} y1={pad.top + gH} x2={pad.left + gW} y2={pad.top + gH} stroke="var(--pb-border)" strokeWidth="1" />

          <path
              d={pathD} fill="none" stroke={lineColor} strokeWidth="2"
              strokeLinecap="round" strokeLinejoin="round"
              clipPath="url(#lc-plot-area)"
          />

          {tooltip.visible && (
              <line
                  x1={tooltip.x} y1={pad.top}
                  x2={tooltip.x} y2={pad.top + gH}
                  stroke={lineColor} strokeWidth="1" strokeDasharray="3,3" opacity="0.45"
              />
          )}

          <g clipPath="url(#lc-plot-area)">
            {points.filter((p): p is typeof p & { y: number } => p.y != null).map(p => (
                <circle
                    key={p.idx}
                    cx={p.x} cy={p.y}
                    r={hoveredIndex === p.idx ? 5 : 2.5}
                    fill={lineColor}
                    opacity={hoveredIndex === null || hoveredIndex === p.idx ? 1 : 0.25}
                    style={{ transition: 'r 0.1s ease, opacity 0.1s ease' }}
                />
            ))}
          </g>

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
      </div>
  );
}