import { SimulationRequest, SimulationResult, SimulatedPath, PercentileSeries, DistributionBucket } from '../types/simulation';

/**
 * Generate mock Monte Carlo simulation data
 * Structure ready for backend replacement
 */
function generateMockSimulation(request: SimulationRequest, currentValue: number): SimulationResult {
  const { numberOfSimulations, timeHorizonDays, confidenceLevel } = request;
  const dailyReturn = (request.assumedReturnPercent ?? 8) / 100 / 252;
  const dailyVolatility = (request.assumedVolatilityPercent ?? 15) / 100 / Math.sqrt(252);

  // Generate all simulated paths
  const allPaths: SimulatedPath[] = [];
  const finalValues: number[] = [];

  for (let pathId = 0; pathId < numberOfSimulations; pathId++) {
    const values = [{ dayIndex: 0, value: currentValue }];
    let currentPathValue = currentValue;

    for (let day = 1; day <= timeHorizonDays; day++) {
      const randomNormal = Math.sqrt(-2 * Math.log(Math.random())) * Math.cos(2 * Math.PI * Math.random());
      const returnValue = dailyReturn + dailyVolatility * randomNormal;
      currentPathValue *= 1 + returnValue;
      values.push({ dayIndex: day, value: Math.max(currentPathValue, 0) });
    }

    allPaths.push({ pathId, values });
    finalValues.push(currentPathValue);
  }

  // Sort final values to compute percentiles
  finalValues.sort((a, b) => a - b);

  const getPercentileValue = (percentile: number) => {
    const index = Math.floor((percentile / 100) * (finalValues.length - 1));
    return finalValues[index];
  };

  // Generate percentile series (show 10, 50, 90 for main visualization)
  const percentileSeries: PercentileSeries[] = [5, 10, 25, 50, 75, 90, 95].map((percentile) => {
    const percentileValue = getPercentileValue(percentile);
    const growth = (percentileValue - currentValue) / currentValue;

    return {
      percentile,
      finalValue: percentileValue,
      values: allPaths.map((path) => ({
        dayIndex: path.values[path.values.length - 1].dayIndex,
        value: (percentileValue - currentValue) * (path.values[path.values.length - 1].dayIndex / timeHorizonDays) + currentValue,
      })),
    };
  });

  // Calculate statistics
  const mean = finalValues.reduce((a, b) => a + b, 0) / finalValues.length;
  const variance = finalValues.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0) / finalValues.length;
  const stdDev = Math.sqrt(variance);

  // Compute outcomes
  const lossThreshold10 = currentValue * 0.9;
  const lossThreshold20 = currentValue * 0.8;
  const count10Loss = finalValues.filter((v) => v < lossThreshold10).length;
  const count20Loss = finalValues.filter((v) => v < lossThreshold20).length;

  // Value at Risk (95% confidence)
  const var95Index = Math.floor(0.05 * finalValues.length);
  const var95 = finalValues[var95Index];
  const cvar95 = finalValues.slice(0, var95Index + 1).reduce((a, b) => a + b, 0) / (var95Index + 1);

  // Create distribution histogram
  const minValue = Math.min(...finalValues);
  const maxValue = Math.max(...finalValues);
  const bucketCount = 30;
  const bucketWidth = (maxValue - minValue) / bucketCount;

  const distribution: DistributionBucket[] = [];
  for (let i = 0; i < bucketCount; i++) {
    const rangeMin = minValue + i * bucketWidth;
    const rangeMax = rangeMin + bucketWidth;
    const count = finalValues.filter((v) => v >= rangeMin && v < rangeMax).length;

    distribution.push({
      rangeMin,
      rangeMax,
      count,
      percentage: (count / numberOfSimulations) * 100,
    });
  }

  return {
    id: `sim-${Date.now()}`,
    portfolioId: request.portfolioId,
    createdAt: new Date().toISOString(),
    parameters: {
      numberOfSimulations,
      timeHorizonDays,
      confidenceLevel,
    },
    currentPortfolioValue: currentValue,
    allPaths,
    percentileSeries,
    statistics: {
      expectedFinalValue: mean,
      medianFinalValue: finalValues[Math.floor(finalValues.length / 2)],
      stdDeviation: stdDev,
      minValue,
      maxValue,
    },
    outcomes: {
      probabilityOf10PercentLoss: (count10Loss / numberOfSimulations) * 100,
      probabilityOf20PercentLoss: (count20Loss / numberOfSimulations) * 100,
      valueAtRisk95: var95,
      conditionalValueAtRisk95: cvar95,
    },
    distribution,
  };
}

/**
 * Run Monte Carlo simulation
 * TODO: Replace with actual backend call to POST /api/simulations/run
 */
export async function runSimulation(request: SimulationRequest, currentPortfolioValue: number): Promise<SimulationResult> {
  // Simulate network delay
  await new Promise((resolve) => setTimeout(resolve, 1500));

  // TODO: Replace with actual API call
  // const response = await api.post('/simulations/run', request);
  // return response.data;

  return generateMockSimulation(request, currentPortfolioValue);
}

/**
 * Fetch simulation result by ID
 * TODO: Replace with actual backend call to GET /api/simulations/:id
 */
export async function fetchSimulation(simulationId: string): Promise<SimulationResult | null> {
  // TODO: Replace with actual API call
  // const response = await api.get(`/simulations/${simulationId}`);
  // return response.data;

  return null;
}
