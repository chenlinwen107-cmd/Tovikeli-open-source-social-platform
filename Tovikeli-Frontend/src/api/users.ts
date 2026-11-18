import api from './client';
import type { CreateUserRequest, UserResponse, InteractionResponse } from './types';

export async function createUser(payload: CreateUserRequest): Promise<UserResponse> {
  const { data } = await api.post<UserResponse>('/users', payload);
  return data;
}

export async function getUserByUsername(username: string): Promise<UserResponse> {
  const { data } = await api.get<UserResponse>(`/users/by-username/${encodeURIComponent(username)}`);
  return data;
}

export async function getUser(userId: string): Promise<UserResponse> {
  const { data } = await api.get<UserResponse>(`/users/${encodeURIComponent(userId)}`);
  return data;
}

export async function getMyInteractionHistory(
  userId: string,
  type: 'LIKE' | 'FAVORITE' | 'REPOST' | 'BOOKMARK' = 'LIKE',
  limit = 20,
  cursor?: string,
) {
  const response = await api.get<InteractionResponse[]>(`/users/${encodeURIComponent(userId)}/interactions`, {
    params: { type, limit, cursor },
  });
  const link = response.headers.link as string | undefined;
  const match = link?.match(/cursor=([^&>]+)/);
  return { items: response.data, nextCursor: match ? decodeURIComponent(match[1]) : null };
}
