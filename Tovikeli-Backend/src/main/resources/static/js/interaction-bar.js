/**
 * Interaction controls: Like / Repost / Favorite / Bookmark, plus an optional
 * Comment entry point.
 *
 * A small reusable component. It owns no state of its own — it renders whatever
 * `interactions.js` reports and forwards clicks to it, so the control can never
 * drift from the store.
 *
 * Behaviour:
 *  - signed out    -> if `showHint` is on, controls explain that signing in is
 *                     required and link to the login page; pressing a control
 *                     always explains it too.
 *  - signed in     -> pressing one toggles it optimistically; a failed write is
 *                     rolled back and reported, never shown as success.
 *  - in flight     -> the pressed control is disabled so a double tap cannot
 *                     produce a duplicate request.
 *
 * Options:
 *  - `showCounts` (default true): render the per-type count. Content detail
 *    uses the real detail statistics; list/feed surfaces pass false because
 *    list responses carry zero-default statistics that must never be shown.
 *  - `showComment` (default false): append a Comment link to the content's
 *    comments section. Comment is NOT an interaction type and this makes no
 *    interaction write.
 *  - `showHint` (default true): render the persistent signed-out hint. Feed
 *    cards pass false to avoid repeating it on every card.
 *
 * Accessibility: the bar is a labelled group of toggle buttons, each exposing
 * `aria-pressed` and an `aria-label` (which includes the count only when counts
 * are shown), with a polite live region for the outcome.
 */

import { h, render, notice } from './ui.js';
import { icon } from './icons.js';
import {
  INTERACTION_TYPES,
  INTERACTION_LABELS,
  load,
  subscribe,
  toggle,
} from './interactions.js';

/**
 * UI V1 Hugeicons builder per interaction type. Bookmark uses the dedicated
 * bookmark icon — never the Favorite star.
 */
const ICON_BUILDERS = Object.freeze({
  LIKE: () => icon('heart'),
  FAVORITE: () => icon('star'),
  REPOST: () => icon('repost'),
  BOOKMARK: () => icon('bookmark'),
});

/**
 * Display order of the bar. COMMENT is deliberately listed here but is not an
 * interaction type: it is rendered as a plain navigation link.
 */
const DISPLAY_ORDER = Object.freeze(['LIKE', 'COMMENT', 'REPOST', 'FAVORITE', 'BOOKMARK']);

/** Reads the current page as a same-site path, for the post-login return. */
function currentPath() {
  return `${window.location.pathname}${window.location.search}`;
}

/**
 * Maps a failed interaction write to an accurate, non-technical message.
 * @param {unknown} error
 * @returns {string}
 */
function writeErrorMessage(error) {
  const status = error && typeof error === 'object' ? error.status : 0;
  if (status === 400) return '该互动不适用于此内容。';
  if (status === 401) return '登录状态已失效，请重新登录。';
  if (status === 403) return '你的账号无权与此内容互动。';
  if (status === 404) return '该内容已不可用。';
  if (status === 409) return '无法对此内容进行互动。';
  if (status === 0) return '无法连接到服务器，请检查网络后重试。';
  return '互动保存失败，请重试。';
}

/**
 * Mounts the interaction bar into `host`.
 *
 * @param {HTMLElement} host
 * @param {{
 *   contentId: string,
 *   statistics?: {total?: number, like?: number, favorite?: number, repost?: number, bookmark?: number},
 *   onStatisticsChange?: (counts: Record<string, number>) => void,
 *   showCounts?: boolean,
 *   showComment?: boolean,
 *   showHint?: boolean,
 * }} options
 * @returns {{destroy: () => void}}
 */
export function mountInteractionBar(host, options) {
  const {
    contentId,
    onStatisticsChange,
    showCounts = true,
    showComment = false,
    showHint = true,
  } = options;

  // Local, optimistic copy of the four counters. Not rendered when showCounts
  // is false, but still tracked so an optimistic toggle stays consistent.
  const counts = {
    LIKE: Number(options.statistics?.like) || 0,
    FAVORITE: Number(options.statistics?.favorite) || 0,
    REPOST: Number(options.statistics?.repost) || 0,
    BOOKMARK: Number(options.statistics?.bookmark) || 0,
  };

  const statusHost = h('div', { class: 'reactions__status', role: 'status', 'aria-live': 'polite' });
  const promptHost = h('div', {});

  /** @type {Record<string, {button: HTMLButtonElement, count: HTMLElement|null}>} */
  const controls = {};

  /** Accessible name for one control (count included only when shown). */
  function labelFor(type) {
    return showCounts ? `${INTERACTION_LABELS[type]} ${counts[type]}` : INTERACTION_LABELS[type];
  }

  /** The Comment navigation entry. Not an interaction; performs no write. */
  function buildComment() {
    return h(
      'a',
      {
        class: 'reaction reaction--comment',
        href: `/content.html?id=${encodeURIComponent(contentId)}#comments`,
        'aria-label': '评论',
      },
      h('span', { class: 'reaction__icon', 'aria-hidden': 'true' }, icon('comment'))
    );
  }

  const bar = h('div', { class: 'reactions', role: 'group', 'aria-label': '与内容互动' });

  for (const type of DISPLAY_ORDER) {
    if (type === 'COMMENT') {
      if (showComment) bar.append(buildComment());
      continue;
    }

    const count = showCounts
      ? h('span', { class: 'reaction__count', text: String(counts[type]) })
      : null;

    const button = h(
      'button',
      {
        type: 'button',
        class: 'reaction',
        'data-type': type,
        'aria-pressed': 'false',
        'aria-label': labelFor(type),
        onclick: () => handlePress(type),
      },
      h('span', { class: 'reaction__icon', 'aria-hidden': 'true' }, ICON_BUILDERS[type]()),
      count
    );

    controls[type] = { button, count };
    bar.append(button);
  }

  render(host, bar, promptHost, statusHost);

  /** Applies a snapshot from the store to the controls. */
  function paint(snapshot) {
    for (const type of INTERACTION_TYPES) {
      const control = controls[type];
      if (!control) continue;

      const { button, count } = control;
      const active = snapshot.state[type.toLowerCase()];
      button.setAttribute('aria-pressed', active ? 'true' : 'false');
      button.disabled = snapshot.pending[type];
      button.setAttribute('aria-busy', snapshot.pending[type] ? 'true' : 'false');
      if (count) count.textContent = String(counts[type]);
      button.setAttribute('aria-label', labelFor(type));
    }

    // The signed-out hint is part of the bar's own state, so it appears and
    // disappears with the session rather than needing a page reload.
    if (showHint && !snapshot.authenticated) {
      render(
        promptHost,
        h(
          'p',
          { class: 'reactions__hint muted small' },
          h('a', {
            href: `/login.html?next=${encodeURIComponent(currentPath())}`,
            text: '登录',
          }),
          ' 后即可点赞、收藏、转发或添加书签。'
        )
      );
    } else {
      render(promptHost);
    }
  }

  /** One press: explain, or toggle. */
  async function handlePress(type) {
    render(statusHost);

    const result = await toggle(contentId, type);

    if (result.ok) {
      counts[type] = Math.max(0, counts[type] + (result.active ? 1 : -1));
      onStatisticsChange?.({ ...counts });
      render(
        statusHost,
        h('span', {
          class: 'muted small',
          text: result.active
            ? `${INTERACTION_LABELS[type]}成功。`
            : `已取消${INTERACTION_LABELS[type]}。`,
        })
      );
      return;
    }

    if (result.reason === 'unauthenticated') {
      // Defensive: the store refuses writes without a session, so the bar shows
      // the login route instead of a dead end. No new endpoint is involved.
      render(
        statusHost,
        notice('请登录后再进行互动。', 'error'),
        h('a', {
          class: 'btn btn--inline',
          href: `/login.html?next=${encodeURIComponent(currentPath())}`,
          text: '登录',
        })
      );
      return;
    }

    if (result.reason === 'pending') return;

    render(statusHost, notice(writeErrorMessage(result.error), 'error'));
  }

  const unsubscribe = subscribe(contentId, paint);

  // Reading the caller's own state is best-effort: if it fails the controls
  // still work, because the write is idempotent server-side.
  load(contentId);

  return {
    destroy() {
      unsubscribe();
    },
  };
}
