export type FeedType = 'recommend' | 'latest' | 'following' | 'video';

export interface FeedQuery {
  limit?: number;
  cursor?: string;
}

export interface FeedTab {
  key: FeedType;
  label: string;
  requiresAuth?: boolean;
}
