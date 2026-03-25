import React from "react";

interface LineChartProps {
  data: number[];
  width?: number;
  height?: number;
  lineColor?: string;
  gridColor?: string;
  label?: string;
}

export function LineChart({
  data,
  width = 400,
  height = 240,
  lineColor = "#3B82F6",
  gridColor = "rgba(30, 45, 69, 0.3)",
  label,
}: LineChartProps) {
  if (data.length < 2) return null;

  const padding = 40;
  const graphWidth = width - padding * 2;
  const graphHeight = height - padding * 2;

  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;

  // Grid lines
  const gridLines = 5;
  const gridLinePoints = [];
  for (let i = 0; i <= gridLines; i++) {
    const y = padding + (graphHeight / gridLines) * i;
    const value = max - (range / gridLines) * i;
    gridLinePoints.push({ y, value });
  }

  // Data points
  const points = data.map((value, idx) => {
    const x = padding + (graphWidth / (data.length - 1)) * idx;
    const y = padding + graphHeight - ((value - min) / range) * graphHeight;
    return { x, y, value };
  });

  // Path data
  const pathData = points.map((p, idx) => `${idx === 0 ? "M" : "L"} ${p.x} ${p.y}`).join(" ");

  // Area fill
  const areaPath = `${pathData} L ${points[points.length - 1].x} ${padding + graphHeight} L ${padding} ${padding + graphHeight} Z`;

  return (
    <svg width={width} height={height} style={{ width: "100%" }}>
      {/* Grid background */}
      <rect x={padding} y={padding} width={graphWidth} height={graphHeight} fill="transparent" />

      {/* Grid lines */}
      {gridLinePoints.map((point, idx) => (
        <g key={`grid-${idx}`}>
          <line
            x1={padding}
            y1={point.y}
            x2={padding + graphWidth}
            y2={point.y}
            stroke={gridColor}
            strokeWidth="1"
            strokeDasharray="4,4"
          />
          <text
            x={padding - 8}
            y={point.y + 4}
            textAnchor="end"
            fontSize="11"
            fill="var(--pb-text-secondary)"
            fontFamily="var(--font-mono)"
          >
            {point.value.toFixed(1)}
          </text>
        </g>
      ))}

      {/* Area fill */}
      <path d={areaPath} fill={lineColor} opacity="0.08" />

      {/* Line */}
      <path d={pathData} fill="none" stroke={lineColor} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />

      {/* Data points */}
      {points.map((p, idx) => (
        <circle key={`point-${idx}`} cx={p.x} cy={p.y} r="3" fill={lineColor} opacity="0.6" />
      ))}

      {/* Y-axis */}
      <line x1={padding} y1={padding} x2={padding} y2={padding + graphHeight} stroke="var(--pb-border)" strokeWidth="1" />

      {/* X-axis */}
      <line
        x1={padding}
        y1={padding + graphHeight}
        x2={padding + graphWidth}
        y2={padding + graphHeight}
        stroke="var(--pb-border)"
        strokeWidth="1"
      />
    </svg>
  );
}
