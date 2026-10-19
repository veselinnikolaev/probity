import React from "react";

interface SparklineProps {
  data: (number | null)[];
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
  const validData = data.filter((v): v is number => v != null);
  if (!validData || validData.length < 2) return null;

  const min = Math.min(...validData);
  const max = Math.max(...validData);
  const range = max - min || 1;

  const points = data.map((v, i) => ({
    x: (i / (data.length - 1)) * width,
    y: v != null ? height - ((v - min) / range) * (height - 4) - 2 : null,
  }));

  const validPoints = points.filter((p): p is typeof p & { y: number } => p.y != null);

  const polylinePoints = validPoints.map((p) => `${p.x},${p.y}`).join(" ");

  const areaPath = validPoints.length < 2 ? "" :
      `M ${validPoints[0].x},${validPoints[0].y} ` +
      validPoints.slice(1).map((p) => `L ${p.x},${p.y}`).join(" ") +
      ` L ${validPoints[validPoints.length - 1].x},${height} L ${validPoints[0].x},${height} Z`;

  const last = validPoints[validPoints.length - 1];
  const first = validPoints[0];
  // In SVG coords Y is inverted — lower y means higher value, so
  // last.y < first.y means the trend is up (positive).
  const isPositive = last.y <= first.y;
  const lineColor = isPositive ? "#10B981" : "#EF4444";

  const id = `sg-${color.replace("#", "")}-${width}`;

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
        {showArea && <path d={areaPath} fill={`url(#${id})`} />}
        <polyline
            points={polylinePoints}
            fill="none"
            stroke={lineColor}
            strokeWidth="1.5"
            strokeLinejoin="round"
            strokeLinecap="round"
        />
        <circle cx={last.x} cy={last.y} r="2" fill={lineColor} />
      </svg>
  );
}