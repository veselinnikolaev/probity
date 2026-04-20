import React from 'react';
import { AsyncState } from '../../types/async-state';

/**
 * Universal data boundary component.
 * Every data-driven component MUST go through this.
 *
 * Handles:
 * - loading → show loader
 * - error → show error state
 * - empty → show empty state
 * - success → show children with data
 *
 * This kills inconsistent empty states, missing loaders, and random error handling.
 */

export interface DataBoundaryProps<T> {
  state: AsyncState<T>;
  isEmpty: (data: T) => boolean;
  loading: React.ReactNode;
  empty: React.ReactNode;
  error: (msg: string) => React.ReactNode;
  partial?: (msg: string) => React.ReactNode;
  children: (data: T) => React.ReactNode;
}

export function DataBoundary<T>({
  state,
  isEmpty,
  loading,
  empty,
  error,
  partial,
  children,
}: DataBoundaryProps<T>) {
  // Step 1: Check loading
  if (state.isLoading) {
    return <>{loading}</>;
  }

  // Step 2: Check error
  if (state.error) {
    return <>{error(state.error)}</>;
  }

  // Step 3: Check if data is missing or empty
  if (!state.data || isEmpty(state.data)) {
    return <>{empty}</>;
  }

  // Step 4: Render children with data
  return (
    <>
      {partial && <>{partial('')}</> /* partial states can be shown alongside success */}
      {children(state.data)}
    </>
  );
}

export default DataBoundary;
