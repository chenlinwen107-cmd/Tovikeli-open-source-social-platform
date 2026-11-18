/**
 * Shared site shell: header, footer, auth area, and page bootstrapping.
 *
 * Every page renders through `startPage()`, so navigation, the signed-in
 * indicator and the "session ended" handling are defined exactly once.
 *
 * The header is rendered from JavaScript rather than duplicated in seven HTML
 * files. This site is entirely API-driven (there is no server-side rendering),
 * so JavaScript is already required for content to appear at all.
 */

import { auth } from './auth.js';
import { onAuthLost, onForbidden } from './api.js';
import { h, render, notice, errorState } from './ui.js';
import { icon } from './icons.js';

/**
 * Global background preference (Settings -> 动态背景).
 *
 * The background markup is static per HTML page (`.site-background`), so this
 * only reads the stored preference and toggles the shared `html.no-background`
 * state before first paint — applying the choice on every page without changing
 * the background architecture. Default: on. Any non-'off' value keeps it on.
 */
function applyBackgroundPreference() {
  let value = null;
  try {
    value = localStorage.getItem('cp.prefs.background');
  } catch {
    value = null;
  }
  document.documentElement.classList.toggle('no-background', value === 'off');
}

applyBackgroundPreference();

/**
 * Global theme preference (Settings -> 主题).
 *
 * Applies the stored choice on every page before first paint by setting
 * `data-theme` on <html>, which the stylesheet already uses
 * (`html[data-theme="dark"]` / `:root:not([data-theme="light"])` +
 * prefers-color-scheme). Only 'light' / 'dark' force a theme; anything else
 * (including 'system' or a missing/invalid value) removes the override so the OS
 * preference applies. No theme tokens are re-implemented here.
 */
function applyThemePreference() {
  let value = null;
  try {
    value = localStorage.getItem('cp.prefs.theme');
  } catch {
    value = null;
  }
  const root = document.documentElement;
  if (value === 'light' || value === 'dark') root.setAttribute('data-theme', value);
  else root.removeAttribute('data-theme');
}

applyThemePreference();

/** Set while a session-ended notice is already on screen, to avoid repeats. */
let sessionNoticeShown = false;

/** Set while a refusal notice is already on screen, to avoid repeats. */
let forbiddenNoticeShown = false;

/** Hosts the account menu contents (login/register, or account + sign-out). */
let authSlot = null;

/** The account popover element, or null before the header is rendered. */
let accountMenu = null;

/** The button that toggles the account popover. */
let accountTrigger = null;

/** Guards the one-time document-level close listeners. */
let accountListenersBound = false;

/**
 * Fills the account menu for the current authentication state.
 *
 * Signed out: the existing login and register pages are offered. Signed in:
 * the display label and the existing sign-out action are shown.
 *
 * The Profile icon is the account entry. Signed in, the menu links to the real
 * self-profile page (`/profile.html`) plus sign-out; signed out it offers
 * login/register. Authentication behaviour is unchanged.
 *
 * @param {{id: string, username: string, displayName: string|null}|null} user
 */
function renderAuthSlot(user) {
  if (!authSlot) return;

  if (!user) {
    render(
      authSlot,
      h('a', { class: 'account-menu__item', href: '/login.html', text: '登录' }),
      h('a', { class: 'account-menu__item', href: '/register.html', text: '注册' })
    );
    return;
  }

  const label = user.displayName || user.username;
  render(
    authSlot,
    h('div', { class: 'account-menu__user', title: label, text: label }),
    h('a', { class: 'account-menu__item', href: '/profile.html', text: '个人主页' }),
    h('button', {
      type: 'button',
      class: 'account-menu__item account-menu__item--danger',
      text: '退出登录',
      onclick: handleLogout,
    })
  );
}

/** Opens or closes the account popover and keeps `aria-expanded` in sync. */
function setAccountMenuOpen(open) {
  if (!accountMenu || !accountTrigger) return;
  accountMenu.hidden = !open;
  accountTrigger.setAttribute('aria-expanded', open ? 'true' : 'false');
}

/** Toggles the account popover. */
function toggleAccountMenu() {
  if (!accountMenu) return;
  setAccountMenuOpen(accountMenu.hidden);
}

/** Closes the account popover. Safe to call while already closed. */
function closeAccountMenu() {
  setAccountMenuOpen(false);
}

/** Installs outside-click and Escape handling exactly once per page. */
function ensureAccountCloseHandlers() {
  if (accountListenersBound) return;
  accountListenersBound = true;

  document.addEventListener('click', (event) => {
    if (!accountMenu || accountMenu.hidden) return;
    const target = event.target;
    // The trigger's own click already toggled the popover here.
    if (accountTrigger && accountTrigger.contains(target)) return;
    if (accountMenu.contains(target)) return;
    closeAccountMenu();
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeAccountMenu();
  });
}

/** Signs out, then returns the user to the home page. */
async function handleLogout() {
  const button = authSlot?.querySelector('button');
  if (button) {
    button.disabled = true;
    button.textContent = '正在退出…';
  }
  await auth.logout();
  window.location.assign('/');
}

/**
 * Builds the header navigation's Search entry.
 *
 * The button is icon-only (UI V1: icon for operation) and the surrounding form
 * keeps the existing behaviour: typing a value and submitting enters the
 * existing search page through the existing `?q=` mechanism. Submitting with an
 * empty value still enters the search page, so pressing the icon alone always
 * navigates there.
 */
function buildSearchForm() {
  const input = h('input', {
    type: 'search',
    name: 'q',
    placeholder: '搜索内容…',
    'aria-label': '搜索内容',
    autocomplete: 'off',
  });

  const onSearchPage = isCurrentPage('/search.html');

  const submit = h(
    'button',
    {
      class: onSearchPage ? 'icon-btn is-active' : 'icon-btn',
      type: 'submit',
      'aria-label': '搜索',
      'aria-current': onSearchPage ? 'page' : false,
    },
    icon('search')
  );

  const form = h(
    'form',
    {
      class: 'site-header__search',
      role: 'search',
      onsubmit: (event) => {
        event.preventDefault();
        const value = input.value.trim();
        window.location.assign(
          value === '' ? '/search.html' : `/search.html?q=${encodeURIComponent(value)}`
        );
      },
    },
    input,
    submit
  );

  return form;
}

/** The current pathname (query and hash excluded). */
function currentPath() {
  return window.location.pathname;
}

/** True when `pathname` is the page currently open. */
function isCurrentPage(pathname) {
  return currentPath() === pathname;
}

/** True when the current location is the site home page. */
function isHomePage() {
  const path = currentPath();
  return path === '/' || path === '/index.html';
}

/**
 * Builds one UI V1 application navigation entry.
 *
 * The same presentation is used by the mobile bottom bar and the desktop
 * header (one element, repositioned by CSS). Every entry carries an
 * `aria-label`; the visible text label is decorative and only shown on desktop.
 *
 * @param {{
 *   iconNode: Node,
 *   label: string,
 *   href?: string,
 *   active?: boolean,
 * }} item
 */
function buildNavItem(item) {
  const content = [item.iconNode, h('span', { class: 'app-nav__label', text: item.label })];

  return h(
    'a',
    {
      class: item.active ? 'app-nav__item is-active' : 'app-nav__item',
      href: item.href,
      'aria-label': item.label,
      'aria-current': item.active ? 'page' : false,
    },
    ...content
  );
}

/**
 * Builds the account entry: the Profile icon button plus a lightweight
 * popover. The popover contents are filled by `renderAuthSlot`, and the same
 * markup works in the mobile bottom bar and the desktop header (the CSS opens
 * it upward on mobile and downward on desktop).
 */
function buildAccountEntry() {
  accountTrigger = h(
    'button',
    {
      type: 'button',
      class: 'app-nav__item account__trigger',
      'aria-expanded': 'false',
      'aria-controls': 'account-menu',
      'aria-label': '账户',
      onclick: toggleAccountMenu,
    },
    icon('profile'),
    h('span', { class: 'app-nav__label', text: '账户' })
  );

  authSlot = h('div', { class: 'account-menu__body' });
  accountMenu = h(
    'div',
    { id: 'account-menu', class: 'account-menu', hidden: true },
    authSlot
  );

  ensureAccountCloseHandlers();

  return h('div', { class: 'app-nav__account' }, accountTrigger, accountMenu);
}

/**
 * Builds the UI V1 application navigation: Home / Notifications / Profile /
 * Settings.
 *
 * Search is deliberately NOT part of this element — it stays the single header
 * search entry, so no second search icon exists. On mobile this is a fixed
 * bottom bar; on desktop it is inline in the header.
 */
function buildAppNav() {
  return h(
    'nav',
    { class: 'app-nav', 'aria-label': '应用导航' },
    buildNavItem({ iconNode: icon('home'), label: '首页', href: '/', active: isHomePage() }),
    buildNavItem({
      iconNode: icon('notifications'),
      label: '通知',
      href: '/notifications.html',
      active: isCurrentPage('/notifications.html'),
    }),
    buildAccountEntry(),
    buildNavItem({
      iconNode: icon('settings'),
      label: '设置',
      href: '/settings.html',
      active: isCurrentPage('/settings.html'),
    })
  );
}

/** Renders the shared header into `#site-header`. */

function renderHeader() {

  const root = document.getElementById('site-header');
  if (!root) return;

  render(
    root,
    h(
      'div',
      { class: 'container site-header__bar' },
      h('a', { class: 'brand', href: '/', text: 'Content Platform' }),
      buildAppNav(),
      buildSearchForm()
    )
  );

  auth.subscribe(renderAuthSlot);
}

/** Renders the shared footer into `#site-footer`. */
function renderFooter() {
  const root = document.getElementById('site-footer');
  if (!root) return;

  render(
    root,
    h(
      'div',
      { class: 'container' },
      h(
        'nav',
        { 'aria-label': '页脚' },
        h('a', { href: '/', text: '首页' }),
        ' · ',
        h('a', { href: '/search.html', text: '搜索' }),
        ' · ',
        h('a', { href: '/login.html', text: '登录' }),
        ' · ',
        h('a', { href: '/register.html', text: '注册' })
      ),
      h('div', {
        class: 'small',
        text: '外部内容均标注其来源平台。',
      })
    )
  );
}

/**
 * Called when a protected request answers 401: the server-side session is gone,
 * so the optimistic local profile is dropped and the user is told plainly.
 */
function handleSessionLost() {
  if (!auth.isAuthenticated()) return;
  auth.clear();

  if (sessionNoticeShown) return;
  sessionNoticeShown = true;

  const page = document.getElementById('page');
  if (!page) return;

  page.prepend(
    notice('登录状态已失效，请重新登录后继续。', 'error')
  );
}

/**
 * Called when a protected request answers 403: the caller IS recognised, but is
 * not permitted to do this (typically because they are not the owner of the
 * resource, or their account is disabled).
 *
 * Local auth state is deliberately NOT cleared here. A refusal is not a lost
 * session, and treating it as one would sign the user out for a mistake that
 * did not end their session. Only the message is added; the 401 path above owns
 * the "you are signed out" story.
 */
function handleForbidden() {
  if (forbiddenNoticeShown) return;

  const page = document.getElementById('page');
  if (!page) return;

  forbiddenNoticeShown = true;
  page.prepend(notice('你没有权限执行该操作。', 'error'));
}

/**
 * Boots a page: restores auth state, renders the shell, then renders the page
 * body into `#page`.
 *
 * @param {{title: string, render: (container: HTMLElement) => (void|Promise<void>)}} definition
 */
export function startPage({ title, render: renderPage }) {
  if (title) {
    document.title = `${title} · Content Platform`;
  }

  // A 401 from any protected request clears stale local state; a 403 explains
  // the refusal without touching that state.
  onAuthLost(handleSessionLost);
  onForbidden(handleForbidden);

  auth.restore();
  renderHeader();
  renderFooter();

  const container = document.getElementById('page');
  if (!container) return;

  container.replaceChildren();

  // Safety net: a page module must never leave the user with a blank screen.
  return Promise.resolve()
    .then(() => renderPage(container))
    .catch((error) => {
      const message = error instanceof Error && error.message
        ? error.message.slice(0, 200)
        : '出错了。';
      render(container, errorState(message, h('a', { class: 'btn', href: '/', text: '返回首页' })));
    });
}
