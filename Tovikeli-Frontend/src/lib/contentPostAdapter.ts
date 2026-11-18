import type { AuthorResponse, ContentResponse, MediaResponse, PollResponse } from '../api/types';

export interface LegacyPost {
  id: string;
  author: AuthorResponse | null;
  content: string;
  visibility: string;
  createdAt: number;
  updatedAt: number;
  media: any[];
  poll: any | null;
  liked: boolean;
  likeCount: number;
  bookmarked: boolean;
  commentCount: number;
  shareCount: number;
  reactions: any;
  myReaction: string | null;
  location: string | null;
  views: number;
  edited: boolean;
  pinned: boolean;
  globalPinned: boolean;
  shared: any | null;
  sharedDeleted: boolean;
  locked: any | null;
}

export function adaptContentToPost(
  content: ContentResponse,
  author: AuthorResponse | null,
  mediaById: Map<string, MediaResponse> = new Map(),
  pollByContentId: Map<string, PollResponse> = new Map(),
): LegacyPost {
  const stats = content.interactionStatistics;

  return {
    id: content.id,
    author: content.anonymous ? ({ id: "anonymous", username: "anonymous", nickname: "匿名用户", avatar: "" } as any) : author,
    content: content.text ?? '',
    visibility: content.anonymous ? "anonymous" : content.visibility.toLowerCase(),
    createdAt: content.createdAt,
    updatedAt: content.updatedAt,
    media: content.mediaIds
      .map((id) => mediaById.get(id))
      .filter((media): media is MediaResponse => Boolean(media))
      .map((media) => ({
        id: media.id,
        type: media.kind.toLowerCase(),
        url: media.originalUrl ?? media.previewUrl ?? media.thumbnailUrl ?? '',
        poster: media.thumbnailUrl ?? media.previewUrl ?? undefined,
        title: media.metadata?.originalFilename?.toString(),
      }))
      .filter((media) => Boolean(media.url)),
    poll: pollByContentId.get(content.id) ?? null,
    liked: false,
    likeCount: stats.like ?? 0,
    bookmarked: false,
    commentCount: content.commentCount ?? 0,
    shareCount: stats.repost ?? 0,
    reactions: null,
    myReaction: null,
    location: content.location ?? null,
    views: 0,
    edited: content.updatedAt !== content.createdAt,
    pinned: false,
    globalPinned: false,
    shared: null,
    sharedDeleted: false,
    locked: null,
  };
}
