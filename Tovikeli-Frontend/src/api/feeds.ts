import api from './client';
import { toPage } from './pagination';
import type { ContentResponse, Page } from './types';
import type { FeedQuery } from '../lib/feed/types';

async function getFeed(path: string, query: FeedQuery = {}): Promise<Page<ContentResponse>> {
  const response = await api.get<ContentResponse[]>(path, {
    params: { limit: query.limit, cursor: query.cursor },
  });
  return toPage(response);
}

export function getRecommendedFeed(query: FeedQuery = {}) {
  return getFeed('/feed?type=recommend', query);
}

export function getLatestFeed(query: FeedQuery = {}) {
  return getFeed('/feed?type=latest', query);
}

export function getFollowingFeed(query: FeedQuery = {}) {
  return getFeed('/feed?type=following', query);
}

export function getVideoFeed(query: FeedQuery = {}) {
  return getFeed('/feed?type=video', query);
}
