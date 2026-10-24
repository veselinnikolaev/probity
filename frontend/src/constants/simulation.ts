export const CONFIDENCE_LEVELS = [90, 95, 99] as const;

export const TIME_HORIZON_OPTIONS = [
  { value: '30', label: '30 days' },
  { value: '90', label: '90 days' },
  { value: '180', label: '180 days' },
  { value: '365', label: '365 days' },
] as const;

export const DEFAULT_SIMULATION_COUNT = 10000;

// Backend bounds from probity-api RunSimulationRequest: @Min(100) @Max(10_000)
export const SIMULATION_COUNT_MIN = 100;
export const SIMULATION_COUNT_MAX = 10000;
