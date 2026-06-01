import React, { useRef, useEffect, useState } from 'react';
import { DistributionBucket } from '../../types/simulation';

interface DistributionHistogramProps {
  distribution: DistributionBucket[];
  currentValue: number;
  medianValue: number;
  height?: number;
}

export function DistributionHistogram({
  distribution,
  currentValue,
  medianValue,
  height = 300,
}: DistributionHistogramProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [tooltip, setTooltip] = useState<{ x: number; y: number; content: string } | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    ctx.scale(dpr, dpr);

    const width = rect.width;
    const chartHeight = height;
    const padding = { top: 20, right: 20, bottom: 50, left: 60 };
    const chartWidth = width - padding.left - padding.right;
    const chartAreaHeight = chartHeight - padding.top - padding.bottom;

    // Find min/max for range
    const minValue = Math.min(...distribution.map((b) => b.rangeMin));
    const maxValue = Math.max(...distribution.map((b) => b.rangeMax));
    const displayRange = maxValue - minValue;

    const maxCount = Math.max(...distribution.map((b) => b.count));

    // Helper functions
    const xScale = (value: number) => ((value - minValue) / displayRange) * chartWidth + padding.left;
    const yScale = (count: number) => chartHeight - padding.bottom - (count / maxCount) * chartAreaHeight;

    // Clear canvas
    ctx.fillStyle = 'var(--pb-bg)';
    ctx.fillRect(0, 0, width, chartHeight);

    // Draw grid
    ctx.strokeStyle = 'var(--pb-border)';
    ctx.lineWidth = 0.5;
    ctx.globalAlpha = 0.5;

    for (let i = 0; i <= 5; i++) {
      const y = padding.top + (i * chartAreaHeight) / 5;
      ctx.beginPath();
      ctx.moveTo(padding.left, y);
      ctx.lineTo(width - padding.right, y);
      ctx.stroke();
    }

    ctx.globalAlpha = 1;

    // Draw loss region (everything below current value in red)
    ctx.fillStyle = '#EF4444';
    ctx.globalAlpha = 0.1;
    const lossRegionX = xScale(currentValue);
    ctx.fillRect(padding.left, padding.top, lossRegionX - padding.left, chartAreaHeight);
    ctx.globalAlpha = 1;

    // Draw loss region border
    ctx.strokeStyle = '#EF4444';
    ctx.lineWidth = 1;
    ctx.globalAlpha = 0.3;
    ctx.setLineDash([2, 2]);
    ctx.beginPath();
    ctx.moveTo(lossRegionX, padding.top);
    ctx.lineTo(lossRegionX, chartHeight - padding.bottom);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.globalAlpha = 1;

    // Draw histogram bars
    const barWidth = chartWidth / distribution.length;
    for (let i = 0; i < distribution.length; i++) {
      const bucket = distribution[i];
      const x = padding.left + i * barWidth;
      const barHeight = (bucket.count / maxCount) * chartAreaHeight;
      const y = yScale(bucket.count);

      // Determine color: red for loss region, blue for gain
      const bucketMidpoint = (bucket.rangeMin + bucket.rangeMax) / 2;
      const isLoss = bucketMidpoint < currentValue;
      ctx.fillStyle = isLoss ? '#EF4444' : '#3B82F6';
      ctx.globalAlpha = 0.7;
      ctx.fillRect(x + 1, y, barWidth - 2, barHeight);

      // Border
      ctx.strokeStyle = isLoss ? '#DC2626' : '#1D4ED8';
      ctx.lineWidth = 0.5;
      ctx.globalAlpha = 1;
      ctx.strokeRect(x + 1, y, barWidth - 2, barHeight);
    }

    // Draw current value marker
    ctx.strokeStyle = 'var(--pb-accent)';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(lossRegionX, padding.top);
    ctx.lineTo(lossRegionX, chartHeight - padding.bottom);
    ctx.stroke();

    // Current value label
    ctx.fillStyle = 'var(--pb-accent)';
    ctx.font = 'bold 11px var(--font-mono)';
    ctx.textAlign = 'center';
    ctx.fillText('Current', lossRegionX, padding.top - 5);

    // Draw median marker
    const medianX = xScale(medianValue);
    ctx.strokeStyle = '#10B981';
    ctx.lineWidth = 1.5;
    ctx.setLineDash([4, 4]);
    ctx.beginPath();
    ctx.moveTo(medianX, padding.top);
    ctx.lineTo(medianX, chartHeight - padding.bottom);
    ctx.stroke();
    ctx.setLineDash([]);

    // Median label
    ctx.fillStyle = '#10B981';
    ctx.font = '11px var(--font-mono)';
    ctx.fillText('Median', medianX, padding.top - 5);

    // Draw axes
    ctx.strokeStyle = 'var(--pb-border)';
    ctx.lineWidth = 1;
    ctx.globalAlpha = 1;
    ctx.beginPath();
    ctx.moveTo(padding.left, padding.top);
    ctx.lineTo(padding.left, chartHeight - padding.bottom);
    ctx.lineTo(width - padding.right, chartHeight - padding.bottom);
    ctx.stroke();

    // Y-axis labels (frequency)
    ctx.fillStyle = 'var(--pb-text-secondary)';
    ctx.font = '11px var(--font-mono)';
    ctx.textAlign = 'right';
    ctx.textBaseline = 'middle';

    for (let i = 0; i <= 5; i++) {
      const count = Math.round((i * maxCount) / 5);
      const y = chartHeight - padding.bottom - (i * chartAreaHeight) / 5;
      ctx.fillText(count.toString(), padding.left - 10, y);
    }

    // X-axis labels (value)
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';

    for (let i = 0; i <= 5; i++) {
      const value = minValue + (i * displayRange) / 5;
      const x = padding.left + (i * chartWidth) / 5;
      ctx.fillText(`$${Math.round(value).toLocaleString()}`, x, chartHeight - padding.bottom + 15);
    }

    // Title
    ctx.fillStyle = 'var(--pb-text-primary)';
    ctx.font = 'bold 14px var(--font-body)';
    ctx.textAlign = 'left';
    ctx.fillText('Distribution of Final Portfolio Values', padding.left, 5);
  }, [distribution, currentValue, medianValue, height]);

  const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!canvasRef.current || distribution.length === 0) return;

    const rect = canvasRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const canvasWidth = rect.width;
    const padding = { left: 60, right: 20 };
    const chartWidth = canvasWidth - padding.left - padding.right;

    if (x < padding.left || x > canvasWidth - padding.right) {
      setTooltip(null);
      return;
    }

    const fraction = (x - padding.left) / chartWidth;
    const minValue = Math.min(...distribution.map((b) => b.rangeMin));
    const maxValue = Math.max(...distribution.map((b) => b.rangeMax));
    const displayRange = maxValue - minValue;
    const hoverValue = minValue + fraction * displayRange;

    const bucket = distribution.find(
      (b) => hoverValue >= b.rangeMin && hoverValue < b.rangeMax
    );

    if (bucket) {
      setTooltip({
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
        content: `${bucket.count} outcomes\n$${Math.round(bucket.rangeMin).toLocaleString()} - $${Math.round(bucket.rangeMax).toLocaleString()}\n${bucket.percentage.toFixed(1)}%`,
      });
    }
  };

  const handleMouseLeave = () => {
    setTooltip(null);
  };

  return (
    <div ref={containerRef} style={{ position: 'relative', width: '100%' }}>
      <canvas
        ref={canvasRef}
        style={{
          display: 'block',
          width: '100%',
          height: `${height}px`,
          cursor: 'crosshair',
        }}
        onMouseMove={handleMouseMove}
        onMouseLeave={handleMouseLeave}
      />
      {tooltip && (
          <div
              style={{
                position: 'absolute',
                top: tooltip.y + 12,
                left: tooltip.x + 12,
                zIndex: 100,
                backgroundColor: '#131B2E',
                border: '1px solid #1E2D45',
                borderRadius: '6px',
                padding: '7px 10px',
                pointerEvents: 'none',
                boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
                whiteSpace: 'pre-line',       // preserves the \n line breaks in content
                fontFamily: 'var(--font-mono)',
                fontSize: '12px',
                color: 'var(--pb-text-primary)',
                lineHeight: 1.6,
              }}
          >
            {tooltip.content}
          </div>
      )}
    </div>
  );
}
