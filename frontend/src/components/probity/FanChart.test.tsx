import { beforeAll, describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { FanChart, computeFanChartRange } from '@/components/probity/FanChart';
import { PercentileSeries, SimulatedPath } from '@/types/simulation';

// jsdom does not implement canvas 2D; stub it to return null (what a headless env should
// see) so FanChart's drawing effect early-returns instead of jsdom raising "Not
// implemented". Rendering is what these tests assert — the drawing math is covered by
// computeFanChartRange directly.
beforeAll(() => {
  HTMLCanvasElement.prototype.getContext = (() => null) as unknown as typeof HTMLCanvasElement.prototype.getContext;
});

// ── y-range union (Q9b) ──────────────────────────────────────────────────────

/**
 * The scenario Q9b's sampling guarantee exists for: an extreme path sits at an index an
 * evenly-spaced sample would skip, but the backend always keeps the global min/max paths,
 * so it is still in the sample and the range it computes is the full-data range.
 */
function series(percentile: number, finalValue: number): PercentileSeries {
  const values = Array.from({ length: 11 }, (_, dayIndex) => ({
    dayIndex,
    // Linear from 2160 to finalValue, mirroring how FanChart draws the bands.
    value: 2160 + (finalValue - 2160) * (dayIndex / 10),
  }));
  return { percentile, values, finalValue };
}

function path(pathId: number, flatValue: number): SimulatedPath {
  return {
    pathId,
    values: Array.from({ length: 11 }, (_, dayIndex) => ({ dayIndex, value: flatValue })),
  };
}

describe('computeFanChartRange', () => {
  it('unions percentile series and sampled paths so an extreme sample path is not clipped', () => {
    const percentileSeries = [series(5, 1800), series(50, 2200), series(95, 2600)];

    // Evenly-spaced-looking sample (ids 0, 10, 20) plus the extreme path (id 7) that the
    // global min/max rule is required to keep. Its value is above every percentile band.
    const sampledPaths = [path(0, 2100), path(7, 5000), path(10, 2150), path(20, 2200)];

    const range = computeFanChartRange(sampledPaths, percentileSeries, 2160);

    // Floor comes from the series (their low band dips below every sampled path).
    expect(range.min).toBeCloseTo(1800, 6);
    // Ceiling comes from the extreme path, not from the 95th band — proof the paths widen
    // the range beyond the series and the extreme is inside it.
    expect(range.max).toBe(5000);
  });

  it('is valid from the series alone while the paths are still loading', () => {
    const percentileSeries = [series(5, 1800), series(95, 2600)];

    const range = computeFanChartRange([], percentileSeries, 2160);

    expect(range).toEqual({ min: 1800, max: 2600 });
  });

  it('falls back to the current value when there is nothing else', () => {
    expect(computeFanChartRange([], [], 2160)).toEqual({ min: 2160, max: 2160 });
  });
});

// ── rendering ────────────────────────────────────────────────────────────────

describe('FanChart', () => {
  it('renders a canvas with the sampled paths and percentile bands', () => {
    const percentileSeries = [series(10, 1900), series(50, 2200), series(90, 2500)];
    const sampledPaths = [path(0, 2100), path(7, 5000)];

    const { container } = render(
      <FanChart
        paths={sampledPaths}
        percentileSeries={percentileSeries}
        currentValue={2160}
        timeHorizonDays={10}
      />,
    );

    // jsdom has no 2D context, so the drawing effect early-returns — but the element the
    // chart draws into must be mounted regardless of whether paths have arrived yet.
    expect(container.querySelector('canvas')).toBeTruthy();
  });

  it('renders before the sampled paths arrive (bands-only first paint)', () => {
    const { container } = render(
      <FanChart
        paths={[]}
        pathsLoading
        percentileSeries={[series(50, 2200)]}
        currentValue={2160}
        timeHorizonDays={10}
      />,
    );

    expect(container.querySelector('canvas')).toBeTruthy();
  });
});
