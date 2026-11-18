import api from './client';
import type { MediaResponse } from './types';

export async function getMedia(mediaId: string): Promise<MediaResponse> {
  const { data } = await api.get<MediaResponse>(`/media/${encodeURIComponent(mediaId)}`);
  return data;
}

export async function getMediaBatch(ids: string[]): Promise<MediaResponse[]> {
  if (ids.length === 0) {
    throw new Error('ids must not be empty');
  }
  const { data } = await api.get<MediaResponse[]>('/media/batch', {
    params: { ids: ids.join(',') },
  });
  return data;
}
