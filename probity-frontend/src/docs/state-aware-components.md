# State-Aware Components: The Probity Pattern

> **Core Rule**: No component is allowed to render raw data directly. Every data UI must go through the AsyncState → DataBoundary flow.

## Architecture Overview

The Probity frontend enforces a **state-aware by design** pattern. This means:

- **No inline loading checks** (`if (loading) return null`)
- **No inconsistent empty states** across screens
- **No missing error handlers**
- **Type-safe data flows** with `AsyncState<T>`
- **Reusable state components** (skeletons, empty states, error states)

## The Three Tiers

### 1. AsyncState Type (Core)

Located in `src/types/async-state.ts`:

```typescript
export type AsyncState<T> = {
  data?: T;
  isLoading: boolean;
  error?: string | null;
};
```

This replaces scattered `loading`, `error`, `data` state variables. One type to rule them all.

### 2. Data Hooks (API Layer)

Hooks that **always** return `AsyncState<T>`:

```typescript
// src/hooks/usePortfolios.ts
export function usePortfolios(): AsyncState<Portfolio[]> & { refetch: () => void }

// src/hooks/usePortfolioDetail.ts
export function usePortfolioDetail(
  portfolioId: string,
  dateRange: string
): AsyncState<PortfolioDetail> & { refetch: () => void }
```

**Key principle**: The hook is responsible for orchestrating the fetch. The component consuming the hook is **not**.

### 3. DataBoundary Component (Enforcer)

Located in `src/components/core/DataBoundary.tsx`:

```typescript
<DataBoundary
  state={data}
  isEmpty={(data) => data.length === 0}
  loading={<Skeleton />}
  empty={<EmptyState title="No data" />}
  error={(msg) => <ErrorState message={msg} onRetry={() => {}} />}
>
  {(data) => <ActualContent data={data} />}
</DataBoundary>
```

This enforces the state flow:
1. **loading** → show skeleton/loader
2. **error** → show error state
3. **empty** → show empty state
4. **success** → show children with data

## State Components

### SkeletonLoader
Animated placeholder while data loads.

```typescript
import { StatCardSkeleton, TableSkeleton } from '@/components/probity/SkeletonLoader';

<StatCardSkeleton />
<TableSkeleton rows={5} />
```

### EmptyState
Reusable empty state for all screens.

```typescript
import { EmptyState } from '@/components/probity/EmptyState';

<EmptyState
  icon="💼"
  title="No Portfolios"
  description="Create your first portfolio to get started"
  action={<button>Create</button>}
  size="md"
/>
```

### ErrorState
Reusable error display with retry.

```typescript
import { ErrorState } from '@/components/probity/ErrorState';

<ErrorState
  message="Failed to load portfolios"
  onRetry={portfoliosState.refetch}
  size="sm"
/>
```

## Real Examples

### Before (❌ Bad)

```typescript
const DashboardPage = () => {
  const { portfolios, loading, error } = usePortfolio('id', '30d');

  // ❌ Multiple conditional checks scattered everywhere
  if (loading) return <div>Loading...</div>;
  if (error) return <div>{error}</div>;
  if (!portfolios.length) return <div>No portfolios</div>;

  return (
    <div>
      {/* 🔴 What if this .map() is undefined? No protection */}
      {portfolios.map(p => <PortfolioCard {...p} />)}
    </div>
  );
};
```

### After (✅ Good)

```typescript
const DashboardPage = () => {
  const portfoliosState = usePortfolios();

  return (
    <DataBoundary
      state={portfoliosState}
      isEmpty={(data) => data.length === 0}
      loading={<PortfolioCardSkeleton />}
      empty={
        <EmptyState
          title="No Portfolios"
          description="Create your first portfolio"
        />
      }
      error={(msg) => (
        <ErrorState message={msg} onRetry={portfoliosState.refetch} />
      )}
    >
      {(portfolios) => (
        <div>
          {/* ✅ TypeScript knows portfolios is Portfolio[], never undefined */}
          {portfolios.map(p => <PortfolioCard {...p} />)}
        </div>
      )}
    </DataBoundary>
  );
};
```

## Rules (Non-Negotiable)

### 1. Never Map Undefined Data
❌ Bad:
```typescript
{data.positions?.map(p => ...)}
```

✅ Good:
```typescript
<DataBoundary state={positionsState}>
  {(data) => {
    /* data.positions is guaranteed to exist */
    return data.positions.map(p => ...);
  }}
</DataBoundary>
```

### 2. Always Format Values
❌ Bad:
```typescript
<span>${value}</span>
```

✅ Good:
```typescript
<span>{formatCurrency(value)}</span>
```

### 3. No Inline Loading Checks
❌ Bad:
```typescript
if (!data) return null;
if (loading) return <Spinner />;
return <Component data={data} />;
```

✅ Good:
```typescript
<DataBoundary
  state={state}
  isEmpty={(d) => !d}
  loading={<Skeleton />}
  empty={<EmptyState />}
>
  {(data) => <Component data={data} />}
</DataBoundary>
```

### 4. No Direct API Response Rendering
❌ Bad:
```typescript
const data = await fetchPositions();
return <Table rows={data} />; // What if it's undefined?
```

✅ Good:
```typescript
const state = usePositions(); // Always returns AsyncState
return <DataBoundary state={state}>...</DataBoundary>;
```

## Creating New Hooks

When adding a new data-driven feature:

```typescript
// src/hooks/useMyData.ts
import { AsyncState } from '../types/async-state';

export function useMyData(id: string): AsyncState<MyType> & { refetch: () => void } {
  const [state, setState] = useState<AsyncState<MyType>>({
    isLoading: true,
    error: null,
  });

  const fetch = useCallback(async () => {
    setState({ isLoading: true, error: null });

    try {
      const data = await api.fetchMyData(id);
      setState({ data, isLoading: false, error: null });
    } catch (e: any) {
      const msg = e?.response?.data?.message ?? 'Failed to load data';
      setState({ isLoading: false, error: msg });
    }
  }, [id]);

  useEffect(() => {
    fetch();
  }, [fetch]);

  return { ...state, refetch: fetch };
}
```

Then use it:

```typescript
const MyComponent = () => {
  const state = useMyData('123');

  return (
    <DataBoundary
      state={state}
      isEmpty={(data) => !data}
      loading={<Skeleton />}
      empty={<EmptyState title="No data" />}
      error={(msg) => <ErrorState message={msg} onRetry={state.refetch} />}
    >
      {(data) => <MyContent data={data} />}
    </DataBoundary>
  );
};
```

## File Structure

```
src/
├── types/
│   ├── async-state.ts          ← AsyncState<T> type
│   ├── portfolio.ts            ← Domain models
│   └── auth.types.ts
├── hooks/
│   ├── usePortfolios.ts        ← Returns AsyncState<Portfolio[]>
│   ├── usePortfolioDetail.ts   ← Returns AsyncState<PortfolioDetail>
│   ├── usePortfolio.ts         ← Legacy, deprecated
│   └── useAuth.ts
├── components/
│   ├── core/
│   │   └── DataBoundary.tsx    ← The enforcer
│   └── probity/
│       ├── SkeletonLoader.tsx  ← Animated placeholders
│       ├── EmptyState.tsx      ← Reusable empty UI
│       ├── ErrorState.tsx      ← Reusable error UI
│       ├── StatCard.tsx        ← Presentation (no state logic)
│       ├── DataTable.tsx       ← Presentation (no state logic)
│       └── ...
└── pages/
    ├── dashboard/
    │   └── DashboardPage.tsx   ← Uses hooks + DataBoundary
    ├── portfolios/
    │   └── PortfoliosPage.tsx  ← Uses hooks + DataBoundary
    └── portfolio-detail/
        └── PortfolioDetailPage.tsx
```

## Common Patterns

### Multi-Dependent States

If you have multiple independent data sources:

```typescript
const detailState = usePortfolioDetail(id, range);
const alertsState = usePortfolioAlerts(id);

return (
  <DataBoundary state={detailState} ...>
    {(detail) => (
      <DataBoundary state={alertsState} ...>
        {(alerts) => <Combined detail={detail} alerts={alerts} />}
      </DataBoundary>
    )}
  </DataBoundary>
);
```

### Partial Data Display

Some metrics might be unavailable:

```typescript
<DataBoundary state={detailState}>
  {(detail) => (
    <>
      {detail.missingRisk && (
        <WarningBanner>Risk metrics unavailable</WarningBanner>
      )}
      <Content data={detail} />
    </>
  )}
</DataBoundary>
```

### Refetch on User Action

```typescript
const state = useMyData(id);

return (
  <>
    <DataBoundary state={state} ...>
      {(data) => <Content data={data} />}
    </DataBoundary>
    <button onClick={() => state.refetch()}>Refresh</button>
  </>
);
```

## Migration Checklist

When refactoring an existing page:

- [ ] Identify all data fetching (useEffect + useState)
- [ ] Create/use appropriate `useXxx` hook returning AsyncState
- [ ] Wrap each data section in `<DataBoundary>`
- [ ] Provide `loading` → skeleton
- [ ] Provide `empty` → EmptyState
- [ ] Provide `error` → ErrorState
- [ ] Move all data logic into children render function
- [ ] Remove manual loading/error/empty checks
- [ ] Test all states (loading, empty, error, success)

## Benefits

✅ **Type Safety**: TypeScript knows your data is never undefined in children
✅ **Consistency**: Same loading/empty/error patterns everywhere
✅ **Maintainability**: No scattered loading checks
✅ **User Experience**: Always shows something (skeleton/empty/error/data)
✅ **Reusability**: State components work everywhere
✅ **Testability**: Pure render logic, no conditional side effects

## Further Reading

- `src/types/async-state.ts` - Type definition
- `src/components/core/DataBoundary.tsx` - Component implementation
- `src/hooks/usePortfolios.ts` - Example hook implementation
- `src/pages/dashboard/DashboardPage.tsx` - Full real-world usage
