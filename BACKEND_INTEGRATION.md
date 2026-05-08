# Backend Integration Guide for Monte Carlo Simulation

This guide explains how to wire up the backend API endpoints to the frontend Monte Carlo Simulation screen.

---

## Overview

The frontend is ready for backend integration with:

1. ✅ Typed interfaces for all request/response contracts
2. ✅ Mock data adapter for testing
3. ✅ Service layer abstraction in `simulationService.ts`
4. ✅ Async state handling in page component
5. ✅ Full error handling (validation, network, server, permission)

---

## Current Architecture

### Frontend Service Layer

`src/api/simulationService.ts` contains:

```typescript
export async function runSimulation(
  request: SimulationRequest,
  currentPortfolioValue: number
): Promise<SimulationResult> {
  // Currently: generates mock data
  // TODO: Replace with actual API call
  return generateMockSimulation(request, currentPortfolioValue);
}

export async function fetchSimulation(simulationId: string): Promise<SimulationResult | null> {
  // Currently: returns null
  // TODO: Replace with actual API call
  return null;
}
```

### How to Replace Mock Data

**Option 1: Direct axios call (simple)**

```typescript
import axios from 'axios';

const api = axios.create({
  baseURL: process.env.REACT_APP_API_URL || 'http://localhost:3000/api',
  withCredentials: true,
});

export async function runSimulation(
  request: SimulationRequest,
  currentPortfolioValue: number
): Promise<SimulationResult> {
  try {
    const response = await api.post<SimulationResponse>('/simulations/run', request);
    if (response.data.success && response.data.data) {
      return response.data.data;
    }
    throw new Error(response.data.error?.message || 'Simulation failed');
  } catch (error) {
    if (axios.isAxiosError(error)) {
      throw new Error(error.response?.data?.error?.message || 'Network error');
    }
    throw error;
  }
}
```

**Option 2: Existing API client (recommended)**

```typescript
import { api } from './api';

export async function runSimulation(
  request: SimulationRequest,
  currentPortfolioValue: number
): Promise<SimulationResult> {
  const response = await api.post<SimulationResponse>('/simulations/run', request);
  return response.data.data || throwErrorResponse(response.data);
}
```

---

## Suggested API Endpoints

### 1. POST /api/simulations/run

**Purpose**: Run a new Monte Carlo simulation

**Request**:
```typescript
interface SimulationRequest {
  portfolioId: string;
  numberOfSimulations: number;        // 1–100,000
  timeHorizonDays: number;            // 30, 90, 180, 365
  confidenceLevel: number;            // 90, 95, 99
  assumedReturnPercent?: number;      // Optional override
  assumedVolatilityPercent?: number;  // Optional override
}
```

**Response**:
```typescript
interface SimulationResponse {
  success: boolean;
  data?: SimulationResult;
  error?: {
    code: string;
    message: string;
  };
}

interface SimulationResult {
  id: string;
  portfolioId: string;
  createdAt: string;
  parameters: SimulationRequest;
  currentPortfolioValue: number;
  allPaths: SimulatedPath[];          // 10K paths
  percentileSeries: PercentileSeries[];
  statistics: {
    expectedFinalValue: number;
    medianFinalValue: number;
    stdDeviation: number;
    minValue: number;
    maxValue: number;
  };
  outcomes: {
    probabilityOf10PercentLoss: number;
    probabilityOf20PercentLoss: number;
    valueAtRisk95: number;
    conditionalValueAtRisk95: number;
  };
  distribution: DistributionBucket[];  // 30 buckets
}
```

**Success Response** (200 OK):
```json
{
  "success": true,
  "data": {
    "id": "sim-1234567890",
    "portfolioId": "port-abc123",
    "createdAt": "2026-05-08T12:30:45Z",
    "parameters": {
      "portfolioId": "port-abc123",
      "numberOfSimulations": 10000,
      "timeHorizonDays": 365,
      "confidenceLevel": 95
    },
    "currentPortfolioValue": 100000,
    "allPaths": [
      {
        "pathId": 0,
        "values": [
          { "dayIndex": 0, "value": 100000 },
          { "dayIndex": 1, "value": 100234.5 },
          ...
        ]
      },
      ...
    ],
    "percentileSeries": [
      {
        "percentile": 10,
        "finalValue": 87230.15,
        "values": [...]
      },
      ...
    ],
    "statistics": {
      "expectedFinalValue": 110234.5,
      "medianFinalValue": 108765.3,
      "stdDeviation": 15234.2,
      "minValue": 62000,
      "maxValue": 180000
    },
    "outcomes": {
      "probabilityOf10PercentLoss": 25.3,
      "probabilityOf20PercentLoss": 8.1,
      "valueAtRisk95": 87230.15,
      "conditionalValueAtRisk95": 82456.7
    },
    "distribution": [
      {
        "rangeMin": 62000,
        "rangeMax": 65333.33,
        "count": 15,
        "percentage": 0.15
      },
      ...
    ]
  }
}
```

**Error Response** (400, 500):
```json
{
  "success": false,
  "error": {
    "code": "INVALID_PORTFOLIO",
    "message": "Portfolio not found or no positions"
  }
}
```

**Error Cases**:
- `400 INVALID_REQUEST`: Missing/invalid parameters
- `400 INVALID_PORTFOLIO`: Portfolio doesn't exist or empty
- `401 UNAUTHORIZED`: User not authenticated
- `403 FORBIDDEN`: User doesn't have access to this portfolio
- `500 SERVER_ERROR`: Simulation failed to compute

---

### 2. GET /api/simulations/:id

**Purpose**: Fetch previously-run simulation by ID

**Request**:
```
GET /api/simulations/sim-1234567890
```

**Response**: Same as POST /simulations/run success response

**Status Codes**:
- `200 OK`: Simulation found
- `404 NOT_FOUND`: Simulation doesn't exist or expired
- `401 UNAUTHORIZED`: User not authenticated

---

### 3. GET /api/simulations

**Purpose** (Optional): List user's simulation history

**Query Parameters**:
```
?portfolioId=port-abc123
?limit=10
?offset=0
?sortBy=createdAt
```

**Response**:
```json
{
  "success": true,
  "data": {
    "items": [
      { "id": "sim-1", "portfolioId": "port-1", "createdAt": "...", ... },
      { "id": "sim-2", "portfolioId": "port-1", "createdAt": "...", ... }
    ],
    "total": 2,
    "limit": 10,
    "offset": 0
  }
}
```

---

## Backend Implementation Notes

### Algorithm Choice

The frontend uses a standard **Monte Carlo simulation** with:

1. **Geometric Brownian Motion** for path generation:
   ```
   S(t+dt) = S(t) * exp((μ - σ²/2)*dt + σ*√dt*Z)
   
   where:
     μ = daily return (assumed or actual)
     σ = daily volatility (annualized / √252)
     Z = random normal (0, 1)
   ```

2. **Box-Muller transform** for random normal distribution (frontend mock)

3. **Percentile computation** via sorting final values (O(n log n))

4. **Distribution bucketing** into 30 equal-width bins

### Suggested Backend Stack

**Python** (recommended for numerical work):
```python
# Use numpy for vectorized operations
import numpy as np

def generate_paths(S0, mu, sigma, T, dt, n_paths):
    n_steps = int(T / dt)
    paths = np.zeros((n_paths, n_steps))
    paths[:, 0] = S0
    
    for t in range(1, n_steps):
        Z = np.random.standard_normal(n_paths)
        paths[:, t] = paths[:, t-1] * np.exp(
            (mu - 0.5 * sigma**2) * dt + sigma * np.sqrt(dt) * Z
        )
    
    return paths
```

**Node.js** (if building in TypeScript):
```typescript
// Use numerical libraries like jstat or simple-statistics
import jStat from 'jstat';

function generatePaths(S0, mu, sigma, T, dt, nPaths) {
  const nSteps = Math.ceil(T / dt);
  const paths = Array(nPaths).fill(0).map(() => Array(nSteps));
  
  for (let i = 0; i < nPaths; i++) {
    paths[i][0] = S0;
    for (let t = 1; t < nSteps; t++) {
      const Z = jStat.normal.sample(0, 1);
      paths[i][t] = paths[i][t-1] * Math.exp(
        (mu - 0.5 * sigma**2) * dt + sigma * Math.sqrt(dt) * Z
      );
    }
  }
  
  return paths;
}
```

### Performance Optimization

1. **Vectorization**: Use vectorized operations (NumPy, CUDA)
2. **Caching**: Store results with TTL (e.g., 24 hours)
3. **Memoization**: For repeated simulations with same parameters
4. **Queue**: For long-running simulations (> 5 seconds)
5. **Progress**: WebSocket updates for interactive feedback

### Data Storage

Store simulation results in database:

```sql
CREATE TABLE simulations (
  id VARCHAR(36) PRIMARY KEY,
  portfolio_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(36) NOT NULL,
  
  -- Parameters
  num_simulations INT NOT NULL,
  time_horizon_days INT NOT NULL,
  confidence_level INT NOT NULL,
  assumed_return DECIMAL(5,2),
  assumed_volatility DECIMAL(5,2),
  
  -- Results (JSON or columns)
  current_portfolio_value DECIMAL(15,2) NOT NULL,
  all_paths LONGTEXT NOT NULL,  -- JSON
  percentile_series LONGTEXT NOT NULL,  -- JSON
  statistics LONGTEXT NOT NULL,  -- JSON
  outcomes LONGTEXT NOT NULL,    -- JSON
  distribution LONGTEXT NOT NULL,  -- JSON
  
  -- Metadata
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP,  -- For cleanup
  
  FOREIGN KEY (portfolio_id) REFERENCES portfolios(id),
  FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX (portfolio_id, created_at)
);
```

---

## Error Handling

### Frontend Error Handling (Already Implemented)

The component handles 4 error categories:

1. **Validation Errors** (400):
   - Shown in DataBoundary error state
   - Message: "Invalid simulation parameters. Ensure portfolio has positions."

2. **Network Errors** (timeout, connection):
   - Shown as transient error
   - "Lost connection. Reconnecting…"

3. **Server Errors** (500):
   - Shown with retry button
   - "Simulation failed. Try again?"

4. **Permission Errors** (401, 403):
   - "You don't have access to this portfolio."

### Backend Error Response Format

Always return consistent error structure:

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable message",
    "details": {
      "field": "portfolio_id",
      "issue": "Portfolio not found"
    }
  }
}
```

**Error Codes**:
```
INVALID_REQUEST        // 400: Missing/invalid fields
INVALID_PORTFOLIO      // 400: Portfolio doesn't exist
EMPTY_PORTFOLIO        // 400: No positions to simulate
INVALID_PARAMETERS     // 400: Parameter out of range
UNAUTHORIZED           // 401: Not authenticated
FORBIDDEN              // 403: Access denied
SIMULATION_FAILED      // 500: Algorithm error
TIMEOUT                // 504: Took too long
DATABASE_ERROR         // 500: DB failure
```

---

## Integration Checklist

- [ ] Implement POST /api/simulations/run endpoint
- [ ] Implement GET /api/simulations/:id endpoint
- [ ] Return correct SimulationResult structure
- [ ] Handle all error cases with appropriate codes
- [ ] Store results in database
- [ ] Set up result expiration (e.g., 24-hour TTL)
- [ ] Add API documentation (Swagger/OpenAPI)
- [ ] Load test with 10K paths × 365 days
- [ ] Verify response times < 5 seconds (or use queue)
- [ ] Test with invalid portfolios
- [ ] Test with empty portfolios
- [ ] Test with invalid parameters
- [ ] Test with unauthorized access
- [ ] Deploy to staging
- [ ] Update REACT_APP_API_URL environment variable
- [ ] Test end-to-end in preview
- [ ] Monitor error logs

---

## Environment Variables

Frontend expects these environment variables:

```env
REACT_APP_API_URL=http://localhost:3000/api
REACT_APP_ENV=development
```

Update `src/api/api.ts` to use:

```typescript
const API_URL = process.env.REACT_APP_API_URL || 'http://localhost:3000/api';

const api = axios.create({
  baseURL: API_URL,
  withCredentials: true,
});
```

---

## WebSocket Support (Future Enhancement)

For long-running simulations (> 5 sec), consider WebSocket progress updates:

```typescript
// Frontend usage
const ws = new WebSocket('ws://localhost:3000/api/simulations/progress');
ws.onmessage = (event) => {
  const { simulationId, progress, status } = JSON.parse(event.data);
  // Update UI: progress bar, eta, etc.
};

// Backend sends
{
  "simulationId": "sim-123",
  "progress": 45,  // 45%
  "status": "generating_paths",
  "eta": 12  // seconds remaining
}
```

---

## Testing

### Test Cases for Backend

```typescript
// Test valid simulation
POST /api/simulations/run
{
  "portfolioId": "valid-id",
  "numberOfSimulations": 100,
  "timeHorizonDays": 365,
  "confidenceLevel": 95
}
// Expect: 200 with valid SimulationResult

// Test invalid portfolio
POST /api/simulations/run
{
  "portfolioId": "invalid-id",
  ...
}
// Expect: 400 INVALID_PORTFOLIO

// Test empty portfolio
POST /api/simulations/run
{
  "portfolioId": "empty-portfolio-id",
  ...
}
// Expect: 400 EMPTY_PORTFOLIO

// Test missing parameters
POST /api/simulations/run
{
  "portfolioId": "valid-id"
}
// Expect: 400 INVALID_REQUEST

// Test unauthorized
GET /api/simulations/sim-123
(no auth header)
// Expect: 401 UNAUTHORIZED

// Test forbidden
GET /api/simulations/sim-123
(user different from owner)
// Expect: 403 FORBIDDEN
```

### Frontend Testing

The frontend already has:
- ✅ Loading state test (shows "Running…")
- ✅ Success state test (displays results)
- ✅ Error state test (shows message + retry)
- ✅ Controls validation (disable submit while running)

No additional testing needed once backend is integrated.

---

## Monitoring & Logging

Suggest adding:

1. **Request logging**: All simulation requests with parameters
2. **Performance logging**: Simulation duration, path count
3. **Error logging**: Failed simulations with error message
4. **Usage analytics**: Most common parameters, error rates

Example:

```python
import logging

logger = logging.getLogger(__name__)

@app.post('/simulations/run')
def run_simulation(request: SimulationRequest):
    logger.info(f"Simulation started: {request.portfolioId}, "
                f"sims={request.numberOfSimulations}, "
                f"horizon={request.timeHorizonDays}d")
    
    try:
        result = monte_carlo(request)
        logger.info(f"Simulation {result.id} completed in {elapsed}s")
        return result
    except Exception as e:
        logger.error(f"Simulation failed: {e}", exc_info=True)
        raise
```

---

## Deployment Notes

1. **Database migration**: Add `simulations` table before deploying
2. **Environment variables**: Set `REACT_APP_API_URL` in CI/CD
3. **Scaling**: Consider queue system if simulations take > 30 seconds
4. **Caching**: Implement result caching for common parameter combinations
5. **Cleanup**: Implement TTL-based cleanup of old simulations

---

## Support

For integration questions or issues:

1. Check types in `src/types/simulation.ts`
2. Review mock data generator in `src/api/simulationService.ts`
3. See component usage in `src/pages/monte-carlo/MonteCarlo.tsx`
4. Refer to error handling examples in DataBoundary and ErrorState components

Good luck with the backend implementation! 🚀
