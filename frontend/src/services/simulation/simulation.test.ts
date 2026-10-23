import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SimulationRequest, SimulationStatus } from '@/types/simulation';

// Mock the axios instance so the async orchestrator is exercised without a network.
vi.mock('@/services/api', () => ({
  default: { post: vi.fn(), get: vi.fn() },
}));

import api from '@/services/api';
import {
  runSimulationAsync,
  fetchSimulationStatus,
  pollSimulationUntilDone,
  runSimulationAndWait,
} from '@/services/simulation';

const post = api.post as unknown as ReturnType<typeof vi.fn>;
const get = api.get as unknown as ReturnType<typeof vi.fn>;

const REQUEST: SimulationRequest = {
  portfolioId: 'portfolio-1',
  numberOfSimulations: 1000,
  timeHorizonDays: 365,
  confidenceLevel: 95,
};

function status(overrides: Partial<SimulationStatus> = {}): SimulationStatus {
  return {
    id: 'sim-1',
    portfolioId: 'portfolio-1',
    status: 'PENDING',
    createdAt: '2026-10-23T00:00:00Z',
    updatedAt: '2026-10-23T00:00:00Z',
    result: null,
    ...overrides,
  };
}

const COMPLETED_RESULT = {
  currentPortfolioValue: 2160,
  parameters: { numberOfSimulations: 1000, timeHorizonDays: 365, confidenceLevel: 0.95 },
  statistics: { expectedFinalValue: 2300, medianFinalValue: 2250, stdDeviation: 200, minValue: 1400, maxValue: 5000 },
  outcomes: {
    probabilityOf10PercentLoss: 12.3,
    probabilityOf20PercentLoss: 4.5,
    valueAtRisk95: 1800,
    conditionalValueAtRisk95: 1600,
  },
  percentileSeries: [{ percentile: 50, values: [{ dayIndex: 0, value: 2160 }], finalValue: 2250 }],
  distribution: [{ rangeMin: 0, rangeMax: 100, count: 1, percentage: 100 }],
};

beforeEach(() => {
  post.mockReset();
  get.mockReset();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('runSimulationAsync', () => {
  it('posts to run-async with the confidence level normalised and returns the status envelope', async () => {
    post.mockResolvedValue({ data: status() });

    const started = await runSimulationAsync(REQUEST);

    expect(post).toHaveBeenCalledTimes(1);
    expect(post.mock.calls[0][0]).toBe('/simulations/run-async');
    expect(post.mock.calls[0][1]).toMatchObject({ confidenceLevel: 0.95 });
    expect(started.status).toBe('PENDING');
  });
});

describe('pollSimulationUntilDone', () => {
  it('polls until COMPLETED and maps the status payload onto a SimulationResult', async () => {
    get
      .mockResolvedValueOnce({ data: status({ status: 'PROCESSING' }) })
      .mockResolvedValueOnce({ data: status({ status: 'COMPLETED', result: COMPLETED_RESULT }) });

    const result = await pollSimulationUntilDone('sim-1', { intervalMs: 0 });

    expect(get).toHaveBeenCalledTimes(2);
    expect(get.mock.calls[0][0]).toBe('/simulations/sim-1/status');
    // Identity comes from the envelope, not the nested result.
    expect(result.id).toBe('sim-1');
    expect(result.portfolioId).toBe('portfolio-1');
    expect(result.statistics.medianFinalValue).toBe(2250);
    expect(result.percentileSeries).toHaveLength(1);
  });

  it('rejects when the simulation reports FAILED', async () => {
    get.mockResolvedValue({ data: status({ status: 'FAILED' }) });

    await expect(pollSimulationUntilDone('sim-1', { intervalMs: 0 })).rejects.toThrow('Simulation failed');
  });

  it('rejects with a timeout when the deadline passes before a terminal status', async () => {
    get.mockResolvedValue({ data: status({ status: 'PROCESSING' }) });

    await expect(
      pollSimulationUntilDone('sim-1', { intervalMs: 0, timeoutMs: 20 }),
    ).rejects.toThrow('Simulation timed out');
  });

  it('rejects as soon as the caller aborts', async () => {
    get.mockResolvedValue({ data: status({ status: 'PROCESSING' }) });
    const controller = new AbortController();

    const promise = pollSimulationUntilDone('sim-1', {
      intervalMs: 5,
      signal: controller.signal,
    });
    controller.abort();

    await expect(promise).rejects.toMatchObject({ name: 'AbortError' });
  });
});

describe('runSimulationAndWait', () => {
  it('starts the run then polls its id to completion', async () => {
    post.mockResolvedValue({ data: status({ id: 'sim-9' }) });
    get.mockResolvedValue({ data: status({ id: 'sim-9', status: 'COMPLETED', result: COMPLETED_RESULT }) });

    const result = await runSimulationAndWait(REQUEST, { intervalMs: 0 });

    expect(post.mock.calls[0][0]).toBe('/simulations/run-async');
    expect(get.mock.calls[0][0]).toBe('/simulations/sim-9/status');
    expect(result.id).toBe('sim-9');
  });
});
