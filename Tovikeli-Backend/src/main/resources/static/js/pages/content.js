/**
 * Content detail page.
 *
 * Real endpoints used:
 *  - `GET /api/v1/contents/{id}`                 detail (includes real comment
 *                                                count + interaction statistics)
 *  - `GET /api/v1/contents/{id}/media`           attached media (policy-filtered)
 *  - `GET /api/v1/contents/{id}/relationships`   parent / repostOf / quoteOf
 *
 * Two interactive areas are delegated to the shared P5 components rather than
 * implemented here, so the detail page and any future feed cannot disagree:
 *  - `interaction-bar.js` — Like / Favorite / Repost / Bookmark, backed by
 *    `interactions.js` (server-authoritative, optimistic, rolled back on
 *    failure).
 *  - `comments.js`        — threaded comment list, composer, reply, edit,
 *    delete and pagination.
 *
 * Only the detail request is mandatory. The media and relationship requests are
 * best-effort: if one fails, that section says so while the rest of the page
 * keeps working. The author is shown on the feed card and the author page, so
 * the detail page does not fetch or render a separate author block.
 *
 * There is no tag list here on purpose: the backend exposes tag -> content but
 * no content -> tags endpoint, so per-content tags cannot be shown without
 * inventing an API.
 */

import { startPage } from '../app.js';
import { api, ApiError } from '../api.js';
import {
  h,
  render,
  emptyState,
  loadingState,
  badge,
  formatDate,
  formatRelative,
  safeUrl,
  queryParam,
} from '../ui.js';
import { mountInteractionBar } from '../interaction-bar.js';
import { mountComments } from '../comments.js';
// Shared single-vs-rail media presentation (owned by feed-media.js) so Content
// Detail and the Feed render identical galleries. Images open the shared
// lightbox via the per-image handler inside feed-media.js.
import { buildMediaGallery } from '../feed-media.js';

/** Characters of a related content's text shown in the relationship block. */
const RELATION_PREVIEW_LENGTH = 200;

export async function renderContentDetail(container, id) {
    // Own the container completely. The Content Sheet pre-renders a loading
    // placeholder before calling this function; without clearing it first, that
    // placeholder stayed stranded at the top of the sheet forever (the "loading
    // never finishes" symptom), because this function only APPENDS its own host.
    // Clearing here is safe for the standalone /content.html page too (startPage
    // already cleared #page).
    render(container);

    const host = h('div', {});
    container.append(host);
    render(host, loadingState('正在加载内容…'));

    let content;
    try {
      content = await api.get(`/contents/${encodeURIComponent(id)}`);
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        render(
          host,
          emptyState(
            '未找到该内容，可能已被删除。',
            h('a', { class: 'btn', href: '/', text: '返回首页' })
          )
        );
        return;
      }
      render(host, emptyState(error?.message || '内容加载失败。'));
      return;
    }

    // Live values for the detail block. `comments` is seeded from the detail
    // response (the only place the backend reports the real count) and kept in
    // sync by the comments component; the four reaction counters are kept in
    // sync by the interaction bar. Nothing here is invented: the initial numbers
    // come from `interactionStatistics` / `commentCount`.
    const counts = {
      comments: h('strong', { text: '0' }),
      total: h('strong', { text: '0' }),
      like: h('strong', { text: '0' }),
      favorite: h('strong', { text: '0' }),
      repost: h('strong', { text: '0' }),
      bookmark: h('strong', { text: '0' }),
    };

    const reactionsHost = h('div', {});

    render(
      host,
      contentBody(content, counts),
      reactionsHost,
      h('div', { class: 'section' }, h('h2', { text: '媒体' }), mediaHost(content)),
      relationshipsHost(content)
    );

    // ---- Reactions -----------------------------------------------------
    const interactionBar = mountInteractionBar(reactionsHost, {
      contentId: content.id,
      statistics: content.interactionStatistics,
      onStatisticsChange: (next) => {
        counts.like.textContent = String(next.LIKE);
        counts.favorite.textContent = String(next.FAVORITE);
        counts.repost.textContent = String(next.REPOST);
        counts.bookmark.textContent = String(next.BOOKMARK);
        counts.total.textContent = String(next.LIKE + next.FAVORITE + next.REPOST + next.BOOKMARK);
      },
    });

    // ---- Comments ------------------------------------------------------
    // Appended before mounting so the loading state is visible immediately;
    // `mountComments` renders its own heading and handles the rest.
    const commentsHost = h('div', {});
    container.append(commentsHost);
    await mountComments(commentsHost, {
      contentId: content.id,
      commentCount: Number(content.commentCount) || 0,
      onCountChange: (count) => {
        counts.comments.textContent = String(count);
      },
    });

    // Teardown so the Content Sheet can release the interaction subscription
    // when it replaces/closes this detail. Without it, every sheet open leaks a
    // subscriber into interactions.js (unbounded growth over repeated opens).
    return () => interactionBar.destroy();
}


if (window.location.pathname === '/content.html') {
  startPage({
    title: '内容',
    async render(container) {
      const id = (queryParam('id') || '').trim();

      if (id === '') {
        container.append(
          h('h1', { text: '内容' }),
          emptyState(
            '未指定内容。',
            h('a', { class: 'btn', href: '/', text: '返回首页' })
          )
        );
        return;
      }

      return renderContentDetail(container, id);
    },
  });
}

/* -------------------------------------------------------------- content body */

/**
 * Renders the main content card, including the real detail-only statistics.
 *
 * @param {object} content the content detail response
 * @param {Record<string, HTMLElement>} counts live value nodes owned by the page
 */
function contentBody(content, counts) {
  const stats = content.interactionStatistics || {};
  const created = content.externalCreatedAt ?? content.createdAt;

  counts.comments.textContent = String(Number(content.commentCount) || 0);
  counts.total.textContent = String(stats.total ?? 0);
  counts.like.textContent = String(stats.like ?? 0);
  counts.favorite.textContent = String(stats.favorite ?? 0);
  counts.repost.textContent = String(stats.repost ?? 0);
  counts.bookmark.textContent = String(stats.bookmark ?? 0);

  return h(
    'article',
    { class: 'card' },
    content.text
      ? h('p', { class: 'content-text', text: content.text })
      : h('p', { class: 'content-text muted', text: '（无正文）' }),
    h(
      'div',
      { class: 'card__meta' },
      badge(content.status),
      badge(content.visibility),
      content.language ? h('span', { text: content.language }) : null,
      h('span', { text: `来源 ${content.sourceId}` }),
      h('span', { text: `实例 ${content.instanceId}` })
    ),
    h(
      'div',
      { class: 'stat-row' },
      h('span', {}, '评论：', counts.comments),
      h('span', {}, '互动：', counts.total),
      h('span', {}, '点赞：', counts.like),
      h('span', {}, '收藏：', counts.favorite),
      h('span', {}, '转发：', counts.repost),
      h('span', {}, '书签：', counts.bookmark)
    ),
    h(
      'div',
      { class: 'card__meta', style: 'margin-top:8px' },
      h('span', { text: `外部 ID ${content.sourceContentId}` }),
      created ? h('span', { title: formatDate(created), text: `发布于 ${formatRelative(created)}` }) : null,
      content.createdAt ? h('span', { text: `收录于 ${formatRelative(content.createdAt)}` }) : null
    )
  );
}

/* --------------------------------------------------------------------- media */

/** Media block: the backend already applied the instance's media policy. */
function mediaHost(content) {
  const host = h('div', { class: 'card' });
  render(host, h('div', { class: 'muted small', text: '正在加载媒体…' }));

  api
    .get(`/contents/${encodeURIComponent(content.id)}/media`)
    .then((mediaList) => {
      if (!Array.isArray(mediaList) || mediaList.length === 0) {
        render(host, h('p', { class: 'muted small', text: '此内容没有媒体附件。' }));
        return;
      }
      const gallery = buildMediaGallery(mediaList);
      if (!gallery) {
        render(host, h('p', { class: 'muted small', text: '此内容没有可显示的媒体。' }));
        return;
      }
      // Gallery (single image or one horizontal rail) + metadata kept OUTSIDE
      // the scrolling track.
      render(host, gallery, mediaMeta(mediaList));
    })
    .catch(() => render(host, h('p', { class: 'muted small', text: '媒体加载失败。' })));

  return host;
}

/**
 * Per-media metadata (license / original link) rendered below the gallery, so it
 * stays accessible without living inside the horizontal track.
 * @param {object[]} mediaList MediaResponse items
 * @returns {HTMLElement}
 */
function mediaMeta(mediaList) {
  const entries = mediaList
    .map((media, index) => ({ media, index }))
    .filter(({ media }) => safeUrl(media.originalUrl) || (typeof media.license === 'string' && media.license !== ''));

  if (entries.length === 0) {
    return h('p', { class: 'muted small', text: '媒体来自原始平台。' });
  }

  const multiple = mediaList.length > 1;
  return h(
    'div',
    { class: 'media-meta' },
    ...entries.map(({ media, index }) => {
      const original = safeUrl(media.originalUrl);
      const license = typeof media.license === 'string' && media.license !== '' ? media.license : null;
      return h(
        'div',
        { class: 'media-item__note' },
        multiple ? h('span', { text: `媒体 ${index + 1}` }) : null,
        license ? h('span', { text: `${multiple ? ' · ' : ''}${license}` }) : null,
        original
          ? h('a', {
              class: 'media-item__note-link',
              href: original,
              target: '_blank',
              rel: 'noopener noreferrer nofollow',
              text: `${multiple || license ? ' · ' : ''}查看原内容`,
            })
          : null
      );
    })
  );
}

/* ------------------------------------------------------------- relationships */

/** Parent / repost / quote block. Hidden entirely when there are none. */
function relationshipsHost(content) {
  const host = h('div', { class: 'section' });
  const placeholder = h('div', {});
  render(host, h('h2', { text: '相关内容' }), placeholder);

  api
    .get(`/contents/${encodeURIComponent(content.id)}/relationships`)
    .then((relationships) => {
      const entries = [
        ['父内容', relationships?.parent],
        ['转发自', relationships?.repostOf],
        ['引用的内容', relationships?.quoteOf],
      ].filter(([, item]) => item != null);

      if (entries.length === 0) {
        // Nothing related: drop the whole section rather than showing an empty box.
        render(host);
        return;
      }

      render(
        host,
        h('h2', { text: '相关内容' }),
        entries.map(([label, item]) =>
          h(
            'div',
            { class: 'card' },
            h('div', { class: 'card__meta' }, badge(label)),
            item.text
              ? h('p', { class: 'content-text', text: previewRelation(item.text) })
              : h('p', { class: 'content-text muted', text: '（无正文）' }),
            h('a', {
              href: `/content.html?id=${encodeURIComponent(item.id)}`,
              text: '打开内容',
            })
          )
        )
      );
    })
    .catch(() => render(host));

  return host;
}

/** Truncates a related content's text for the preview. */
function previewRelation(text) {
  const trimmed = text.trim();
  return trimmed.length <= RELATION_PREVIEW_LENGTH
    ? trimmed
    : `${trimmed.slice(0, RELATION_PREVIEW_LENGTH).trimEnd()}…`;
}
