import api from './client';

export interface FollowStateResponse {
  following: boolean;
}

export async function getFollowState(userId: string): Promise<FollowStateResponse> {
  const { data } = await api.get<FollowStateResponse>(
    `/users/${encodeURIComponent(userId)}/follow`,
  );
  return data;
}

export async function followUser(userId: string): Promise<void> {
  await api.post(`/users/${encodeURIComponent(userId)}/follow`);
}

export async function unfollowUser(userId: string): Promise<void> {
  await api.delete(`/users/${encodeURIComponent(userId)}/follow`);
}


export interface FollowUserResponse {
  id: string;
  username: string;
  displayName: string | null;
  avatar: string | null;
  bio: string | null;
  gender: string | null;
  location: string | null;
  cover: string | null;
  status: string;
  createdAt: number;
  updatedAt: number;
  isFollowing: boolean;
}

export async function getFollowingUsers(
  userId: string,
  limit = 50,
): Promise<FollowUserResponse[]> {
  const { data } = await api.get<FollowUserResponse[]>(
    `/users/${encodeURIComponent(userId)}/following`,
    { params: { limit } },
  );
  return data;
}

export async function getFollowerUsers(
  userId: string,
  limit = 50,
): Promise<FollowUserResponse[]> {
  const { data } = await api.get<FollowUserResponse[]>(
    `/users/${encodeURIComponent(userId)}/followers`,
    { params: { limit } },
  );
  return data;
}
