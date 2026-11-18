/**
 * Author detail page.
 *
 * Uses two real endpoints:
 *  - `GET /api/v1/authors/{id}`            author detail
 *  - `GET /api/v1/authors/{authorId}/contents`  that author's public content
 *
 * The author name/avatar are resolved here (one request), which is affordable
 * on a detail page — the home feed deliberately does not do this per card.
 */

import { startPage } from '../app.js';
import { api, ApiError } from '../api.js';
import { h, render, queryParam, emptyState, loadingState, authorRow, safeUrl } from '../ui.js';
import { mountPagedList } from '../paged-list.js';
import { contentCard } from '../content-card.js';
import { hydrateMedia } from '../feed-media.js';

startPage({
  title: '作者',
  async render(container) {
    const id = (queryParam('id') || '').trim();

    if (id === '') {
      container.append(
        h('h1', { text: '作者' }),
        emptyState(
          '未指定作者。',
          h('a', { class: 'btn', href: '/', text: '返回首页' })
        )
      );
      return;
    }

    const host = h('div', {});
    container.append(host);
    render(host, loadingState('正在加载作者…'));

    let author;
    try {
      author = await api.get(`/authors/${encodeURIComponent(id)}`);
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        render(
          host,
          emptyState(
            '未找到该作者。',
            h('a', { class: 'btn', href: '/', text: '返回首页' })
          )
        );
        return;
      }
      render(host, emptyState(error?.message || '作者加载失败。'));
      return;
    }

    render(host, authorHeader(author));

    const listHost = h('div', {});
    container.append(h('h2', { class: 'section', text: '内容' }), listHost);

    await mountPagedList(listHost, {
      path: `/authors/${encodeURIComponent(id)}/contents`,
      query: { limit: 20 },
      itemRenderer: contentCard,
      emptyMessage: '该作者暂无公开内容。',
      onLoaded: () => hydrateMedia(listHost),
    });
  },
});

/** Builds the author profile block. */
function authorHeader(author) {
  const profileUrl = safeUrl(author.url);
  const handle = author.username ? `@${author.username}` : null;

  const meta = h(
    'div',
    { class: 'card__meta' },
    handle ? h('span', { text: handle }) : null,
    author.sourceAuthorId ? h('span', { text: `来源 ID ${author.sourceAuthorId}` }) : null,
    author.status ? h('span', { text: author.status }) : null
  );

  return h(
    'div',
    { class: 'card' },
    authorRow(author),
    h('div', { style: 'margin-top:10px' }, meta),
    author.bio ? h('p', { class: 'content-text', text: author.bio }) : null,
    profileUrl
      ? h(
          'p',
          { class: 'small' },
          h('a', {
            href: profileUrl,
            target: '_blank',
            rel: 'noopener noreferrer nofollow',
            text: '查看原作者主页',
          })
        )
      : null,
    h('p', { class: 'muted small', text: `来自实例 ${author.instanceId} 的外部作者。` })
  );
}
