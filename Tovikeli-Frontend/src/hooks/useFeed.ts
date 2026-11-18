import { useCallback, useEffect, useRef, useState } from 'react';
import { getAuthor, getLocalAuthorByUserId } from '../api/authors';
import { getMediaBatch } from '../api/media';
import { getPollBatch } from '../api/contents';
import {
  getFollowingFeed,
  getLatestFeed,
  getRecommendedFeed,
  getVideoFeed,
} from '../api/feeds';
import type { AuthorResponse, ContentResponse, MediaResponse, PollResponse } from '../api/types';
import { adaptContentToPost, type LegacyPost } from '../lib/contentPostAdapter';
import type { FeedQuery, FeedType } from '../lib/feed/types';

const PAGE_SIZE = 20;

const loaders = {
  recommend: getRecommendedFeed,
  latest: getLatestFeed,
  following: getFollowingFeed,
  video: getVideoFeed,
} satisfies Record<FeedType, (query?: FeedQuery) => Promise<{ items: ContentResponse[]; nextCursor: string | null; nextUrl: string | null }>>;

export function useFeed(type: FeedType, enabled = true) {
  const [items, setItems] = useState<LegacyPost[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusCode, setStatusCode] = useState<number | null>(null);
  const [hasMore, setHasMore] = useState(true);

  const cursorRef = useRef<string | null>(null);
  const hasMoreRef = useRef(true);
  const loadingRef = useRef(false);
  const authorsRef = useRef<Map<string, AuthorResponse>>(new Map());
  const typeRef = useRef(type);

  const hydratePosts = useCallback(async (contents: ContentResponse[]) => {
    const missingIds = [...new Set(contents.map((content) => content.authorId).filter((id): id is string => id !== null))].filter((id) => !authorsRef.current.has(id));
    if (missingIds.length > 0) {
      const results = await Promise.all(missingIds.map(async (authorId) => {
        try { return [authorId, await getAuthor(authorId)] as const; }
        catch { return [authorId, null] as const; }
      }));
      for (const [authorId, author] of results) {
        if (author) authorsRef.current.set(authorId, author);
      }
    }
    const mediaIds = [...new Set(
      contents.flatMap((content) => content.mediaIds ?? []).filter(Boolean),
    )];

    let mediaById = new Map<string, MediaResponse>();
    let pollByContentId = new Map<string, PollResponse>();

    if (mediaIds.length > 0) {
      try {
        const media = await getMediaBatch(mediaIds);
        mediaById = new Map(media.map((item) => [item.id, item]));
      } catch {
        // Keep posts visible even if media hydration fails.
      }
    }

    const pollIds = contents.map((content) => content.id);
    if (pollIds.length > 0) {
      try {
        pollByContentId = new Map(Object.entries(await getPollBatch(pollIds)));
      } catch {
        // Keep posts visible if poll hydration fails.
      }
    }

    return contents.map((content) =>
      adaptContentToPost(
        content,
        content.authorId ? authorsRef.current.get(content.authorId) ?? null : null,
        mediaById,
        pollByContentId,
      ),
    );
  }, []);

  const load = useCallback(async (reset = false) => {
    if (!enabled || loadingRef.current) return;
    if (!reset && !hasMoreRef.current) return;
    loadingRef.current = true;
    setError(null);
    setStatusCode(null);
    if (reset) {
      setLoading(true);
      cursorRef.current = null;
      hasMoreRef.current = true;
      setHasMore(true);
      authorsRef.current.clear();
    } else {
      setLoadingMore(true);
    }
    try {
      const page = await loaders[type]({
        limit: PAGE_SIZE,
        cursor: reset ? undefined : cursorRef.current ?? undefined,
      });
      if (typeRef.current !== type) return;
      const posts = await hydratePosts(page.items);
      if (typeRef.current !== type) return;
      setItems((prev) => reset ? posts : [...prev, ...posts]);
      cursorRef.current = page.nextCursor;
      hasMoreRef.current = Boolean(page.nextCursor);
      setHasMore(Boolean(page.nextCursor));
    } catch (e) {
      if (typeRef.current === type) {
        const status = typeof e === 'object' && e !== null && 'response' in e
          ? (e as { response?: { status?: number } }).response?.status ?? null
          : null;
        setStatusCode(status);
        setError(
          status === 401 || status === 403
            ? '请先登录后查看此动态'
            : status === 501
              ? '这个动态频道还在开发中'
              : e instanceof Error ? e.message : '加载内容失败',
        );
      }
    } finally {
      if (typeRef.current === type) {
        loadingRef.current = false;
        setLoading(false);
        setLoadingMore(false);
      }
    }
  }, [enabled, hydratePosts, type]);

  useEffect(() => {
    typeRef.current = type;
    cursorRef.current = null;
    hasMoreRef.current = true;
    setHasMore(true);
    setItems([]);
    setError(null);
    setStatusCode(null);
    setLoadingMore(false);
    loadingRef.current = false;
    void load(true);
  }, [type, enabled, load]);

  return {
    items,
    loading,
    loadingMore,
    error,
    statusCode,
    hasMore,
    reload: () => load(true),
    loadMore: () => load(false),
  };
}
