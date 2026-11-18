/**
 * Home Dashboard — UI V1 Spatial Glass (Step 1: structural shell).
 *
 * Lifecycle (important): the Dashboard and the existing Feed are BOTH mounted
 * once into persistent view containers inside `#page`, and switching only toggles
 * `hidden`. Neither view is destroyed or re-created, the Feed is not re-fetched,
 * and — critically — the background `<video>` (a sibling of `#page` in
 * index.html) is never touched, so playback position is preserved.
 *
 * Navigation is explicit: the hero / back controls `preventDefault()` and use
 * `history.pushState`, so switching never triggers a document navigation.
 *
 * Scope:
 *   Recommendation -> existing Feed        (`#feed`)
 *   Profile        -> existing auth/account (login / register / logout)
 *   Video          -> disabled placeholder  (future immersive video space)
 *   Info / Bookmarks / Settings -> disabled placeholders (no broken links)
 *   Search stays in the header form / footer (-> `/search.html`), NOT a card.
 *
 * No pointer tilt, no blur, no new glass system: cards reuse Glass Surface v1.
 */

import { startPage } from '../app.js';
import { auth } from '../auth.js';
import { h, render } from '../ui.js';
import { icon } from '../icons.js';

/**
 * Applied ONCE for this page and never toggled: the home rail (dashboard and
 * feed share one constant page width), so switching views can never change a
 * parent width — that was the cause of the Dashboard shrinking/growing at the
 * switch moment. The Feed view gets its own fixed content width via CSS.
 */
document.body.classList.add('home-shell');

/** Hash that selects the Feed view; anything else shows the Dashboard. */
const FEED_HASH = '#feed';

/** The `#page` element, captured once so handlers can toggle views. */
let pageContainer = null;

/** Persistent view containers (created once). */
let dashboardView = null;
let feedView = null;
let feedMounted = false;

/** @returns {'dashboard'|'feed'} the view requested by the current hash. */
function currentView() {
  return window.location.hash === FEED_HASH ? 'feed' : 'dashboard';
}

/** Generic dashboard card (link when `href` is set, disabled placeholder otherwise). */
function dashCard({ className, iconName, eyebrow, title, subtitle, cta, href, disabled, onActivate }) {
  const content = [
    iconName ? h('span', { class: 'dash-card__icon', 'aria-hidden': 'true' }, icon(iconName)) : null,
    eyebrow ? h('span', { class: 'dash-card__eyebrow', text: eyebrow }) : null,
    h('h2', { class: 'dash-card__title', text: title }),
    subtitle ? h('p', { class: 'dash-card__subtitle', text: subtitle }) : null,
    cta ? h('span', { class: 'dash-card__cta', text: cta }) : null,
  ];

  if (disabled || !href) {
    return h(
      'div',
      { class: `dash-card ${className} dash-card--disabled`, 'aria-disabled': 'true' },
      ...content
    );
  }

  // In-app view switches intercept the click and never navigate the document.
  const props = { class: `dash-card ${className}`, href };
  if (onActivate) {
    props.onclick = (event) => {
      event.preventDefault();
      onActivate();
    };
  }
  return h('a', props, ...content);
}

/** Profile card: reuses the existing auth profile, never a fabricated page. */
function profileCard() {
  const user = auth.getUser();

  const iconEl = h('span', { class: 'dash-card__icon', 'aria-hidden': 'true' }, icon('profile'));
  const eyebrow = h('span', { class: 'dash-card__eyebrow', text: 'YOUR SPACE' });

  // Signed out: the WHOLE card is the single entry to the auth space (no inner
  // login/register links — they would duplicate and dilute the entry).
  if (!user) {
    return h(
      'a',
      { class: 'dash-card dash-card--profile', href: '/login.html' },
      iconEl,
      eyebrow,
      h('h2', { class: 'dash-card__title', text: '个人主页' }),
      h('p', { class: 'dash-card__subtitle', text: '登录后查看你的内容与互动' })
    );
  }

  // Signed in: the WHOLE card navigates to the real self-profile page. The
  // sign-out action lives in the account menu (no nested interactive element).
  return h(
    'a',
    { class: 'dash-card dash-card--profile', href: '/profile.html' },
    iconEl,
    eyebrow,
    h('h2', { class: 'dash-card__title', text: user.displayName || user.username }),
    h('p', { class: 'dash-card__subtitle', text: `@${user.username}` })
  );
}

/**
 * Recommendation Hero — the dashboard's main visual entry to the Feed.
 * A dedicated builder so the other cards (dashCard) stay untouched. Keeps the
 * native `<a href="#feed">` + preventDefault/`go('feed')` lifecycle.
 */
function heroCard() {
  const arrow = h('span', { class: 'dash-hero__arrow', 'aria-hidden': 'true' }, icon('back'));

  return h(
    'a',
    {
      class: 'dash-card dash-card--hero',
      href: FEED_HASH,
      'aria-label': '推荐：开始探索',
      onclick: (event) => {
        event.preventDefault();
        go('feed');
      },
    },
    h(
      'span',
      { class: 'dash-hero__content' },
      h('span', { class: 'dash-card__eyebrow dash-hero__eyebrow', text: 'EXPLORE' }),
      h('h2', { class: 'dash-card__title dash-hero__title', text: '推荐' }),
      h('p', {
        class: 'dash-card__subtitle dash-hero__subtitle',
        text: '发现来自不同来源的最新内容',
      }),
      h(
        'span',
        { class: 'dash-card__cta dash-hero__cta' },
        h('span', { text: '开始探索' }),
        arrow
      )
    ),
    h(
      'span',
      { class: 'dash-hero__decor', 'aria-hidden': 'true' },
      h('span', { class: 'dash-hero__ring' }),
      h('span', { class: 'dash-hero__dot' })
    )
  );
}

/**
 * Video Space card — a disabled placeholder for the future immersive vertical
 * video experience. Non-interactive (`<div>`, aria-disabled), no href, no route,
 * no player, no media request; the decorative frame + play glyph are CSS-only.
 */
function videoCard() {
  return h(
    'a',
    { class: 'dash-card dash-card--video', href: '/video.html' },
    h(
      'span',
      { class: 'dash-card__frame', 'aria-hidden': 'true' },
      h('span', { class: 'dash-card__screen' }),
      h('span', { class: 'dash-card__play' })
    ),
    h(
      'span',
      { class: 'dash-card__text' },
      h('span', { class: 'dash-card__eyebrow', text: 'WATCH' }),
      h('h2', { class: 'dash-card__title', text: '视频' }),
      h('p', { class: 'dash-card__subtitle', text: '沉浸式浏览精彩视频' }),
      h('span', { class: 'dash-card__status', text: '即将推出' })
    )
  );
}

/** Builds the dashboard shell. */
function buildDashboard() {
  const hero = heroCard();

  const utilities = h(
    'div',
    { class: 'dashboard__utilities' },
    videoCard(),
    dashCard({
      className: 'dash-card--info',
      iconName: 'notifications',
      title: '信息',
      subtitle: '平台与数据说明。',
      href: '/info.html',
    }),
    dashCard({
      className: 'dash-card--create',
      iconName: 'share',
      title: '发布内容',
      subtitle: '分享你的内容。',
      href: '/create.html',
    }),
    dashCard({
      className: 'dash-card--settings',
      iconName: 'settings',
      title: '设置',
      subtitle: '账户与偏好。',
      href: '/settings.html',
    })
  );

  return h(
    'section',
    { class: 'dashboard', 'aria-label': '功能入口' },
    hero,
    profileCard(),
    utilities
  );
}

/** Creates the two persistent view containers exactly once. */
function ensureViews(container) {
  if (dashboardView) return;

  dashboardView = h('div', { class: 'dashboard-view' });
  feedView = h('div', { class: 'dashboard-view feed-view', hidden: true });
  render(container, dashboardView, feedView);
  render(dashboardView, buildDashboard());

  // One-time entrance state: after every card has finished rising, mark the
  // dashboard so returning from the Feed never replays the animation. This is
  // event-driven only — no loop, no timer, no requestAnimationFrame.
  const dashboard = dashboardView.querySelector('.dashboard');
  const cards = dashboard ? dashboard.querySelectorAll('.dash-card') : [];
  if (dashboard && cards.length > 0) {
    let remaining = cards.length;
    dashboardView.addEventListener('animationend', (event) => {
      if (event.animationName !== 'dash-enter') return;
      remaining -= 1;
      if (remaining <= 0) dashboard.classList.add('is-entered');
    });
  }
}

/**
 * Shows one view by toggling `hidden`. The Feed is rendered lazily ONCE and
 * then kept mounted, so its loaded pages/scroll survive later switches.
 */
async function showView(container, view) {
  ensureViews(container);

  if (view === 'feed' && !feedMounted) {
    feedMounted = true;
    feedView.append(
      h('a', {
        class: 'dash-back',
        href: '#',
        text: '← 返回 Dashboard',
        onclick: (event) => {
          event.preventDefault();
          go('dashboard');
        },
      })
    );
    const { renderHomeFeed } = await import('./home.js');
    await renderHomeFeed(feedView);
  }

  dashboardView.hidden = view === 'feed';
  feedView.hidden = view !== 'feed';
}

/** Switches views explicitly: no anchor navigation, no document reload. */
function go(view) {
  const wantHash = view === 'feed' ? FEED_HASH : '';
  if (window.location.hash !== wantHash) {
    history.pushState({ view }, '', wantHash || window.location.pathname + window.location.search);
  }
  showView(pageContainer, view);
}

startPage({
  title: '首页',
  async render(container) {
    pageContainer = container;
    await showView(container, currentView());
    window.addEventListener('popstate', () => showView(pageContainer, currentView()));
    window.addEventListener('hashchange', () => showView(pageContainer, currentView()));
  },
});
