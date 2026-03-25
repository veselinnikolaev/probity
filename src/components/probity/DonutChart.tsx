import React from "react";

interface DonutChartProps {
  data: Array<{
    label: string;
    value: number;
    color: string;
  }>;
  width?: number;
  height?: number;
}

export function DonutChart({ data, width = 240, height = 240 }: DonutChartProps) {
  const radius = Math.min(width, height) / 2 - 20;
  const innerRadius = radius * 0.6;
  const centerX = width / 2;
  const centerY = height / 2;

  const total = data.reduce((sum, item) => sum + item.value, 0);
  let currentAngle = -Math.PI / 2;

  const segments = data.map((item) => {
    const sliceAngle = (item.value / total) * 2 * Math.PI;
    const startAngle = currentAngle;
    const endAngle = currentAngle + sliceAngle;

    const x1 = centerX + radius * Math.cos(startAngle);
    const y1 = centerY + radius * Math.sin(startAngle);
    const x2 = centerX + radius * Math.cos(endAngle);
    const y2 = centerY + radius * Math.sin(endAngle);

    const ix1 = centerX + innerRadius * Math.cos(startAngle);
    const iy1 = centerY + innerRadius * Math.sin(startAngle);
    const ix2 = centerX + innerRadius * Math.cos(endAngle);
    const iy2 = centerY + innerRadius * Math.sin(endAngle);

    const largeArcFlag = sliceAngle > Math.PI ? 1 : 0;

    const pathData = `
      M ${ix1} ${iy1}
      L ${x1} ${y1}
      A ${radius} ${radius} 0 ${largeArcFlag} 1 ${x2} ${y2}
      L ${ix2} ${iy2}
      A ${innerRadius} ${innerRadius} 0 ${largeArcFlag} 0 ${ix1} ${iy1}
      Z
    `;

    // Calculate label position
    const labelAngle = startAngle + sliceAngle / 2;
    const labelRadius = (radius + innerRadius) / 2;
    const labelX = centerX + labelRadius * Math.cos(labelAngle);
    const labelY = centerY + labelRadius * Math.sin(labelAngle);
    const percentage = ((item.value / total) * 100).toFixed(0);

    currentAngle = endAngle;

    return { item, pathData, labelX, labelY, percentage };
  });

  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} style={{ margin: "0 auto" }}>
      {segments.map((segment, idx) => (
        <g key={idx}>
          <path
            d={segment.pathData}
            fill={segment.item.color}
            stroke="var(--pb-surface)"
            strokeWidth="2"
            opacity="0.9"
          />
          <text
            x={segment.labelX}
            y={segment.labelY}
            textAnchor="middle"
            dominantBaseline="middle"
            fontSize="11"
            fontWeight="600"
            fill="var(--pb-text-primary)"
            fontFamily="var(--font-mono)"
          >
            {segment.percentage}%
          </text>
        </g>
      ))}
    </svg>
  );
}
