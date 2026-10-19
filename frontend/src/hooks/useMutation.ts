import { useState, useCallback } from 'react';
import { ActionState, createPendingActionState, createSuccessActionState, createErrorActionState } from '@/types/shared';

export function useMutation<T = void, P = void>(
  mutationFn: (params: P) => Promise<T>
) {
  const [state, setState] = useState<ActionState<T>>({ status: 'idle' });

  const execute = useCallback(async (params: P) => {
    setState(createPendingActionState<T>());
    try {
      const result = await mutationFn(params);
      setState(createSuccessActionState(result));
      return result;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'An unexpected error occurred';
      setState(createErrorActionState<T>(errorMessage));
      throw error;
    }
  }, [mutationFn]);

  const reset = useCallback(() => {
    setState({ status: 'idle' });
  }, []);

  return {
    state,
    execute,
    reset,
    status: state.status,
    error: state.error,
    data: state.data,
    isPending: state.status === 'pending',
    isSuccess: state.status === 'success',
    isError: state.status === 'error',
    isIdle: state.status === 'idle',
  };
}
