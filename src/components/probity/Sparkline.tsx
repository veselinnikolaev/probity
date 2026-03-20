import React from "react";

interface SparklineProps {
  data: number[];
  width?: number;
  height?: number;
  color?: string;
  showArea?: boolean;
}

export function Sparkline({
  data,
  width = 80,
  height = 32,
  color = "#3B82F6",
  showArea = true,
}: SparklineProps) {
  if (!data || data.length < 2) return null;

  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;

  const points = data.map((v, i) => ({
    x: (i / (data.length - 1)) * width,
    y: height - ((v - min) / range) * (height - 4) - 2,
  }));

  const polylinePoints = points.map((p) => `${p.x},${p.y}`).join(" ");

  // Area path: go down to bottom-right, across bottom, up to bottom-left
  const areaPath =
    `M ${points[0].x},${points[0].y} ` +
    points.slice(1).map((p) => `L ${p.x},${p.y}`).join(" ") +
    ` L ${points[points.length - 1].x},${height} L ${points[0].x},${height} Z`;

  const id = `sg-${color.replace("#", "")}-${width}`;

  const isPositive = data[data.length - 1] >= data[0];
  const lineColor = isPositive ? "#10B981" : "#EF4444";
  const areaColor = isPositive ? "rgba(16,185,129," : "rgba(239,68,68,";

  return (
    <svg
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      fill="none"
      style={{ overflow: "visible" }}
    >
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={lineColor} stopOpacity="0.2" />
          <stop offset="100%" stopColor={lineColor} stopOpacity="0" />
        </linearGradient>
      </defs>
      {showArea && (
        <path d={areaPath} fill={`url(#${id})`} />
      )}
      <polyline
        points={polylinePoints}
        fill="none"
        stroke={lineColor}
        strokeWidth="1.5"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      {/* End dot */}
      <circle
        cx={points[points.length - 1].x}
        cy={points[points.length - 1].y}
        r="2"
        fill={lineColor}
      />
    </svg>
  );
}
