/**
 * Video page — Video Feed UI.
 *
 * Presentation only: there is no video backend, so no `<video>`, no iframe, no
 * remote media and no fabricated counts/creators. Cards are static, clearly
 * "coming soon" placeholders on a light surface over the shared background
 * video. Action glyphs reuse the existing icon system and are non-interactive.
 */

import { startPage } from '../app.js';
import { h } from '../ui.js';
import { icon } from '../icons.js';

document.body.classList.add('is-video');

const PLACEHOLDER_COUNT = 6;

/** Decorative, non-interactive action glyph (existing icons only). */
function action(name) {
  return h('span', { class: 'video-card__action', 'aria-hidden': 'true' }, icon(name));
}

function videoCard() {
  return h(
    'article',
    { class: 'video-card' },
    h(
      'div',
      { class: 'video-card__cover' },
      h('span', { class: 'video-card__badge', text: '即将开放' }),
      h('span', { class: 'video-card__play', 'aria-hidden': 'true' })
    ),
    h(
      'div',
      { class: 'video-card__body' },
      h('h2', { class: 'video-card__title', text: '视频内容' }),
      h('p', { class: 'video-card__meta muted small', text: '视频空间即将开放。' }),
      h(
        'div',
        { class: 'video-card__actions' },
        action('heart'),
        action('comment'),
        action('share'),
        action('more')
      )
    )
  );
}

startPage({
  title: '视频',
  async render(container) {
    container.append(
      h(
        'section',
        { class: 'page-shell video-page' },
        h(
          'div',
          { class: 'video-page__head' },
          h('h1', { class: 'video-page__title', text: '视频' }),
          h('p', { class: 'muted small', text: '沉浸式浏览精彩视频，即将开放。' })
        ),
        h(
          'div',
          { class: 'video-feed' },
          Array.from({ length: PLACEHOLDER_COUNT }, () => videoCard())
        )
      )
    );
  },
});
