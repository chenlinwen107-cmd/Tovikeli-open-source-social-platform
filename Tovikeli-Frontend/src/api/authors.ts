import api from './client';
import { toPage } from './pagination';
import type { AuthorResponse, ContentResponse, Page } from './types';

export interface GetAuthorContentsParams {
  limit?: number;
  cursor?: string;
}

export async function getAuthor(authorId: string): Promise<AuthorResponse> {
  const { data } = await api.get<AuthorResponse>(`/authors/${encodeURIComponent(authorId)}`);
  return data;
}

export async function getLocalAuthorByUserId(
  userId: string,
): Promise<AuthorResponse> {
  const { data } = await api.get<AuthorResponse>(
    `/authors/by-user/${encodeURIComponent(userId)}`,
  );
  return data;
}

export async function getAuthorContents(
  authorId: string,
  params: GetAuthorContentsParams = {},
): Promise<Page<ContentResponse>> {
  const response = await api.get<ContentResponse[]>(
    `/authors/${encodeURIComponent(authorId)}/contents`,
    { params: { limit: params.limit, cursor: params.cursor } },
  );
  return toPage(response);
}
