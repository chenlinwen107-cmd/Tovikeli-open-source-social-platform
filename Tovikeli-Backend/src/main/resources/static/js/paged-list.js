/**
 * Shared "list with load-more" behaviour.
 *
 * The backend uses stateless keyset pagination and advertises the next page via
 * `Link: <...>; rel="next"`. This helper loads the first page, renders each item
 * and, when a next page exists, offers a touch-friendly "Load more" button that
 * appends the following page.
 *
 * It also owns the loading / empty / error states for a list region so each page
 * does not repeat that logic.
 *
 * Optional automatic loading: when `infiniteScroll` is true, a lightweight
 * sentinel is observed with `IntersectionObserver` and the next page is fetched
 * automatically as the sentinel approaches the viewport — no click required.
 * The default (`false`) keeps the original "Load more" button, so Search,
 * Author and Tag are unchanged. The next page is ALWAYS the URL the server
 * returned in the `Link` header; the cursor is never decoded or rebuilt here.
 *
 * Returns a handle so a page can insert an item it just created (for example a
 * freshly posted comment) without reloading the whole list.
 */

import { api } from './api.js';
import { h, render, emptyState, errorState, loadingState, notice } from './ui.js';

/** Maximum characters of a backend error message shown to the user. */
const MAX_MESSAGE_LENGTH = 200;

/** How far before the viewport the auto-load sentinel triggers. */
const AUTO_LOAD_MARGIN = '200px 0px';

/** @param {unknown} error */
function messageOf(error) {
  const text = error instanceof Error && error.message ? error.message : '出错了。';
  return text.slice(0, MAX_MESSAGE_LENGTH);
}

/**
 * @typedef {object} PagedListHandle
 * @property {HTMLElement} list the element new items are appended to
 * @property {(item: any) => void} prependItem insert one item at the top
 * @property {() => void} dispose stop any automatic loading
 */

/**
 * Mounts a paginated list into `host`.
 *
 * @param {HTMLElement} host container to render into
 * @param {{
 *   path: string,
 *   query?: Record<string, unknown>,
 *   itemRenderer: (item: any) => Node,
 *   emptyMessage?: string,
 *   listClass?: string,
 *   onLoaded?: (items: any[], isFirstPage: boolean) => void,
 *   infiniteScroll?: boolean,
 * }} options
 * @returns {Promise<PagedListHandle>}
 */
export async function mountPagedList(host, options) {
  const {
    path,
    query,
    itemRenderer,
    emptyMessage = '暂无内容',
    listClass = '',
    onLoaded,
    infiniteScroll = false,
  } = options;

  // Automatic loading needs IntersectionObserver. When it is not available the
  // helper quietly falls back to the "Load more" button so the list can always
  // be extended.
  const autoLoad = infiniteScroll === true && typeof IntersectionObserver === 'function';

  const list = h('div', { class: listClass });
  const tail = h('div', {});

  /** @type {string|null} */
  let nextUrl = null;
  let loading = false;
  let disposed = false;
  /** @type {IntersectionObserver|null} */
  let observer = null;

  // Stable auto-load sentinel. Created once so the observer always watches the
  // same node even across re-renders (no observer accumulation, no leak).
  const autoTail = h('div', { class: 'load-more', role: 'status', style: 'min-height: 1px;' });

  function updateAutoTail() {
    autoTail.textContent = loading ? '正在加载…' : '';
  }

  /** Disconnects the observer; safe to call repeatedly. */
  function stopAuto() {
    if (observer) {
      observer.disconnect();
      observer = null;
    }
  }

  /** Creates the single observer for this list, if one is needed. */
  function ensureObserver() {
    if (!autoLoad || observer || disposed || !nextUrl) return;
    observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            loadMore();
          }
        }
      },
      { rootMargin: AUTO_LOAD_MARGIN }
    );
    observer.observe(autoTail);
  }

  /** Re-checks the sentinel after content was appended below it. */
  function reobserve() {
    if (!observer) {
      ensureObserver();
      return;
    }
    observer.unobserve(autoTail);
    observer.observe(autoTail);
  }

  /** Renders the manual "Load more" button (non auto-loading pages). */
  function renderButtonTail() {
    const button = h('button', {
      type: 'button',
      class: 'btn',
      text: '加载更多',
      onclick: () => loadMore(button),
    });
    render(tail, h('div', { class: 'load-more' }, button));
  }

  /** Renders the existing inline error notice plus a retry button. */
  function renderRetry(message) {
    const retry = h('button', {
      type: 'button',
      class: 'btn',
      text: '重试',
      onclick: () => loadMore(retry),
    });
    render(tail, notice(message, 'error'), h('div', { class: 'load-more' }, retry));
  }

  /** Renders the current tail: auto sentinel, button, or nothing. */
  function renderTail() {
    if (!nextUrl) {
      render(tail);
      stopAuto();
      return;
    }
    if (autoLoad) {
      updateAutoTail();
      render(tail, autoTail);
      ensureObserver();
      return;
    }
    renderButtonTail();
  }

  /**
   * Loads the next page using the server-provided next URL. Guarded so a single
   * list never issues two overlapping requests (auto + button, repeated
   * sentinel triggers, or a double tap).
   *
   * @param {HTMLButtonElement} [button] the button that triggered the load
   */
  async function loadMore(button) {
    if (disposed || loading || !nextUrl) return;
    const url = nextUrl;
    loading = true;
    if (button) {
      button.disabled = true;
      button.textContent = '正在加载…';
    }
    if (autoLoad) {
      updateAutoTail();
    }

    try {
      const next = await api.getPageByUrl(url);
      for (const item of next.items) {
        list.append(itemRenderer(item));
      }
      nextUrl = next.nextUrl;
      loading = false;
      onLoaded?.(next.items, false);

      if (!nextUrl) {
        render(tail);
        stopAuto();
      } else if (autoLoad) {
        updateAutoTail();
        render(tail, autoTail);
        reobserve();
      } else {
        renderButtonTail();
      }
    } catch (error) {
      loading = false;
      // No automatic retry loop: the user decides when to try again.
      stopAuto();
      renderRetry(messageOf(error));
    }
  }

  /** Makes sure `list` is the attached list node (it may be detached when empty). */
  function ensureListAttached() {
    if (!host.contains(list)) {
      render(host, list, tail);
      renderTail();
    }
  }

  const handle = {
    list,
    prependItem(item) {
      list.prepend(itemRenderer(item));
      ensureListAttached();
    },
    dispose() {
      disposed = true;
      stopAuto();
    },
  };

  // ---- first page ------------------------------------------------------
  render(host, loadingState());

  let firstPage;
  try {
    firstPage = await api.getPage(path, { query });
  } catch (error) {
    render(host, errorState(messageOf(error)));
    return handle;
  }

  if (firstPage.items.length === 0) {
    render(host, emptyState(emptyMessage));
    onLoaded?.([], true);
    return handle;
  }

  for (const item of firstPage.items) {
    list.append(itemRenderer(item));
  }
  nextUrl = firstPage.nextUrl;
  render(host, list, tail);
  renderTail();
  onLoaded?.(firstPage.items, true);

  return handle;
}
