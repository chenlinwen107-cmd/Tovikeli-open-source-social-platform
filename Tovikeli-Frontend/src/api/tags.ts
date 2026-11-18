import api from './client';
import { toPage } from './pagination';
import type { ContentResponse, Page, TagResponse } from './types';

export interface SearchTagsParams {
  q: string;
  limit?: number;
}

export interface GetTagContentsParams {
  limit?: number;
  cursor?: string;
}

export async function searchTags(params: SearchTagsParams): Promise<TagResponse[]> {
  const { data } = await api.get<TagResponse[]>('/tags', {
    params: { q: params.q, limit: params.limit },
  });
  return data;
}

export async function getTag(tagId: string): Promise<TagResponse> {
  const { data } = await api.get<TagResponse>(`/tags/${encodeURIComponent(tagId)}`);
  return data;
}

export async function getTagContents(
  tagId: string,
  params: GetTagContentsParams = {},
): Promise<Page<ContentResponse>> {
  const response = await api.get<ContentResponse[]>(
    `/tags/${encodeURIComponent(tagId)}/contents`,
    { params: { limit: params.limit, cursor: params.cursor } },
  );
  return toPage(response);
}
