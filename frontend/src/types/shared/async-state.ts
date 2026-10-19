export interface AsyncState<T> {
  isLoading: boolean;
  error: string | null;
  data?: T;
}

export function createLoadingState<T>(): AsyncState<T> {
  return { isLoading: true, error: null };
}

export function createSuccessState<T>(data: T): AsyncState<T> {
  return { isLoading: false, error: null, data };
}

export function createErrorState<T>(error: string): AsyncState<T> {
  return { isLoading: false, error };
}
