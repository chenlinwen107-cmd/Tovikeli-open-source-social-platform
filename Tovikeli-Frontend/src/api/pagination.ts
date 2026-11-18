import type { AxiosResponse } from 'axios';
import type { Page } from './types';

export function parseNextLink(link?: string | null): string | null {
  if (!link) return null;
  const match = /<([^>]+)>/.exec(link);
  return match ? match[1] : null;
}

export function parseCursorFromNextLink(link?: string | null): string | null {
  const url = parseNextLink(link);
  if (!url) return null;
  const queryIndex = url.indexOf('?');
  if (queryIndex < 0) return null;
  return new URLSearchParams(url.slice(queryIndex + 1)).get('cursor');
}

export function toPage<T>(response: AxiosResponse<T[]>): Page<T> {
  const headers = response.headers as unknown as Record<string, string | undefined>;
  const link = headers['link'] ?? headers['Link'];
  return {
    items: response.data,
    nextCursor: parseCursorFromNextLink(link),
    nextUrl: parseNextLink(link),
  };
}
