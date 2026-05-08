# Probity Component Usage Examples

This document shows how to use the new Monte Carlo components and shared design system components across the platform.

---

## Monte Carlo Components

### FanChart

Visualizes 10,000+ simulated portfolio paths with emphasized percentiles.

```jsx
import { FanChart } from '@/components/probity/FanChart';
import { SimulationResult } from '@/types/simulation';

function MyComponent({ result }: { result: SimulationResult }) {
  return (
    <FanChart
      allPaths={result.allPaths}
      percentileSeries={result.percentileSeries}
      currentValue={result.currentPortfolioValue}
      timeHorizonDays={result.parameters.timeHorizonDays}
      height={400}  // optional, default 400
    />
  );
}
```

**Props**:
- `allPaths`: SimulatedPath[] - All simulated paths
- `percentileSeries`: PercentileSeries[] - Percentile series (5th–95th)
- `currentValue`: number - Starting portfolio value
- `timeHorizonDays`: number - Number of days simulated
- `height?`: number - Chart height (default: 400)

**Features**:
- Canvas-based rendering for performance
- Three emphasized paths (10th, 50th, 90th percentiles)
- Hover tooltip showing day and values
- Grid background with axes
- Baseline reference line at current value

---

### DistributionHistogram

Shows distribution of final portfolio values as a frequency histogram.

```jsx
import { DistributionHistogram } from '@/components/probity/DistributionHistogram';
import { SimulationResult } from '@/types/simulation';

function MyComponent({ result }: { result: SimulationResult }) {
  return (
    <DistributionHistogram
      distribution={result.distribution}
      currentValue={result.currentPortfolioValue}
      medianValue={result.statistics.medianFinalValue}
      height={300}  // optional, default 300
    />
  );
}
```

**Props**:
- `distribution`: DistributionBucket[] - Histogram buckets (30)
- `currentValue`: number - Starting value (baseline marker)
- `medianValue`: number - Median outcome (dashed marker)
- `height?`: number - Chart height (default: 300)

**Features**:
- Color-coded bars (red for losses, blue for gains)
- Loss region shading (10% opacity)
- Current value marker (blue solid line)
- Median marker (green dashed line)
- Hover tooltip with count and percentage

---

### PercentileTable

Shows outcome values at each percentile point.

```jsx
import { PercentileTable } from '@/components/probity/PercentileTable';
import { SimulationResult } from '@/types/simulation';

function MyComponent({ result }: { result: SimulationResult }) {
  return (
    <PercentileTable
      percentileSeries={result.percentileSeries}
      currentValue={result.currentPortfolioValue}
    />
  );
}
```

**Props**:
- `percentileSeries`: PercentileSeries[] - Percentile outcomes (5th–95th)
- `currentValue`: number - Starting value (for gain/loss calculation)

**Features**:
- Semantic HTML `<table>`
- Monospace font for numeric alignment
- Color-coded gain/loss values
- Alternating row backgrounds
- Row hover highlighting
- Fully responsive

---

## Shared Design System Components

### StatCard

Unified statistics card component used across all pages.

```jsx
import { StatCard } from '@/components/probity/StatCard';

// Basic usage
<StatCard
  label="Portfolio Volatility"
  value="18.5%"
/>

// With delta and sentiment
<StatCard
  label="Portfolio Volatility"
  value="18.5%"
  delta="+2.1%"
  deltaType="up"  // or 'down' or 'neutral'
  invertSentiment={true}  // Higher is worse for risk metrics
/>

// With sparkline and risk badge
<StatCard
  label="Max Drawdown"
  value="-24.3%"
  sparklineData={[20, 22, 21, 24, 23, 21, 19]}
  riskLevel="HIGH"
  suffix="YTD"
  prefix="$"
/>

// Loading skeleton
<StatCard
  label="Metric"
  value="—"
  loading={true}
/>
```

**Props**:
- `label`: string - Metric name (uppercase, secondary text)
- `value`: string - Main value (monospace, large)
- `subValue?`: string - Secondary value
- `delta?`: string - Change indicator (e.g., "+5%")
- `deltaType?`: "up" | "down" | "neutral"
- `invertSentiment?`: boolean - Inverts delta color (for risk metrics)
- `sparklineData?`: (number | null)[] - Trend sparkline
- `riskLevel?`: "HIGH" | "MODERATE" | "LOW" - Risk badge
- `loading?`: boolean - Show loading skeleton
- `suffix?`: string - Right-side label (e.g., "%")
- `prefix?`: string - Left-side label (e.g., "$")
- `className?`: string - Additional CSS class

**Styling**:
- Background: `var(--pb-surface)`
- Border: 1px solid `var(--pb-border)` with hover state
- 8px border-radius
- 20px padding
- Minimum width: 200px

---

### DataBoundary Wrapper

Orchestrates loading/error/empty states for async data.

```jsx
import { DataBoundary } from '@/components/core/DataBoundary';
import { usePortfolios } from '@/hooks/usePortfolios';

function MyComponent() {
  const portfoliosState = usePortfolios();

  return (
    <DataBoundary
      state={portfoliosState}
      isEmpty={(data) => data.length === 0}
      loading={
        <div>Loading portfolios…</div>
      }
      empty={
        <EmptyState
          icon="💼"
          title="No Portfolios"
          description="Create one to get started"
        />
      }
      error={(msg) => (
        <ErrorState
          message={msg}
          onRetry={portfoliosState.refetch}
        />
      )}
    >
      {(data) => (
        <div>
          {data.map(portfolio => (
            <div key={portfolio.id}>{portfolio.name}</div>
          ))}
        </div>
      )}
    </DataBoundary>
  );
}
```

**Props**:
- `state`: AsyncState<T> - Async state object
- `isEmpty`: (data: T) => boolean - Predicate to check if data is empty
- `loading`: ReactNode - Loading UI
- `empty`: ReactNode - Empty state UI
- `error`: (message: string) => ReactNode - Error UI
- `children`: (data: T) => ReactNode - Success UI

**Evaluation Order**: loading → error → empty → children

---

### EmptyState

Generic empty state component for no-data scenarios.

```jsx
import { EmptyState } from '@/components/probity/EmptyState';

// Basic
<EmptyState
  title="No Data"
  description="Create a portfolio to get started"
/>

// With icon and action
<EmptyState
  icon="📊"
  title="No Results Found"
  description="Your search returned no portfolios"
  action={
    <button onClick={() => navigate('/portfolios/new')}>
      Create Portfolio
    </button>
  }
/>

// Compact size
<EmptyState
  icon="✓"
  title="All Clear"
  size="sm"  // or 'md' or 'lg'
/>
```

**Props**:
- `icon?`: string - Emoji or symbol
- `title`: string - Main message
- `description`: string - Secondary message
- `action?`: ReactNode - CTA button
- `size?`: "sm" | "md" | "lg" - Component size

---

### ErrorState

Generic error state component with retry capability.

```jsx
import { ErrorState } from '@/components/probity/ErrorState';

<ErrorState
  message="Failed to load portfolio data"
  onRetry={() => portfoliosState.refetch()}
/>

// Compact size
<ErrorState
  message="Network error"
  size="sm"
/>
```

**Props**:
- `message`: string - Error message
- `onRetry?`: () => void - Retry callback
- `size?`: "sm" | "md" | "lg" - Component size

---

### Skeleton Loaders

Pre-built skeleton components matching common patterns.

```jsx
import {
  Skeleton,
  StatCardSkeleton,
  TableSkeleton,
  TableRowSkeleton,
} from '@/components/probity/SkeletonLoader';

// Generic skeleton (customize width/height)
<Skeleton width="100%" height="40px" />

// StatCard skeleton
<div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px' }}>
  {[...Array(4)].map((_, i) => (
    <StatCardSkeleton key={i} />
  ))}
</div>

// Table skeleton
<TableSkeleton rows={5} />

// Custom: Load while fetching
{loading ? (
  <TableSkeleton rows={10} />
) : (
  <MyTable data={data} />
)}
```

**Animation**: `pb-shimmer` 1.5s ease-in-out infinite (staggered +0.1s per item)

---

## Layout Components

### Sidebar Navigation

Fixed sidebar with consistent navigation pattern.

```jsx
import { SidebarNavItem } from '@/components/probity/SidebarNavItem';

<nav className={styles['sidebar-nav']}>
  {navItems.map(item => (
    <SidebarNavItem
      key={item.id}
      label={item.label}
      icon={item.icon}
      active={activeNav === item.id}
      onClick={() => {
        setActiveNav(item.id);
        navigate(`/${item.id}`);
      }}
    />
  ))}
</nav>
```

**Styling**:
- 240px width (desktop)
- 200px (tablet)
- Fixed position with z-index: 100
- Gap: 32px (top), 4px (between items)
- Background: `var(--pb-surface)`

---

### Dropdown Select

Controlled dropdown component.

```jsx
import { DropdownSelect } from '@/components/probity/DropdownSelect';

const portfolioOptions = [
  { value: 'p1', label: 'Tech Portfolio', description: '$50K' },
  { value: 'p2', label: 'Growth Portfolio', description: '$30K' },
];

<DropdownSelect
  options={portfolioOptions}
  value={selectedPortfolio}
  onChange={(value) => setSelectedPortfolio(value)}
  placeholder="Select portfolio"
  label="Portfolio"
  width="240px"
  disabled={false}
/>
```

**Props**:
- `options`: Array<{ value, label, description? }>
- `value`: string - Current selected value
- `onChange`: (value: string) => void
- `placeholder?`: string
- `label?`: string
- `width?`: string
- `disabled?`: boolean

---

## Form Components

### InputField

Standard form input with validation support.

```jsx
import { InputField } from '@/components/probity/InputField';

<InputField
  label="Portfolio Name"
  placeholder="e.g., Aggressive Growth"
  value={portfolioName}
  onChange={(e) => setPortfolioName(e.target.value)}
  helpText="Give your portfolio a memorable name"
  errorMessage={errors.name}
  inputState={errors.name ? 'error' : 'default'}
  mono={false}
/>

// Monospace variant
<InputField
  label="Simulations"
  type="number"
  value={10000}
  mono={true}
  prefix="Count:"
  suffix="simulations"
/>
```

**Props**:
- `label`: string
- `value`: string | number
- `onChange`: (e) => void
- `placeholder?`: string
- `helperText?`: string
- `errorMessage?`: string
- `inputState?`: "default" | "error" | "success"
- `prefix?`: string
- `suffix?`: string
- `mono?`: boolean - Use monospace font
- `disabled?`: boolean

---

## Utility Hooks

### useSimulation

Hook for running Monte Carlo simulations.

```jsx
import { useSimulation } from '@/pages/monte-carlo/MonteCarlo';
import { SimulationRequest } from '@/types/simulation';

function MyComponent() {
  const simulationState = useSimulation(portfolioId);

  const handleRun = async () => {
    const request: SimulationRequest = {
      portfolioId,
      numberOfSimulations: 10000,
      timeHorizonDays: 365,
      confidenceLevel: 95,
    };
    
    const result = await simulationState.run(request, currentValue);
    
    if (result) {
      console.log('Simulation complete:', result);
    }
  };

  return (
    <div>
      <button onClick={handleRun} disabled={simulationState.state.status === 'loading'}>
        Run Simulation
      </button>
      
      {simulationState.state.status === 'loading' && <p>Running…</p>}
      {simulationState.state.status === 'error' && (
        <p>{simulationState.state.error}</p>
      )}
      {simulationState.state.status === 'success' && (
        <p>Result ID: {simulationState.state.data.id}</p>
      )}
    </div>
  );
}
```

**State**:
```typescript
{
  status: 'idle' | 'loading' | 'success' | 'error',
  data: SimulationResult | null,
  error?: string
}
```

**Methods**:
- `run(request, currentValue)`: Async, runs simulation
- `refetch(currentValue)`: Runs last simulation again

---

## CSS Module Classes (Reusable)

All page CSS modules follow consistent naming:

```css
/* Containers */
.layout              /* main layout wrapper */
.sidebar             /* sidebar container */
.main-wrapper        /* main content wrapper */
.topbar              /* top navigation bar */
.content-area        /* scrollable content area */
.content-container   /* max-width container */

/* Sections */
.section-title       /* h2 style */
.controls-bar        /* control section */
.chart-container     /* card wrapper for charts */
.chart-wrapper       /* chart aspect ratio container */

/* Grids */
.outcomes-grid       /* responsive card grid */
.bottom-section      /* 2-column layout */
.bottom-column       /* column in 2-column layout */

/* States */
.loading-state       /* centered loading message */
.empty-state-container  /* centered empty state */
```

Copy these patterns to new pages for consistency.

---

## TypeScript Types

All types are defined in dedicated files:

```typescript
// src/types/simulation.ts
export interface SimulationRequest { ... }
export interface SimulationResult { ... }
export interface PortfolioPath { ... }
export interface PercentileSeries { ... }
export interface DistributionBucket { ... }

// src/types/async-state.ts
export interface AsyncState<T> { ... }

// src/types/auth.types.ts
export interface AuthResponse { ... }
export interface User { ... }

// src/types/portfolio.ts
export interface Portfolio { ... }
export interface Position { ... }
```

Import and use types to ensure type safety:

```typescript
import { SimulationResult } from '@/types/simulation';
import { Portfolio } from '@/types/portfolio';

function MyComponent(result: SimulationResult) { ... }
```

---

## Best Practices

1. **Always wrap async data in DataBoundary**
   ```jsx
   <DataBoundary state={asyncState} isEmpty={...} loading={...} error={...}>
     {(data) => <Content data={data} />}
   </DataBoundary>
   ```

2. **Use semantic color tokens, not hardcoded colors**
   ```css
   color: var(--pb-text-primary);  /* ✓ */
   background: var(--pb-success);  /* ✓ */
   border-color: #3B82F6;          /* ✗ */
   ```

3. **Use monospace font for financial values**
   ```jsx
   <span style={{ fontFamily: 'var(--font-mono)' }}>
     ${value.toLocaleString()}
   </span>
   ```

4. **Import CSS modules, not inline styles**
   ```jsx
   import styles from './page.module.css';
   <div className={styles['section-title']}>…</div>  /* ✓ */
   <div style={{ fontSize: '20px' }}>…</div>         /* ✗ */
   ```

5. **Use consistent spacing scale**
   ```css
   margin: 32px;    /* ✓ */
   padding: 20px;   /* ✓ */
   gap: 16px;       /* ✓ */
   margin: 17px;    /* ✗ Not in scale */
   ```

6. **Always provide loading + error + empty states**
   ```jsx
   <DataBoundary
     state={state}
     isEmpty={(d) => d.length === 0}
     loading={<Skeleton />}           /* Required */
     empty={<EmptyState />}           /* Required */
     error={(msg) => <Error />}       /* Required */
   >
     {(data) => <Content data={data} />}
   </DataBoundary>
   ```

---

## Support

For additional examples or questions about component usage:
1. Check existing pages (e.g., RiskAnalysisPage.tsx, PortfolioDetailPage.tsx)
2. Review type definitions in src/types/
3. Consult DESIGN_SYSTEM.md for patterns
4. Check component prop documentation in component files
