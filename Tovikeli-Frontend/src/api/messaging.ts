import api from './client';

export interface DirectUser {
  id: string;
  username: string;
  displayName: string | null;
  avatar: string | null;
}

export interface DirectMessage {
  id: string;
  conversationId: string;
  senderUserId: string;
  body: string;
  createdAt: number;
  readAt: number | null;
}

export interface DirectConversation {
  id: string;
  user: DirectUser;
  lastMessage: DirectMessage | null;
  unreadCount: number;
  updatedAt: number;
}

export async function listConversations(limit = 50): Promise<DirectConversation[]> {
  const { data } = await api.get<DirectConversation[]>('/messages/conversations', { params: { limit } });
  return data;
}

export async function createOrGetConversation(userId: string): Promise<string> {
  const { data } = await api.post<{ id: string }>('/messages/conversations', { userId });
  return data.id;
}

export async function listMessages(conversationId: string, limit = 50): Promise<DirectMessage[]> {
  const { data } = await api.get<DirectMessage[]>(
    `/messages/conversations/${encodeURIComponent(conversationId)}/messages`,
    { params: { limit } },
  );
  return data;
}

export async function sendMessage(conversationId: string, body: string): Promise<DirectMessage> {
  const { data } = await api.post<DirectMessage>(
    `/messages/conversations/${encodeURIComponent(conversationId)}/messages`,
    { body },
  );
  return data;
}

export async function markConversationRead(conversationId: string): Promise<void> {
  await api.post(
    `/messages/conversations/${encodeURIComponent(conversationId)}/read`,
  );
}
