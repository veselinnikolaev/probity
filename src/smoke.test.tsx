import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';

/**
 * Minimal smoke test.
 *
 * Purpose: prove the React toolchain is actually installed and wired up —
 * `react`, `react-dom`, JSX/TSX transform and the jsdom environment all work
 * together. It deliberately does not render the real `App`, whose providers
 * issue network calls on mount.
 */
function Root() {
  return <h1>Probity</h1>;
}

describe('smoke', () => {
  it('renders a React component into the DOM', () => {
    render(<Root />);

    expect(screen.getByRole('heading', { name: 'Probity' })).toBeTruthy();
  });
});
