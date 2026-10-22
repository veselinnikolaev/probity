import React, { useRef, useEffect, useState } from 'react';
import { SimulatedPath, PercentileSeries } from '@/types/simulation';

interface FanChartProps {
  /** Sampled paths from GET /{id}/paths (Q9b). May be empty while they are still loading. */
  paths: SimulatedPath[];
  percentileSeries: PercentileSeries[];
  currentValue: number;
  timeHorizonDays: number;
  /** True while the sampled paths are still being fetched; drawn as a caption only. */
  pathsLoading?: boolean;
  height?: number;
}

/**
 * The y-range the fan is drawn in: the union of the percentile series values, the sampled
 * paths, and the current value.
 *
 * <p>Q9b: the series and the paths are both used — never the paths alone. The series gives a
 * stable range the moment a result arrives (they are always present and never sampled),
 * and the sampled paths contribute their extremes: the backend guarantees the global
 * minimum and maximum paths are always in the sample, so the union is the full-data range
 * and no path is clipped. Unioning with the current value keeps the day-0 baseline in view.
 */
export function computeFanChartRange(
  paths: SimulatedPath[],
  percentileSeries: PercentileSeries[],
  currentValue: number,
): { min: number; max: number } {
  let min = currentValue;
  let max = currentValue;

  for (const series of percentileSeries) {
    for (const point of series.values) {
      min = Math.min(min, point.value);
      max = Math.max(max, point.value);
    }
  }

  for (const path of paths) {
    for (const point of path.values) {
      min = Math.min(min, point.value);
      max = Math.max(max, point.value);
    }
  }

  return { min, max };
}

export function FanChart({
  paths,
  percentileSeries,
  currentValue,
  timeHorizonDays,
  pathsLoading = false,
  height = 400,
}: FanChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [hoveredDay, setHoveredDay] = useState<number | null>(null);
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

    // Find min/max values for scaling. Union of percentiles + sampled paths + current
    // value — see computeFanChartRange. Paths may be empty while loading; the percentile
    // series alone already yield a valid, unclipped range in that window.
    const { min: minValue, max: maxValue } = computeFanChartRange(paths, percentileSeries, currentValue);

    const valueRange = maxValue - minValue || 1;
    const valueBuffer = valueRange * 0.1;
    const scaledMin = minValue - valueBuffer;
    const scaledMax = maxValue + valueBuffer;
    const displayRange = scaledMax - scaledMin;

    // Helper functions
    const xScale = (dayIndex: number) => (dayIndex / timeHorizonDays) * chartWidth + padding.left;
    const yScale = (value: number) => chartHeight - padding.bottom - ((value - scaledMin) / displayRange) * chartAreaHeight;

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

    for (let i = 0; i <= 10; i++) {
      const x = padding.left + (i * chartWidth) / 10;
      ctx.beginPath();
      ctx.moveTo(x, padding.top);
      ctx.lineTo(x, chartHeight - padding.bottom);
      ctx.stroke();
    }

    ctx.globalAlpha = 1;

    // Draw all simulated paths (very faint gray)
    ctx.strokeStyle = '#4B5563';
    ctx.lineWidth = 0.3;
    ctx.globalAlpha = 0.08;

    for (const path of paths) {
      ctx.beginPath();
      for (let i = 0; i < path.values.length; i++) {
        const x = xScale(path.values[i].dayIndex);
        const y = yScale(path.values[i].value);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.stroke();
    }

    ctx.globalAlpha = 1;
    ctx.lineWidth = 1.5;

    // Draw emphasized percentile paths (10th, 50th, 90th)
    const p10 = percentileSeries.find((s) => s.percentile === 10);
    const p50 = percentileSeries.find((s) => s.percentile === 50);
    const p90 = percentileSeries.find((s) => s.percentile === 90);

    // 10th percentile (red). Bands draw even while the sampled paths are still loading —
    // they come entirely from the series, never from the paths.
    if (p10 && p10.values.length > 1) {
      const pathLength = p10.values.length;
      ctx.strokeStyle = '#EF4444';
      ctx.beginPath();
      for (let i = 0; i < pathLength; i++) {
        const fraction = i / (pathLength - 1);
        const value = currentValue + (p10.finalValue - currentValue) * fraction;
        const x = xScale(i);
        const y = yScale(value);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.stroke();
    }

    // 50th percentile (blue)
    if (p50 && p50.values.length > 1) {
      const pathLength = p50.values.length;
      ctx.strokeStyle = '#3B82F6';
      ctx.beginPath();
      for (let i = 0; i < pathLength; i++) {
        const fraction = i / (pathLength - 1);
        const value = currentValue + (p50.finalValue - currentValue) * fraction;
        const x = xScale(i);
        const y = yScale(value);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.stroke();
    }

    // 90th percentile (green)
    if (p90 && p90.values.length > 1) {
      const pathLength = p90.values.length;
      ctx.strokeStyle = '#10B981';
      ctx.beginPath();
      for (let i = 0; i < pathLength; i++) {
        const fraction = i / (pathLength - 1);
        const value = currentValue + (p90.finalValue - currentValue) * fraction;
        const x = xScale(i);
        const y = yScale(value);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.stroke();
    }

    // Draw current value baseline
    ctx.strokeStyle = 'var(--pb-accent)';
    ctx.lineWidth = 1;
    ctx.globalAlpha = 0.5;
    ctx.setLineDash([4, 4]);
    const baselineY = yScale(currentValue);
    ctx.beginPath();
    ctx.moveTo(padding.left, baselineY);
    ctx.lineTo(width - padding.right, baselineY);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.globalAlpha = 1;

    // Draw axes
    ctx.strokeStyle = 'var(--pb-border)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(padding.left, padding.top);
    ctx.lineTo(padding.left, chartHeight - padding.bottom);
    ctx.lineTo(width - padding.right, chartHeight - padding.bottom);
    ctx.stroke();

    // Draw axis labels
    ctx.fillStyle = 'var(--pb-text-secondary)';
    ctx.font = '11px var(--font-mono)';
    ctx.textAlign = 'right';
    ctx.textBaseline = 'top';

    for (let i = 0; i <= 5; i++) {
      const value = scaledMin + (i * displayRange) / 5;
      const y = chartHeight - padding.bottom - (i * chartAreaHeight) / 5;
      ctx.fillText(`$${Math.round(value).toLocaleString()}`, padding.left - 10, y - 5);
    }

    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    for (let i = 0; i <= 10; i++) {
      const day = Math.round((i * timeHorizonDays) / 10);
      const x = padding.left + (i * chartWidth) / 10;
      ctx.fillText(`${day}d`, x, chartHeight - padding.bottom + 15);
    }

    // Draw title. While the sampled paths are still in flight the caption says so, so the
    // first paint (summary + bands) is legibly incomplete rather than silently thin.
    ctx.fillStyle = 'var(--pb-text-primary)';
    ctx.font = 'bold 14px var(--font-body)';
    ctx.textAlign = 'left';
    ctx.fillText(
      pathsLoading ? 'Simulated Portfolio Paths — loading sample…' : 'Simulated Portfolio Paths',
      padding.left,
      5,
    );
  }, [paths, percentileSeries, currentValue, timeHorizonDays, height, pathsLoading]);

  const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!canvasRef.current) return;

    const rect = canvasRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const canvasWidth = rect.width;
    const padding = { left: 60, right: 20 };
    const chartWidth = canvasWidth - padding.left - padding.right;

    if (x < padding.left || x > canvasWidth - padding.right) {
      setTooltip(null);
      return;
    }

    const dayFraction = (x - padding.left) / chartWidth;
    const day = Math.round(dayFraction * timeHorizonDays);

    const p10 = percentileSeries.find((s) => s.percentile === 10);
    const p50 = percentileSeries.find((s) => s.percentile === 50);
    const p90 = percentileSeries.find((s) => s.percentile === 90);

    if (p10 && p50 && p90) {
      const fraction = dayFraction;
      const v10 = currentValue + (p10.finalValue - currentValue) * fraction;
      const v50 = currentValue + (p50.finalValue - currentValue) * fraction;
      const v90 = currentValue + (p90.finalValue - currentValue) * fraction;

      setHoveredDay(day);
      setTooltip({
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
        content: `Day ${day}\n10th: $${Math.round(v10).toLocaleString()}\n50th: $${Math.round(v50).toLocaleString()}\n90th: $${Math.round(v90).toLocaleString()}`,
      });
    }
  };

  const handleMouseLeave = () => {
    setTooltip(null);
    setHoveredDay(null);
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
