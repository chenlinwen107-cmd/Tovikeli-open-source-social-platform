/**
 * Create page — publishing UI.
 *
 * The backend has no create/publish API and no media-upload API, so this page
 * is presentation only: the editor is real and typeable, while the media
 * picker, visibility control and publish action are genuinely disabled
 * (native `disabled`, no click handler, no network request, no fake success and
 * nothing persisted). Signed out, the publish area points to the real login
 * page instead of forcing a redirect.
 */

import { startPage } from '../app.js';
import { auth } from '../auth.js';
import { h } from '../ui.js';
import { icon } from '../icons.js';

/** Shown by the (disabled) visibility control until publishing exists. */
const VISIBILITY_PLACEHOLDER = '暂未开放';

startPage({
  title: '创建内容',
  async render(container) {
    const text = h('textarea', {
      id: 'create-text',
      class: 'composer__input create-text',
      rows: '8',
      placeholder: '分享你的内容…',
      'aria-label': '内容',
    });

    // Real entry point, kept disabled: no file input, no fake upload flow.
    const addMedia = h(
      'button',
      { type: 'button', class: 'btn', disabled: true },
      icon('plus'),
      h('span', { text: '添加图片' })
    );

    const visibility = h(
      'select',
      { id: 'create-visibility', disabled: true, 'aria-label': '可见性' },
      h('option', { text: VISIBILITY_PLACEHOLDER })
    );

    const publish = h('button', {
      type: 'button',
      class: 'btn btn--primary btn--block',
      disabled: true,
      text: '发布',
    });

    // Signed out: keep the skeleton and offer the real login page. Signed in:
    // nothing extra — the skeleton is the same, minus this hint.
    const signIn = auth.isAuthenticated()
      ? null
      : h(
          'div',
          { class: 'create-login' },
          h('p', { class: 'muted small', text: '登录后才能发布内容。' }),
          h('a', { class: 'btn', href: '/login.html', text: '去登录' })
        );

    container.append(
      h(
        'section',
        { class: 'page-shell' },
        h(
          'header',
          { class: 'create-head' },
          h('h1', { text: '创建内容' }),
          h('p', { class: 'muted', text: '分享你的内容。' })
        ),
        h(
          'section',
          { class: 'create-block' },
          h('h2', { class: 'create-block__title', text: '内容正文' }),
          text
        ),
        h(
          'section',
          { class: 'create-block' },
          h('h2', { class: 'create-block__title', text: '媒体' }),
          h('div', { class: 'create-media' }, addMedia),
          h('p', { class: 'muted small', text: '图片上传功能暂未开放。' })
        ),
        h(
          'section',
          { class: 'create-block' },
          h('h2', { class: 'create-block__title', text: '可见性' }),
          visibility,
          h('p', { class: 'muted small', text: '内容可见性将在发布功能开放后提供配置。' })
        ),
        h(
          'section',
          { class: 'create-block' },
          h('h2', { class: 'create-block__title', text: '发布操作' }),
          publish,
          h('p', { class: 'muted small', text: '发布功能暂未开放。' }),
          signIn
        )
      )
    );
  },
});
