export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload {
  fullName: string;
  email: string;
  password: string;
}

export interface User {
  id: string;
  email: string;
  fullName: string;
}

export interface LoginResponse extends AuthTokens {
  user: User;
}