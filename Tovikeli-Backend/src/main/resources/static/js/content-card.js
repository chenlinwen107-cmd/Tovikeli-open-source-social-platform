/**
 * Reusable Content presentation used by the home feed, search results, and the
 * author / tag listing pages.
 *
 * The card renders ONLY the fields the list endpoints actually return.
 *
 * Important contract detail: browse/search responses are built from
 * `ContentResponse.from(Content)`, which leaves `commentCount` and
 * `interactionStatistics` at their zero defaults. Those numbers are only real
 * on the content detail endpoint, so they are never shown on a card. The action
 * bar is mounted with `showCounts: false` for exactly this reason.
 *
 * List responses also carry only `authorId` (no author name/avatar). The author
 * header therefore renders a stable fallback from the id immediately and then
 * resolves the real author in place via `GET /api/v1/authors/{id}`. Requests are
 * cached and de-duplicated per authorId, and never block the feed.
 *
 * UI V1 structure:
 *
 *   author header (avatar + name + @username/id)
 *     -> content link (text preview + real metadata)
 *       -> media placeholder (.card__media hydration target)
 *         -> direct action bar (Like / Comment / Repost / Favorite / Bookmark)
 *
 * Interactive elements never nest: the author header and the content link are
 * separate links, media and the action bar are outside the content link.
 */

import { h, render, safeUrl, formatRelative, badge } from './ui.js';
import { api } from './api.js';
import { mountInteractionBar } from './interaction-bar.js';
import { openContentSheet } from './content-sheet.js';

/** Characters of content text shown on a card before truncation. */
const PREVIEW_LENGTH = 280;

/** Shortens text for a card preview without cutting mid-word where possible. */
function preview(text) {
  const trimmed = text.trim();
  if (trimmed.length <= PREVIEW_LENGTH) return trimmed;
  return `${trimmed.slice(0, PREVIEW_LENGTH).trimEnd()}…`;
}

/* --------------------------------------------------------- author lookup */

/** authorId -> normalised author (resolved once per page load). */
const authorCache = new Map();

/** authorId -> in-flight Promise, so N cards share one request. */
const authorInFlight = new Map();

/** A compact, layout-safe placeholder for a long public id. */
function shortAuthorId(id) {
  return id.length <= 16 ? id : `${id.slice(0, 14)}…`;
}

/** First non-empty (trimmed) string, or null. */
function firstNonEmpty(...values) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim() !== '') return value;
  }
  return null;
}

/** Keeps only the fields this UI displays, validating every one. */
function normaliseAuthor(raw) {
  if (!raw || typeof raw !== 'object') return null;
  if (typeof raw.id !== 'string' || raw.id === '') return null;
  return {
    id: raw.id,
    name: typeof raw.name === 'string' ? raw.name : null,
    username: typeof raw.username === 'string' ? raw.username : null,
    displayName: typeof raw.displayName === 'string' ? raw.displayName : null,
    avatar: typeof raw.avatar === 'string' ? raw.avatar : null,
  };
}

/**
 * Resolves an author once, de-duplicating concurrent lookups. Resolves to null
 * instead of throwing: authorship is cosmetic and must never break the list.
 *
 * @param {string} authorId
 * @returns {Promise<{id: string, name: string|null, username: string|null, displayName: string|null, avatar: string|null}|null>}
 */
function resolveAuthor(authorId) {
  if (typeof authorId !== 'string' || authorId === '') return Promise.resolve(null);
  if (authorCache.has(authorId)) return Promise.resolve(authorCache.get(authorId));

  const shared = authorInFlight.get(authorId);
  if (shared) return shared;

  const pending = api
    // Decorative request: a failure must not sign the caller out.
    .get(`/authors/${encodeURIComponent(authorId)}`, { notifyAuthLost: false })
    .then((raw) => {
      const author = normaliseAuthor(raw);
      if (author) authorCache.set(authorId, author);
      return author;
    })
    .catch(() => null)
    .finally(() => {
      authorInFlight.delete(authorId);
    });

  authorInFlight.set(authorId, pending);
  return pending;
}

/** The existing default avatar UI: no image, no fake URL. */
function defaultAvatar() {
  return h('span', { class: 'avatar', 'aria-hidden': 'true' });
}

/** Avatar element: a safe URL when present, the default avatar otherwise. */
function avatarElement(url) {
  return url
    ? h('img', {
        class: 'avatar',
        src: url,
        alt: '',
        loading: 'lazy',
        decoding: 'async',
        width: '36',
        height: '36',
      })
    : defaultAvatar();
}

/**
 * Builds the author header (above the post body). The whole area is one link to
 * the author page; the feed card never shows `author.url` or `author.bio`.
 */
function authorHeader(content) {
  const authorId = typeof content.authorId === 'string' ? content.authorId : '';
  const href = authorId ? `/author.html?id=${encodeURIComponent(authorId)}` : '/';

  const avatarHost = h('span', { class: 'content-card__author-avatar' });
  const nameEl = h('span', { class: 'content-card__author-name', text: '作者' });
  const handleEl = h('span', {
    class: 'content-card__author-handle',
    text: authorId ? shortAuthorId(authorId) : '',
  });

  render(avatarHost, defaultAvatar());

  const link = h(
    'a',
    { class: 'content-card__author', href },
    avatarHost,
    h('span', { class: 'content-card__author-text' }, nameEl, handleEl)
  );

  if (authorId) {
    resolveAuthor(authorId).then((author) => {
      // The card may have been removed while the lookup was in flight.
      if (!link.isConnected || !author) return;
      render(avatarHost, avatarElement(safeUrl(author.avatar)));

      const username = firstNonEmpty(author.username);
      const displayName = firstNonEmpty(author.displayName);
      const name = firstNonEmpty(author.name);

      // Primary: displayName, else name only when it adds information beyond the
      // @username, else a neutral label — never the same value twice.
      let primary = displayName || (name && name !== username ? name : null) || '作者';
      if (username && primary === username) primary = '作者';

      nameEl.textContent = primary;
      handleEl.textContent = username ? `@${username}` : shortAuthorId(authorId);
    });
  }

  return link;
}

/* -------------------------------------------------------------- the card */

/**
 * Renders one content card.
 * @param {object} content a ContentResponse from the backend
 * @returns {HTMLElement}
 */
export function contentCard(content) {
  const mediaIdList = Array.isArray(content.mediaIds) ? content.mediaIds : [];
  const mediaCount = mediaIdList.length;
  const created = content.externalCreatedAt ?? content.createdAt;

  // Media placeholder: list endpoints expose opaque mediaIds, not URLs. The
  // feed-media module fills this container with one batched media request per
  // rendered list. Class and dataset are the existing hydration contract and
  // must not change. It sits OUTSIDE the content link so the media frame's
  // retry button is never nested inside a link.
  const mediaHost =
    mediaCount > 0
      ? h('div', { class: 'card__media', dataset: { mediaIds: mediaIdList.join(',') } })
      : null;

  const meta = h(
    'div',
    { class: 'content-card__meta' },
    content.repostOfId ? badge('转发') : null,
    content.quoteOfId ? badge('引用') : null,
    mediaCount > 0 ? badge(`${mediaCount} 个媒体`) : null,
    created ? h('span', { text: formatRelative(created) }) : null,
    content.language ? h('span', { text: content.language }) : null
  );

  const main = h(
    'a',
    {
      class: 'content-link content-card__main',
      href: `/content.html?id=${encodeURIComponent(content.id)}`,
      onclick: (event) => {
        if (
          event.defaultPrevented ||
          event.button !== 0 ||
          event.metaKey ||
          event.ctrlKey ||
          event.shiftKey ||
          event.altKey
        ) {
          return;
        }

        event.preventDefault();
        openContentSheet(content.id);
      },
    },
    content.text
      ? h('p', { class: 'content-card__text', text: preview(content.text) })
      : h('p', { class: 'content-card__text content-card__text--empty', text: '（无正文）' }),
    meta
  );

  // Direct actions on the feed. Counts are hidden because list statistics are
  // zero defaults; Comment is a navigation link, not an interaction.
  const actionsHost = h('div', { class: 'content-card__actions' });
  mountInteractionBar(actionsHost, {
    contentId: content.id,
    showCounts: false,
    showComment: true,
    showHint: false,
  });

  return h('article', { class: 'card content-card' }, authorHeader(content), main, mediaHost, actionsHost);
}
