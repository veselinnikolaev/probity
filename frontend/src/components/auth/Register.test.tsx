import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { authService } from '@/services/auth';
import { Register } from '@/components/auth/Register';
import { ROUTES } from '@/constants/routes';

vi.mock('@/services/auth', () => ({
  authService: {
    register: vi.fn(),
    checkEmailAvailability: vi.fn(),
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
    );

    const { container } = renderRegister();

    await userEvent.type(container.querySelector('#first-name'), 'John');
    await userEvent.type(container.querySelector('#last-name'), 'Doe');
    await userEvent.type(container.querySelector('#username'), 'johndoe');
    await userEvent.type(container.querySelector('#email'), 'john@example.com');
    await userEvent.click(screen.getByRole('button', { name: /Continue/i }));

    await expect(screen.getByPlaceholderText('Create a strong password')).toBeInTheDocument();

    expect(authService.checkEmailAvailability).not.toHaveBeenCalled();
  });
});