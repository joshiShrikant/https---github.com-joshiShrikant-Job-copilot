export type UserRole = 'ROLE_USER' | 'ROLE_ADMIN' | string;

export interface UserSummary {
  readonly uuid: string;
  readonly email: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly role: UserRole;
  readonly accountStatus: string;
  readonly emailVerified: boolean;
}

export interface LoginRequest {
  readonly email: string;
  readonly password: string;
}

export interface RegisterRequest {
  readonly email: string;
  readonly password: string;
  readonly firstName: string;
  readonly lastName: string;
}

export interface AuthResponse {
  readonly accessToken: string;
  readonly tokenType: string;
  readonly expiresInSeconds: number;
  readonly user: UserSummary;
  readonly message?: string;
}

export interface TokenRefreshResponse {
  readonly accessToken: string;
  readonly tokenType: string;
  readonly expiresInSeconds: number;
}

export interface LogoutRequest {
  readonly refreshToken?: string;
  readonly revokeAllSessions?: boolean;
}

