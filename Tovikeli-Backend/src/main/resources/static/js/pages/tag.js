/**
 * Tag detail page.
 *
 * Uses two real endpoints:
 *  - `GET /api/v1/tags/{id}`             tag detail
 *  - `GET /api/v1/tags/{tagId}/contents` public content carrying that tag
 *
 * Tags exist as two distinct origins (PLATFORM / EXTERNAL) and the backend does
 * not treat different tag names as semantically equivalent, so the page shows
 * the stored name verbatim together with its origin instead of normalising it.
 */

import { startPage } from '../app.js';
import { api, ApiError } from '../api.js';
import { h, render, queryParam, emptyState, loadingState, badge } from '../ui.js';
import { mountPagedList } from '../paged-list.js';
import { contentCard } from '../content-card.js';
import { hydrateMedia } from '../feed-media.js';

startPage({
  title: '标签',
  async render(container) {
    const id = (queryParam('id') || '').trim();

    if (id === '') {
      container.append(
        h('h1', { text: '标签' }),
        emptyState(
          '未指定标签。',
          h('a', { class: 'btn', href: '/', text: '返回首页' })
        )
      );
      return;
    }

    const host = h('div', {});
    container.append(host);
    render(host, loadingState('正在加载标签…'));

    let tag;
    try {
      tag = await api.get(`/tags/${encodeURIComponent(id)}`);
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        render(
          host,
          emptyState(
            '未找到该标签。',
            h('a', { class: 'btn', href: '/', text: '返回首页' })
          )
        );
        return;
      }
      render(host, emptyState(error?.message || '标签加载失败。'));
      return;
    }

    render(host, tagHeader(tag));

    const listHost = h('div', {});
    container.append(h('h2', { class: 'section', text: '内容' }), listHost);

    await mountPagedList(listHost, {
      path: `/tags/${encodeURIComponent(id)}/contents`,
      query: { limit: 20 },
      itemRenderer: contentCard,
      emptyMessage: '暂无使用该标签的公开内容。',
      onLoaded: () => hydrateMedia(listHost),
    });
  },
});

/** Builds the tag header block. */
function tagHeader(tag) {
  return h(
    'div',
    { class: 'card' },
    h('h1', { text: tag.name }),
    h(
      'div',
      { class: 'card__meta' },
      badge(tag.origin === 'PLATFORM' ? '平台标签' : '外部标签'),
      tag.sourceTagId ? h('span', { text: `来源标签 ${tag.sourceTagId}` }) : null,
      tag.instanceId ? h('span', { text: tag.instanceId }) : null
    ),
    tag.normalizedName && tag.normalizedName !== tag.name
      ? h('p', { class: 'muted small', text: `规范化：${tag.normalizedName}` })
      : null
  );
}
