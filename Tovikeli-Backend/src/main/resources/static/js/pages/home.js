/**
 * Home feed renderer.
 *
 * Exposes `renderHomeFeed(container)` so the Home Dashboard (Step 1) can mount
 * the existing, verified feed in place — no full navigation, so the background
 * video keeps playing. The feed endpoint, keyset pagination, content renderer
 * and media hydration are unchanged.
 *
 * `/` renders the dashboard; the feed is shown on `#feed` (see dashboard.js).
 */

import { auth } from '../auth.js';
import { h } from '../ui.js';
import { mountPagedList } from '../paged-list.js';
import { contentCard } from '../content-card.js';
import { hydrateMedia } from '../feed-media.js';

/**
 * Renders the public feed into `container`.
 * @param {HTMLElement} container
 * @returns {Promise<void>}
 */
export async function renderHomeFeed(container) {
  container.append(
    h('h1', { text: '最新内容' }),
    h('p', {
      class: 'muted',
      text: '来自已连接来源平台的公开内容，按时间倒序排列。',
    })
  );

  if (!auth.isAuthenticated()) {
    container.append(
      h(
        'div',
        { class: 'notice' },
        '浏览对所有人开放，',
        h('a', { href: '/login.html', text: '登录' }),
        ' 或 ',
        h('a', { href: '/register.html', text: '注册' }),
        ' 后即可参与讨论。'
      )
    );
  }

  const listHost = h('div', {});
  container.append(listHost);

  await mountPagedList(listHost, {
    path: '/feed',
    query: { limit: 20 },
    itemRenderer: contentCard,
    emptyMessage: '暂无内容，请稍后再来看看。',
    onLoaded: () => hydrateMedia(listHost),
    infiniteScroll: true,
  });
}
