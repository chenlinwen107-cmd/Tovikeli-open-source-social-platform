import api from './client';
import type { LoginRequest, UserResponse } from './types';

export async function login(payload: LoginRequest): Promise<UserResponse> {
  const { data } = await api.post<UserResponse>('/auth/login', payload);
  return data;
}

export async function logout(): Promise<void> {
  await api.post('/auth/logout');
}
