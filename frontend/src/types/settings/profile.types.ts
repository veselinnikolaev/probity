export interface ProfileResponse {
  username: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  memberSince: string | null;
}

export interface UpdateProfileRequest {
  firstName: string;
  lastName: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}
