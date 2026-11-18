import api from './client';
import { toPage } from './pagination';
import type {
  CommentResponse,
  CreateCommentRequest,
  EditCommentRequest,
  Page,
} from './types';

export interface ListCommentsParams {
  limit?: number;
  cursor?: string;
}

export async function createComment(
  contentId: string,
  payload: CreateCommentRequest,
): Promise<CommentResponse> {
  const { data } = await api.post<CommentResponse>(
    `/contents/${encodeURIComponent(contentId)}/comments`,
    payload,
  );
  return data;
}

export async function listComments(
  contentId: string,
  params: ListCommentsParams = {},
): Promise<Page<CommentResponse>> {
  const response = await api.get<CommentResponse[]>(
    `/contents/${encodeURIComponent(contentId)}/comments`,
    { params: { limit: params.limit, cursor: params.cursor } },
  );
  return toPage(response);
}

export async function getComment(commentId: string): Promise<CommentResponse> {
  const { data } = await api.get<CommentResponse>(`/comments/${encodeURIComponent(commentId)}`);
  return data;
}

export async function listReplies(
  commentId: string,
  params: ListCommentsParams = {},
): Promise<Page<CommentResponse>> {
  const response = await api.get<CommentResponse[]>(
    `/comments/${encodeURIComponent(commentId)}/replies`,
    { params: { limit: params.limit, cursor: params.cursor } },
  );
  return toPage(response);
}

export async function editComment(
  commentId: string,
  payload: EditCommentRequest,
): Promise<CommentResponse> {
  const { data } = await api.patch<CommentResponse>(
    `/comments/${encodeURIComponent(commentId)}`,
    payload,
  );
  return data;
}

export async function deleteComment(commentId: string): Promise<void> {
  await api.delete(`/comments/${encodeURIComponent(commentId)}`);
}


export interface CommentLikeResponse {
  liked: boolean;
  likeCount: number;
}

export async function getCommentLike(commentId: string): Promise<CommentLikeResponse> {
  const { data } = await api.get<CommentLikeResponse>(
    `/comments/${encodeURIComponent(commentId)}/like`,
  );
  return data;
}

export async function likeComment(commentId: string): Promise<CommentLikeResponse> {
  const { data } = await api.post<CommentLikeResponse>(
    `/comments/${encodeURIComponent(commentId)}/like`,
  );
  return data;
}

export async function unlikeComment(commentId: string): Promise<CommentLikeResponse> {
  const { data } = await api.delete<CommentLikeResponse>(
    `/comments/${encodeURIComponent(commentId)}/like`,
  );
  return data;
}
