import api from './client';
import { toPage } from './pagination';
import type {
  CreateInteractionRequest,
  InteractionResponse,
  InteractionType,
  Page,
  UserContentInteractionStateResponse,
} from './types';

export interface GetUserInteractionsParams {
  type?: InteractionType;
  limit?: number;
  cursor?: string;
}

export async function createInteraction(
  userId: string,
  payload: CreateInteractionRequest,
): Promise<InteractionResponse> {
  const { data } = await api.post<InteractionResponse>(
    `/users/${encodeURIComponent(userId)}/interactions`,
    payload,
  );
  return data;
}

export async function deleteInteraction(
  userId: string,
  contentId: string,
  type: InteractionType,
): Promise<void> {
  await api.delete(
    `/users/${encodeURIComponent(userId)}/interactions/${encodeURIComponent(contentId)}`,
    { params: { type } },
  );
}

export async function getUserInteractions(
  userId: string,
  params: GetUserInteractionsParams = {},
): Promise<Page<InteractionResponse>> {
  const response = await api.get<InteractionResponse[]>(
    `/users/${encodeURIComponent(userId)}/interactions`,
    { params: { type: params.type, limit: params.limit, cursor: params.cursor } },
  );
  return toPage(response);
}

export async function getInteractionState(
  userId: string,
  contentId: string,
): Promise<UserContentInteractionStateResponse> {
  const { data } = await api.get<UserContentInteractionStateResponse>(
    `/users/${encodeURIComponent(userId)}/contents/${encodeURIComponent(contentId)}/interactions`,
  );
  return data;
}
