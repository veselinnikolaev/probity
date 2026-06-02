export interface User {
  username: string;
  role: string;
}

export interface LoginPayload {
  identifier: string;  // your backend uses username, not email
  password: string;
}

export interface RegisterPayload {
  username: string;
  email: string;
  password: string;
}
