/**
 * Notifications page.
 *
 * The backend exposes no notifications API and there is no like / favorite /
 * comment / follow notification stream, so this page is presentation only: it
 * renders the shared empty state and never requests notification data, invents
 * counts, or persists anything locally. Signed out, it stays viewable and adds
 * a light note instead of redirecting.
 */

import { startPage } from '../app.js';
import { auth } from '../auth.js';
import { h, emptyState } from '../ui.js';

startPage({
  title: '通知',
  async render(container) {
    const signedOut = auth.isAuthenticated()
      ? null
      : h('p', {
          class: 'muted small notifications-note',
          text: '登录后，后续可在这里查看与你相关的通知。',
        });

    container.append(
      h(
        'section',
        { class: 'page-shell' },
        h('h1', { text: '通知' }),
        h('p', { class: 'muted', text: '查看与你相关的互动消息。' }),
        emptyState('暂无通知'),
        h('p', {
          class: 'muted small notifications-note',
          text: '当前版本暂未提供点赞、收藏、评论和关注通知。',
        }),
        signedOut
      )
    );
  },
});
