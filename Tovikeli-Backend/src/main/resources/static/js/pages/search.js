/**
 * Search page.
 *
 * Uses two real endpoints:
 *  - `GET /api/v1/search?q=`  full-text content search (flat array, `Link` paging)
 *  - `GET /api/v1/tags?q=`    tag name lookup (flat array, no paging)
 *
 * Both require a non-blank `q`; the backend answers 400 otherwise, so a blank
 * query is handled here before any request is made.
 */

import { startPage } from '../app.js';
import { api } from '../api.js';
import { h, render, queryParam, emptyState, loadingState, errorState } from '../ui.js';
import { mountPagedList } from '../paged-list.js';
import { contentCard } from '../content-card.js';
import { hydrateMedia } from '../feed-media.js';

/** Number of tag suggestions requested alongside content results. */
const TAG_LIMIT = 10;

/**
 * Content results requested per search.
 *
 * The search endpoint accepts `limit` (max 100) but performs no keyset
 * pagination: it returns one bounded page and no `Link` header, so this is a
 * hard cap rather than a page size.
 */
const SEARCH_LIMIT = 20;

startPage({
  title: '搜索',
  async render(container) {
    const q = (queryParam('q') || '').trim();

    container.append(h('h1', { text: '搜索' }));

    if (q === '') {
      container.append(
        emptyState('请在上方输入关键词以搜索内容或标签。'),
        h(
          'p',
          { class: 'muted small' },
          '提示：你也可以浏览',
          h('a', { href: '/', text: '最新内容' }),
          '。'
        )
      );
      return;
    }

    container.append(h('p', { class: 'muted', text: `“${q}” 的搜索结果` }));

    // ---- Tags ----------------------------------------------------------
    const tagHost = h('div', {});
    container.append(h('h2', { class: 'section', text: '标签' }), tagHost);
    await renderTags(tagHost, q);

    // ---- Content -------------------------------------------------------
    const contentHost = h('div', {});
    container.append(h('h2', { class: 'section', text: '内容' }), contentHost);
    await mountPagedList(contentHost, {
      path: '/search',
      query: { q, limit: SEARCH_LIMIT },
      itemRenderer: contentCard,
      emptyMessage: `没有找到与“${q}”匹配的内容。`,
      onLoaded: () => hydrateMedia(contentHost),
    });

    // The search endpoint returns a single bounded page and sends no `Link`
    // header, so there is no "Load more" here. Saying so is more honest than
    // silently implying the list is complete.
    container.append(
      h('p', {
        class: 'muted small',
        text: `最多显示 ${SEARCH_LIMIT} 条结果，可使用更具体的关键词缩小范围。`,
      })
    );
  },
});

/** Renders matching tags as chips linking to the tag pages. */
async function renderTags(host, q) {
  render(host, loadingState('正在加载标签…'));

  let tags;
  try {
    tags = await api.get('/tags', { query: { q, limit: TAG_LIMIT } });
  } catch (error) {
    render(host, errorState(error?.message || '标签加载失败。'));
    return;
  }

  if (!Array.isArray(tags) || tags.length === 0) {
    render(host, emptyState('没有找到匹配的标签。'));
    return;
  }

  render(
    host,
    h(
      'ul',
      { class: 'tag-list' },
      tags.map((tag) =>
        h(
          'li',
          {},
          h(
            'a',
            { class: 'chip', href: `/tag.html?id=${encodeURIComponent(tag.id)}` },
            tag.name,
            tag.origin === 'PLATFORM' ? h('span', { class: 'reply-tag', text: ' · 平台' }) : null
          )
        )
      )
    )
  );
}
