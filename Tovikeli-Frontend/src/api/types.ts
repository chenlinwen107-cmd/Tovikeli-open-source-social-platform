export type UserStatus = 'ACTIVE' | 'DISABLED';

export type InteractionType = 'LIKE' | 'FAVORITE' | 'REPOST' | 'BOOKMARK';

export interface LoginRequest {
  username?: string | null;
  password?: string | null;
}

export interface CreateUserRequest {
  username?: string | null;
  password?: string | null;
  displayName?: string | null;
}

export interface UserResponse {
  id: string;
  username: string;
  displayName: string | null;
  status: string;
  createdAt: number;
  updatedAt: number;
  avatar: string | null;
  bio: string | null;
  gender: string | null;
  location: string | null;
  cover: string | null;
}

export interface CreateInteractionRequest {
  contentId?: string | null;
  type?: string | null;
}

export interface HydratedContentResponse {
  id: string;
  instanceId: string;
  sourceContentId: string;
  authorId: string;
  sourceId: string;
  text: string | null;
  visibility: string;
  status: string;
}

export interface InteractionResponse {
  id: string;
  userId: string;
  contentId: string;
  type: string;
  createdAt: number;
  content: HydratedContentResponse | null;
}

export interface UserContentInteractionStateResponse {
  contentId: string;
  like: boolean;
  favorite: boolean;
  repost: boolean;
  bookmark: boolean;
}

export interface InteractionStatisticsResponse {
  total: number;
  like: number;
  favorite: number;
  repost: number;
  bookmark: number;
}

export interface ContentResponse {
  id: string;
  instanceId: string;
  sourceContentId: string;
  authorId: string | null;
  author?: {
    id: string;
    sourceAuthorId: string;
    username: string;
    nickname: string;
    avatar: string | null;
  } | null;
  sourceId: string;
  text: string | null;
  language: string | null;
  visibility: string;
  status: string;
  mediaIds: string[];
  parentId: string | null;
  repostOfId: string | null;
  quoteOfId: string | null;
  externalCreatedAt: number | null;
  createdAt: number;
  updatedAt: number;
  metadata: Record<string, unknown>;
  location: string | null;
  locked?: boolean;
  anonymous?: boolean;
  commentCount: number;
  interactionStatistics: InteractionStatisticsResponse;
}

export interface RelatedContentResponse {
  id: string;
  instanceId: string;
  sourceContentId: string;
  authorId: string;
  sourceId: string;
  text: string | null;
  language: string | null;
  visibility: string;
  status: string;
  mediaIds: string[];
  parentId: string | null;
  repostOfId: string | null;
  quoteOfId: string | null;
  externalCreatedAt: number | null;
  createdAt: number;
  updatedAt: number;
  metadata: Record<string, unknown>;
}

export interface ContentRelationshipsResponse {
  parent: RelatedContentResponse | null;
  repostOf: RelatedContentResponse | null;
  quoteOf: RelatedContentResponse | null;
}

export interface CommentResponse {
  id: string;
  userId: string;
  contentId: string;
  parentCommentId: string | null;
  text: string;
  status: string;
  createdAt: number;
  updatedAt: number;
}

export interface PollResponse {
  id: string;
  contentId: string;
  multi: boolean;
  deadline: string | null;
  closed: boolean;
  totalVotes: number;
  voted: boolean;
  myVotes: string[];
  options: Array<{ id: string; text: string; votes: number }>;
}

export interface PublishContentRequest {
  text?: string | null;
  mediaIds?: string[];
  poll?: { options: string[]; multi: boolean; days: number } | null;
  location?: string | null;
  visibility?: string | null;
  password?: string | null;
  anonymous?: boolean;
}

export interface PublishContentResponse {
  id: string;
}

export interface CreateCommentRequest {
  text?: string | null;
  parentCommentId?: string | null;
}

export interface EditCommentRequest {
  text?: string | null;
}

export interface MediaResponse {
  id: string;
  status: string;
  sourceId: string;
  instanceId: string;
  thumbnailUrl: string | null;
  previewUrl: string | null;
  originalUrl: string | null;
  altText: string | null;
  license: string | null;
  metadata: Record<string, unknown>;
  createdAt: number;
  updatedAt: number;
  kind: string;
}

export interface TagResponse {
  id: string;
  name: string;
  normalizedName: string | null;
  origin: string;
  sourceTagId: string | null;
  instanceId: string | null;
  sourceId: string | null;
  createdAt: number;
  updatedAt: number;
}

export interface AuthorResponse {
  id: string;
  instanceId: string;
  sourceAuthorId: string;
  name: string;
  username: string;
  displayName: string | null;
  url: string | null;
  avatar: string | null;
  bio: string | null;
  status: string | null;
}

export interface InstanceResponse {
  id: string;
  sourceId: string;
  domain: string;
  displayName: string | null;
  enabled: boolean;
  allowIndex: boolean;
  allowMediaPreview: boolean;
  allowMediaCache: boolean;
  allowOriginalAccess: boolean;
  nsfwPolicy: string | null;
  healthStatus: string;
}

export interface Page<T> {
  items: T[];
  nextCursor: string | null;
  nextUrl: string | null;
}
