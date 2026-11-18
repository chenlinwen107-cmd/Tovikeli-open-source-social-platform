import api from './client';
import type { ContentResponse } from './types';

export interface SearchContentsParams {
  q: string;
  limit?: number;
  instanceId?: string;
  authorId?: string;
  sourceId?: string;
}

export async function searchContents(params: SearchContentsParams): Promise<ContentResponse[]> {
  const { data } = await api.get<ContentResponse[]>('/search', {
    params: {
      q: params.q,
      limit: params.limit,
      instanceId: params.instanceId,
      authorId: params.authorId,
      sourceId: params.sourceId,
    },
  });
  return data;
}
