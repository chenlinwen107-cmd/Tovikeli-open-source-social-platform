import api from './client';
import { toPage } from './pagination';
import type {
  ContentRelationshipsResponse,
  ContentResponse,
  MediaResponse,
  Page,
  PublishContentRequest,
  PublishContentResponse,
  PollResponse,
} from './types';

export interface BrowseContentsParams {
  limit?: number;
  instanceId?: string;
  authorId?: string;
  hasMedia?: boolean;
  cursor?: string;
}

export async function browseContents(
  params: BrowseContentsParams = {},
): Promise<Page<ContentResponse>> {
  const response = await api.get<ContentResponse[]>('/contents', {
    params: {
      limit: params.limit,
      instanceId: params.instanceId,
      authorId: params.authorId,
      hasMedia: params.hasMedia,
      cursor: params.cursor,
    },
  });
  return toPage(response);
}

export async function getContent(contentId: string): Promise<ContentResponse> {
  const { data } = await api.get<ContentResponse>(`/contents/${encodeURIComponent(contentId)}`);
  return data;
}

export async function getContentMedia(contentId: string): Promise<MediaResponse[]> {
  const { data } = await api.get<MediaResponse[]>(
    `/contents/${encodeURIComponent(contentId)}/media`,
  );
  return data;
}

export async function getContentRelationships(
  contentId: string,
): Promise<ContentRelationshipsResponse> {
  const { data } = await api.get<ContentRelationshipsResponse>(
    `/contents/${encodeURIComponent(contentId)}/relationships`,
  );
  return data;
}

export async function editContent(
  contentId: string,
  request: { text: string },
): Promise<ContentResponse> {
  const { data } = await api.put<ContentResponse>(
    `/contents/${encodeURIComponent(contentId)}`,
    request,
  );
  return data;
}

export async function deleteContent(contentId: string): Promise<void> {
  await api.post(`/contents/${encodeURIComponent(contentId)}/delete`);
}

export async function hideContent(contentId: string): Promise<void> {
  await api.post(`/contents/${encodeURIComponent(contentId)}/hide`);
}

export async function restoreContent(contentId: string): Promise<void> {
  await api.post(`/contents/${encodeURIComponent(contentId)}/restore`);
}

export async function getPollBatch(contentIds: string[]): Promise<Record<string, PollResponse>> {
  if (contentIds.length === 0) return {};
  const { data } = await api.get<Record<string, PollResponse>>('/polls/batch', {
    params: { contentIds: contentIds.join(',') },
  });
  return data;
}

export async function publishContent(
  request: PublishContentRequest,
): Promise<PublishContentResponse> {
  const { data } = await api.post<PublishContentResponse>('/contents', request);
  return data;
}
