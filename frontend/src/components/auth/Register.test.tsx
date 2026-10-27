import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
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

async function fillStep1AndContinue() {
  const user = userEvent.setup();
  const { container } = renderRegister();
  await user.type(container.querySelector('#first-name') as Element, 'John');
  await user.type(container.querySelector('#last-name') as Element, 'Doe');
  await user.type(container.querySelector('#username') as Element, 'johndoe');
  await user.type(container.querySelector('#email') as Element, 'john@example.com');
  const continueButtons = screen.getAllByRole('button', { name: /Continue/i });
  await user.click(continueButtons[0]);
  return { user, container };
}

describe('Register form (M4c - no live check-email)', () => {
  it('shows "Email already registered" on duplicate email 409', async () => {
    authService.register.mockRejectedValueOnce({
      response: { status: 409, data: { message: 'Email already registered' } },
    });

    const { user } = await fillStep1AndContinue();
    await screen.findByPlaceholderText('Create a strong password');
    await user.type(screen.getByPlaceholderText('Create a strong password'), 'NotARealPassword123!');
    await user.type(screen.getByPlaceholderText('Confirm your password'), 'NotARealPassword123!');
    await user.click(screen.getByRole('button', { name: /Create Account/i }));

    expect(await screen.findByText('Email already registered')).toBeTruthy();
  });

  it('shows "Username already taken" on duplicate username 409', async () => {
    authService.register.mockRejectedValueOnce({
      response: { status: 409, data: { message: 'Username already taken' } },
    });

    const { user } = await fillStep1AndContinue();
    await screen.findByPlaceholderText('Create a strong password');
    await user.type(screen.getByPlaceholderText('Create a strong password'), 'NotARealPassword123!');
    await user.type(screen.getByPlaceholderText('Confirm your password'), 'NotARealPassword123!');
    await user.click(screen.getByRole('button', { name: /Create Account/i }));

    expect(await screen.findByText('Username already taken')).toBeTruthy();
  });

  it('shows general error on other registration error', async () => {
    authService.register.mockRejectedValueOnce({
      response: { status: 400, data: { message: 'Bad request' } },
    });

    const { user } = await fillStep1AndContinue();
    await screen.findByPlaceholderText('Create a strong password');
    await user.type(screen.getByPlaceholderText('Create a strong password'), 'NotARealPassword123!');
    await user.type(screen.getByPlaceholderText('Confirm your password'), 'NotARealPassword123!');
    await user.click(screen.getByRole('button', { name: /Create Account/i }));

    expect(await screen.findByText('Registration failed. Please try again.')).toBeTruthy();
  });
});