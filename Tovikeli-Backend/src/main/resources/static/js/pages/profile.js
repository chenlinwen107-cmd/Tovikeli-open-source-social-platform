/**
 * Profile page (`/profile.html`) — Step 7-E.
 *
 * The signed-in user's own profile. Identity uses only what the backend exposes
 * (`auth.getUser()` -> { id, username, displayName }); avatar/bio stay visual
 * placeholders.
 *
 * Tabs:
 *   作品 / 视频 / 图片 / 评论 -> structured placeholders (no data source).
 *   喜欢 -> the caller's real LIKE history.
 *   收藏 -> the caller's real FAVORITE history.
 *
 * History reuses the existing, caller-only `GET /api/v1/users/{userId}/interactions`
 * (type=LIKE / type=FAVORITE) with hydrated content, plus the shared Content Card,
 * media hydration and the existing paged-list helper. No backend/API is added;
 * `userId` always comes from the auth session, never from the URL.
 */

import { startPage } from '../app.js';
import { auth } from '../auth.js';
import { h, render, emptyState } from '../ui.js';
import { icon } from '../icons.js';
import { contentCard } from '../content-card.js';
import { hydrateMedia } from '../feed-media.js';
import { mountPagedList } from '../paged-list.js';

// Profile page shell: widen the page rail (main + header/footer) on desktop.
document.body.classList.add('is-profile');

/** Interaction history page size (mirrors the controller default of 20). */
const INTERACTION_PAGE_SIZE = 20;

/** First letter of the display name (fallback username) for the initials avatar. */
function initials(user) {
  const source = (user.displayName || user.username || '').trim();
  return source ? source.charAt(0).toUpperCase() : '?';
}

const TABS = [
  { key: 'works', label: '作品' },
  { key: 'likes', label: '喜欢' },
  { key: 'favorites', label: '收藏' },
  { key: 'videos', label: '视频' },
  { key: 'images', label: '图片' },
  { key: 'comments', label: '评论' },
];

/** Tabs with no backend data source (structured placeholders only). */
const PLACEHOLDER = {
  works: '还没有发布作品。',
  videos: '视频内容即将推出。',
  images: '图片内容即将推出。',
  comments: '评论内容将显示在这里。',
};

/**
 * Thin adapter: `HydratedContentResponse` -> the shape the shared Content Card
 * expects. Hydrated content carries no `mediaIds`/timestamps today, so those
 * default to empty; if the backend later adds `mediaIds`, it flows through.
 */
function toCardContent(hydrated) {
  return {
    id: hydrated.id,
    authorId: hydrated.authorId,
    text: typeof hydrated.text === 'string' ? hydrated.text : null,
    mediaIds: Array.isArray(hydrated.mediaIds) ? hydrated.mediaIds : [],
    language: null,
    externalCreatedAt: null,
    createdAt: null,
    repostOfId: null,
    quoteOfId: null,
  };
}

/** One history row -> a Content Card (or nothing when the content is gone). */
function interactionItem(interaction) {
  const hydrated = interaction && interaction.content;
  if (!hydrated) return document.createTextNode('');
  return contentCard(toCardContent(hydrated));
}

/** Builds the profile page body. */
function profileView(user) {
  const cover = h('div', { class: 'profile-cover', 'aria-hidden': 'true' });

  const avatar = h(
    'span',
    { class: 'avatar profile-identity__avatar', 'aria-hidden': 'true' },
    initials(user)
  );

  const identityText = h(
    'div',
    { class: 'profile-identity__text' },
    h('h1', { class: 'profile-identity__name', text: user.displayName || user.username }),
    h('p', { class: 'profile-identity__handle', text: `@${user.username}` }),
    h('p', { class: 'profile-identity__id muted small', text: user.id }),
    h('p', { class: 'profile-identity__bio muted', text: '尚未填写简介。' })
  );

  // Genuinely disabled placeholders: native `disabled`, no handler, no fake nav.
  const actions = h(
    'div',
    { class: 'profile-actions' },
    h('button', {
      type: 'button',
      class: 'btn btn--primary',
      disabled: true,
      title: '即将推出',
      text: '编辑资料',
    }),
    h(
      'button',
      { type: 'button', class: 'btn', disabled: true, title: '即将推出' },
      icon('share'),
      h('span', { text: '分享' })
    )
  );

  const identity = h('header', { class: 'profile-identity' }, avatar, identityText);

  const stats = h(
    'ul',
    { class: 'profile-stats', 'aria-label': '社交统计' },
    h('li', {}, h('strong', { text: '—' }), h('span', { text: '正在关注' })),
    h('li', {}, h('strong', { text: '—' }), h('span', { text: '粉丝' }))
  );

  // Tab panel host + one persistent panel per tab (data tabs mount ONCE).
  const contentHost = h('div', {
    class: 'profile-content',
    role: 'tabpanel',
    id: 'profile-panel',
    'aria-labelledby': `profile-tab-${TABS[0].key}`,
  });

  let active = TABS[0].key;
  const panels = {};

  const buttons = TABS.map((tab) =>
    h(
      'button',
      {
        type: 'button',
        class: 'profile-tab',
        role: 'tab',
        id: `profile-tab-${tab.key}`,
        dataset: { key: tab.key },
        'aria-selected': 'false',
        'aria-controls': 'profile-panel',
        onclick: () => select(tab.key),
      },
      tab.label
    )
  );

  const tabsNav = h(
    'div',
    { class: 'profile-tabs', role: 'tablist', 'aria-label': '内容分类' },
    buttons
  );

  /** 喜欢 / 收藏: real interaction history via the shared paged list. */
  function buildDataPanel(key) {
    const host = h('div', { class: 'profile-panel' });
    mountPagedList(host, {
      path: `/users/${encodeURIComponent(user.id)}/interactions`,
      query: { type: key === 'likes' ? 'LIKE' : 'FAVORITE', limit: INTERACTION_PAGE_SIZE },
      itemRenderer: interactionItem,
      emptyMessage: key === 'likes' ? '还没有喜欢的内容。' : '还没有收藏的内容。',
      listClass: 'profile-list',
      onLoaded: () => hydrateMedia(host),
    });
    return host;
  }

  /** Lazily creates (and then reuses) the panel for a tab — one request per tab. */
  function panelFor(key) {
    if (!panels[key]) {
      panels[key] =
        key === 'likes' || key === 'favorites'
          ? buildDataPanel(key)
          : h('div', { class: 'profile-panel' }, emptyState(PLACEHOLDER[key]));
    }
    return panels[key];
  }

  function select(key) {
    active = key;
    for (const button of buttons) {
      button.setAttribute('aria-selected', button.dataset.key === key ? 'true' : 'false');
    }
    contentHost.setAttribute('aria-labelledby', `profile-tab-${key}`);
    contentHost.replaceChildren(panelFor(key));
  }

  tabsNav.addEventListener('keydown', (event) => {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    const index = TABS.findIndex((tab) => tab.key === active);
    const step = event.key === 'ArrowRight' ? 1 : -1;
    const next = TABS[(index + step + TABS.length) % TABS.length];
    event.preventDefault();
    select(next.key);
    buttons.find((button) => button.dataset.key === next.key)?.focus();
  });

  select(active);

  return h(
    'section',
    { class: 'profile-page', 'aria-label': '个人主页' },
    h(
      'div',
      { class: 'profile-shell' },
      cover,
      identity,
      actions,
      stats,
      tabsNav,
      contentHost
    )
  );
}

startPage({
  title: '个人主页',
  async render(container) {
    if (!auth.isAuthenticated()) {
      window.location.replace('/login.html?next=' + encodeURIComponent('/profile.html'));
      return;
    }

    const user = auth.getUser();
    render(container, profileView(user));
  },
});
