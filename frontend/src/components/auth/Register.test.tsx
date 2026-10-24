import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { authService } from '@/services/auth';
import { Register } from '@/components/auth/Register';
import { ROUTES } from '@/constants/routes';

// Mock the auth service
vi.mock('@/services/auth', () => ({
  authService: {
    register: vi.fn(),
    checkEmailAvailability: vi.fn(), // should not be called after removal
  },
}));

afterEach(() => {
  vi.resetAllMocks();
});

function renderRegister() {
  return render(
    <MemoryRouter initialEntries={[ROUTES.REGISTER]}>
      <Routes>
        <Route path={ROUTES.REGISTER} element={<Register />} />
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

    renderRegister();

    // Fill step 1
    await user.type(screen.getByLabelText('First Name'), 'John');
    await user.type(screen.getByLabelText('Last Name'), 'Doe');
    await user.type(screen.getByLabelText('Username'), 'johndoe');
    await user.type(screen.getByLabelText('Email'), 'john@example.com');
    await user.click(screen.getByRole('button', { name: /Continue/i }));

    // Step 2 - fill password
    await waitFor(() => screen.getByLabelText('Password'));
    await user.type(screen.getByLabelText('Password'), 'StrongPass123!');
    await user.type(screen.getByLabelText('Confirm Password'), 'StrongPass123!');
    await user.click(screen.getByRole('button', { name: /Create Account/i }));

    // Should show field-level error for email
    await waitFor(() => {
      expect(screen.getByText('Email already registered')).toBeInTheDocument();
    });

    // Should not have called checkEmailAvailability
    expect(authService.checkEmailAvailability).not.toHaveBeenCalled();
  });

  it('shows "Username already taken" on duplicate username 409', async () => {
    const user = userEvent.setup();
    authService.register.mockRejectedValueOnce({
      response: { status: 409, data: { message: 'Username already taken' } },
    });

    renderRegister();

    await user.type(screen.getByLabelText('First Name'), 'John');
    await user.type(screen.getByLabelText('Last Name'), 'Doe');
    await user.type(screen.getByLabelText('Username'), 'existinguser');
    await user.type(screen.getByLabelText('Email'), 'new@example.com');
    await user.click(screen.getByRole('button', { name: /Continue/i }));

    await waitFor(() => screen.getByLabelText('Password'));
    await user.type(screen.getByLabelText('Password'), 'StrongPass123!');
    await user.type(screen.getByLabelText('Confirm Password'), 'StrongPass123!');
    await user.click(screen.getByRole('button', { name: /Create Account/i }));

    await waitFor(() => {
      expect(screen.getByText('Username already taken')).toBeInTheDocument();
    });

    expect(authService.checkEmailAvailability).not.toHaveBeenCalled();
  });

  it('successful registration navigates to confirmation step', async () => {
    const user = userEvent.setup();
    authService.register.mockResolvedValueOnce(undefined);

    renderRegister();

    await user.type(screen.getByLabelText('First Name'), 'John');
    await user.type(screen.getByLabelText('Last Name'), 'Doe');
    await user.type(screen.getByLabelText('Username'), 'newuser');
    await user.type(screen.getByLabelText('Email'), 'new@example.com');
    await user.click(screen.getByRole('button', { name: /Continue/i }));

    await waitFor(() => screen.getByLabelText('Password'));
    await user.type(screen.getByLabelText('Password'), 'StrongPass123!');
    await user.type(screen.getByLabelText('Confirm Password'), 'StrongPass123!');
    await user.click(screen.getByRole('button', { name: /Create Account/i }));

    // Should reach confirmation step
    await waitFor(() => {
      expect(screen.getByText('Check your inbox')).toBeInTheDocument();
    });

    expect(authService.checkEmailAvailability).not.toHaveBeenCalled();
  });

  it('does not make any check-email requests during form interaction', async () => {
    const user = userEvent.setup();
    authService.register.mockResolvedValueOnce(undefined);

    renderRegister();

    // Type email - this used to trigger the live check
    await user.type(screen.getByLabelText('Email'), 'test@example.com');

    // Wait a bit for any debounced effect
    await act(async () => {
      await new Promise(resolve => setTimeout(resolve, 600));
    });

    // No check-email call should have been made
    expect(authService.checkEmailAvailability).not.toHaveBeenCalled();
  });
});