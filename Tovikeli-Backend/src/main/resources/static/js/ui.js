/**
 * Shared UI helpers: safe DOM building, formatting, and the loading / empty /
 * error state renderers every page reuses.
 *
 * Safety policy: all API- and user-supplied strings are inserted with
 * `textContent` (via `h`), never concatenated into HTML. There is no
 * `innerHTML` usage anywhere in this frontend, so backend text cannot inject
 * markup or script.
 */

import { icon } from './icons.js';

/**
 * Creates an element.
 *
 * Supported props:
 *  - `class`: className
 *  - `text`: textContent (always used for untrusted strings)
 *  - `dataset`: object of data-* attributes
 *  - `on<Event>`: event listener, e.g. `onclick`
 *  - anything else: setAttribute (boolean true becomes a bare attribute)
 *
 * `null` / `undefined` / `false` children and props are skipped so callers can
 * inline optional values.
 *
 * @param {string} tag
 * @param {Record<string, any>} [props]
 * @param {...any} children
 * @returns {HTMLElement}
 */
export function h(tag, props, ...children) {
  const node = document.createElement(tag);

  for (const [key, value] of Object.entries(props || {})) {
    if (value === null || value === undefined || value === false) continue;
    if (key === 'class') {
      node.className = value;
    } else if (key === 'text') {
      node.textContent = String(value);
    } else if (key === 'dataset') {
      Object.assign(node.dataset, value);
    } else if (key.startsWith('on') && typeof value === 'function') {
      node.addEventListener(key.slice(2).toLowerCase(), value);
    } else if (value === true) {
      node.setAttribute(key, '');
    } else {
      node.setAttribute(key, String(value));
    }
  }

  append(node, children);
  return node;
}

/**
 * Appends children, flattening arrays and skipping nullish/false entries.
 * @param {Node} parent
 * @param {any[]} children
 */
function append(parent, children) {
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false || child === '') continue;
    parent.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
}

/**
 * Replaces the contents of a container.
 * @param {Element} container
 * @param {...any} children
 */
export function render(container, ...children) {
  container.replaceChildren();
  append(container, children);
}

/**
 * Returns a URL only when it is a safe absolute http(s) link.
 *
 * External data (author profile URL, avatar, media URLs) reaches this frontend
 * from third-party sources, so `javascript:` / `data:` style URLs are dropped
 * instead of being written into `href`/`src`.
 * @param {unknown} value
 * @returns {string|null}
 */
export function safeUrl(value) {
  if (typeof value !== 'string' || value.trim() === '') return null;
  try {
    const url = new URL(value, window.location.origin);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null;
  } catch {
    return null;
  }
}

/**
 * Restricts a redirect target to a same-site absolute path.
 *
 * Prevents open-redirect style abuse of `?next=` values such as
 * `https://evil.example` or protocol-relative `//evil.example`.
 * @param {string|null} value
 * @returns {string|null}
 */
export function safeInternalPath(value) {
  if (typeof value !== 'string') return null;
  if (!value.startsWith('/')) return null;
  if (value.startsWith('//')) return null;
  return value;
}

/**
 * Formats a unix-millisecond timestamp for display.
 * @param {number|null|undefined} millis
 * @returns {string}
 */
export function formatDate(millis) {
  if (typeof millis !== 'number' || !Number.isFinite(millis)) return '';
  const date = new Date(millis);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/**
 * Human-friendly relative time for recent items, falling back to a date.
 * @param {number|null|undefined} millis
 * @returns {string}
 */
export function formatRelative(millis) {
  if (typeof millis !== 'number' || !Number.isFinite(millis)) return '';
  const diff = Date.now() - millis;
  if (diff < 0) return formatDate(millis);
  const minute = 60_000;
  const hour = 60 * minute;
  const day = 24 * hour;
  if (diff < minute) return '刚刚';
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`;
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`;
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`;
  return formatDate(millis);
}

/** Loading placeholder. */
export function loadingState(message = '正在加载…') {
  return h(
    'div',
    { class: 'state', role: 'status', 'aria-live': 'polite' },
    h('div', { class: 'spinner', 'aria-hidden': 'true' }),
    h('div', { text: message })
  );
}

/**
 * Empty placeholder.
 *
 * The `empty` icon is decorative (`aria-hidden`); the message alone carries the
 * meaning. Only genuine no-content states use this helper — loading and error
 * placeholders are intentionally left without it.
 * @param {string} message
 * @param {Node} [action]
 */
export function emptyState(message, action) {
  return h(
    'div',
    { class: 'state' },
    h('span', { class: 'state__icon', 'aria-hidden': 'true' }, icon('empty', { size: 32 })),
    h('div', { text: message }),
    action ? h('div', { class: 'state__actions' }, action) : null
  );
}

/**
 * Error placeholder. The message is always a safe, generic string produced by
 * the API helper; raw server output is never shown.
 * @param {string} message
 * @param {Node} [action]
 */
export function errorState(message, action) {
  return h(
    'div',
    { class: 'state state--error', role: 'alert' },
    h('div', { class: 'state__title', text: '出错了' }),
    h('div', { text: message }),
    action ? h('div', { class: 'state__actions' }, action) : null
  );
}

/**
 * Inline notice used for form feedback.
 * @param {string} message
 * @param {'error'|'success'|'info'} [tone]
 */
export function notice(message, tone = 'info') {
  const cls = tone === 'error' ? 'notice notice--error' : tone === 'success' ? 'notice notice--success' : 'notice';
  return h('div', { class: cls, role: tone === 'error' ? 'alert' : 'status', text: message });
}

/**
 * Wraps a password input with a show/hide toggle.
 *
 * The toggle is a real `type="button"` inside the same field, so it never
 * submits the form. While the input is masked it offers `eye` ("显示密码");
 * while the value is visible it offers `eye-off` ("隐藏密码"). `aria-pressed`
 * mirrors the visible state. The helper only flips `input.type`; it never reads
 * or stores the value.
 *
 * @param {HTMLInputElement} input an `input[type="password"]`
 * @returns {HTMLElement} the wrapper holding the input and the toggle
 */
export function passwordField(input) {
  const toggle = h('button', {
    type: 'button',
    class: 'icon-btn password-field__toggle',
    'aria-label': '显示密码',
    'aria-pressed': 'false',
  });

  const paint = () => {
    const visible = input.type === 'text';
    render(toggle, icon(visible ? 'eye-off' : 'eye'));
    toggle.setAttribute('aria-label', visible ? '隐藏密码' : '显示密码');
    toggle.setAttribute('aria-pressed', visible ? 'true' : 'false');
  };

  toggle.addEventListener('click', () => {
    input.type = input.type === 'password' ? 'text' : 'password';
    paint();
  });

  paint();
  return h('div', { class: 'password-field' }, input, toggle);
}

/** Small badge, e.g. a content status. */
export function badge(text) {
  return h('span', { class: 'badge', text });
}

/**
 * A navigable link that looks like a block-level card container.
 * @param {string} href
 * @param {...any} children
 */
export function cardLink(href, ...children) {
  return h('a', { class: 'content-link', href }, ...children);
}

/**
 * Renders the author row (avatar + names) shared by the content and author
 * pages.
 * @param {{id: string, name?: string, username?: string, displayName?: string|null, avatar?: string|null}} author
 */
export function authorRow(author) {
  const avatarUrl = safeUrl(author.avatar);
  const handle = author.username ? `@${author.username}` : null;
  const primary = author.displayName || author.name || author.username || '未知作者';

  return h(
    'div',
    { class: 'author-row' },
    avatarUrl
      ? h('img', { class: 'avatar', src: avatarUrl, alt: '', loading: 'lazy', width: '44', height: '44' })
      : h('div', { class: 'avatar', 'aria-hidden': 'true' }),
    h(
      'div',
      {},
      h('a', { class: 'author-row__name', href: `/author.html?id=${encodeURIComponent(author.id)}`, text: primary }),
      handle ? h('div', { class: 'author-row__sub', text: handle }) : null
    )
  );
}

/**
 * Reads a query parameter from the current location.
 * @param {string} name
 * @returns {string|null}
 */
export function queryParam(name) {
  return new URLSearchParams(window.location.search).get(name);
}
