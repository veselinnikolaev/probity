/**
 * Universal async state type for all data-driven components.
 * Enforces that every component knows about loading, error, and empty states.
 */
export type AsyncState<T> = {
  data?: T;
  isLoading: boolean;
  error?: string | null;
};

/**
 * Helper to create an initial loading state
 */
export function createLoadingState<T>(): AsyncState<T> {
  return { isLoading: true };
}

/**
 * Helper to create a success state
 */
export function createSuccessState<T>(data: T): AsyncState<T> {
  return { data, isLoading: false, error: null };
}

/**
 * Helper to create an error state
 */
export function createErrorState<T>(error: string): AsyncState<T> {
  return { isLoading: false, error };
}
