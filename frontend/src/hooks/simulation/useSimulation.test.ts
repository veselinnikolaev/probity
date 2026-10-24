import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { SimulationRequest, SimulationResult, SimulationStatus } from '@/types/simulation';

// The hook is what M4a wires to the async path; the orchestrator itself is covered in
// services/simulation/simulation.test.ts, so here it is mocked and only the hook's own
// lifecycle (abort on unmount, abort on supersede) is asserted.
vi.mock('@/services/simulation', () => ({
  runSimulationAndWait: vi.fn(),
}));

import { runSimulationAndWait } from '@/services/simulation';
import { useSimulation } from '@/hooks/simulation/useSimulation';

const runAndWait = runSimulationAndWait as unknown as ReturnType<typeof vi.fn>;

const REQUEST: SimulationRequest = {
  portfolioId: 'portfolio-1',
  numberOfSimulations: 1000,
  timeHorizonDays: 365,
  confidenceLevel: 95,
};

const RESULT = { id: 'sim-1' } as unknown as SimulationResult;

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

const aborted = () => new DOMException('Aborted', 'AbortError');

afterEach(() => {
  runAndWait.mockReset();
});

describe('useSimulation (M4a async flow)', () => {
  it('runs through the async orchestrator and exposes the completed result', async () => {
    runAndWait.mockResolvedValue(RESULT);

    const { result } = renderHook(() => useSimulation('portfolio-1'));

    await act(async () => {
      await result.current.run(REQUEST);
    });

    expect(runAndWait).toHaveBeenCalledTimes(1);
    expect(runAndWait.mock.calls[0][0]).toBe(REQUEST);
    expect(result.current.state).toEqual({ status: 'success', data: RESULT });
  });

  it('aborts the in-flight run when the hook unmounts and ignores its late result', async () => {
    const pending = deferred<SimulationResult>();
    runAndWait.mockImplementation((_req: SimulationRequest, opts: { signal: AbortSignal }) => {
      // Reject with AbortError once the hook's controller aborts, mirroring the real
      // poll loop: the unmount must cancel, not merely ignore, the run.
      return Promise.race([
        pending.promise,
        new Promise<SimulationResult>((_resolve, reject) => {
          opts.signal.addEventListener('abort', () => reject(aborted()));
        }),
      ]);
    });

    const { result, unmount } = renderHook(() => useSimulation('portfolio-1'));

    let runPromise!: Promise<unknown>;
    act(() => {
      runPromise = result.current.run(REQUEST);
    });
    expect(result.current.state.status).toBe('loading');

    const signal: AbortSignal = runAndWait.mock.calls[0][1].signal;
    expect(signal.aborted).toBe(false);

    unmount();

    expect(signal.aborted).toBe(true);
    await act(async () => {
      await runPromise;
    });
  });

  it('aborts the previous run when a second run starts', async () => {
    const first = deferred<SimulationResult>();
    const second = deferred<SimulationResult>();
    runAndWait
      .mockImplementationOnce((_req: SimulationRequest, opts: { signal: AbortSignal }) =>
        Promise.race([
          first.promise,
          new Promise<SimulationResult>((_resolve, reject) => {
            opts.signal.addEventListener('abort', () => reject(aborted()));
          }),
        ]),
      )
      .mockImplementationOnce(() => second.promise);

    const { result } = renderHook(() => useSimulation('portfolio-1'));

    let firstRun!: Promise<unknown>;
    act(() => {
      firstRun = result.current.run(REQUEST);
    });
    const firstSignal: AbortSignal = runAndWait.mock.calls[0][1].signal;

    let secondRun!: Promise<unknown>;
    act(() => {
      secondRun = result.current.run(REQUEST);
    });
    expect(firstSignal.aborted).toBe(true);

    second.resolve(RESULT);
    await act(async () => {
      await Promise.allSettled([firstRun, secondRun]);
    });

    await waitFor(() => {
      expect(result.current.state).toEqual({ status: 'success', data: RESULT });
    });
  });
});
