export type ActionState<T = void> = {
  status: 'idle' | 'pending' | 'success' | 'error';
  error?: string;
  data?: T;
};

export function createPendingActionState<T = void>(): ActionState<T> {
  return { status: 'pending' };
}

export function createSuccessActionState<T>(data?: T): ActionState<T> {
  return { status: 'success', data };
}

export function createErrorActionState<T>(error: string): ActionState<T> {
  return { status: 'error', error };
}
