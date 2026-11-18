import type { FeedTab, FeedType } from './types';

export const FEED_TABS: FeedTab[] = [
  { key: 'recommend', label: '推荐' },
  { key: 'latest', label: '最新' },
  { key: 'following', label: '关注', requiresAuth: true },
  { key: 'video', label: '视频' },
];

export const FEED_DEFINITIONS: Record<FeedType, { label: string; requiresAuth: boolean }> = {
  recommend: { label: '推荐', requiresAuth: false },
  latest: { label: '最新', requiresAuth: false },
  following: { label: '关注', requiresAuth: true },
  video: { label: '视频', requiresAuth: false },
};
