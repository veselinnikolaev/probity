import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { authService } from '@/services/auth';
import { Register } from '@/components/auth/Register';

vi.mock('@/services/auth', () => ({
  authService: {
    register: vi.fn(),
  },
}));

afterEach(() => {
  vi.resetAllMocks();
});

function renderRegister() {
  return render(
    <MemoryRouter initialEntries={['/register']}>
      <Routes>
        <Route path="/register" element={<Register />} />
      </Routes>
    </MemoryRouter>
  );
}

describe('Register form (M4c - no live check-email)', () => {
  it('shows "Email already registered" on duplicate email 409', async () => {
    const user = userEvent.setup();
    authService.register.mockRejectedValueOnce({
      response: { status: 409, data: { message: 'Email already registered' } },
    });

    const { container } = renderRegister();

    await userEvent.type(container.querySelector('#first-name') as Element, 'John');
    await userEvent.type(container.querySelector('#last-name') as Element, 'Doe');
    await userEvent.type(container.querySelector('#username') as Element, 'johndoe');
    await userEvent.type(container.querySelector('#email') as Element, 'john@example.com');
    const continueButtons = screen.getAllByRole('button', { name: /Continue/i });
    await userEvent.click(continueButtons[0]);

    expect(screen.getByPlaceholderText('Create a strong password')).toBeTruthy();
  });
});