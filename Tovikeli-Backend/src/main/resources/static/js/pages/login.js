/**
 * Login page: `POST /api/v1/auth/login`.
 *
 * Step 3A — immersive auth space: a single centered Glass panel over the shared
 * background video + Acrylic Veil, with a Login/Register switcher. Switching is
 * real navigation between the two existing pages (`/login.html` ↔
 * `/register.html`) — no same-document router.
 *
 * Authentication logic is unchanged: the backend sets an HttpOnly session
 * cookie; this page never sees or stores that cookie, a session id or a token —
 * only the returned public profile, which is cached for display by auth.js.
 */

import { startPage } from '../app.js';
import { auth, ApiError } from '../auth.js';
import { h, render, notice, passwordField, safeInternalPath, queryParam } from '../ui.js';

// Immersive auth shell: hide the dashboard chrome before first paint.
document.body.classList.add('auth-page');

/**
 * Maps a login failure to an accurate, non-technical message.
 *
 * The backend answers 401 for invalid credentials (including blank ones) and
 * 403 for a disabled account; empty fields are caught in the form before any
 * request is sent.
 */
function loginErrorMessage(error) {
  if (error instanceof ApiError) {
    if (error.status === 401) return '用户名或密码错误。';
    if (error.status === 403) return '该账号已被禁用。';
  }
  return error?.message || '登录失败，请重试。';
}

/** Login/Register switcher — real links, current page marked via aria-current. */
function authTabs(active, next) {
  const withNext = (base) => (next ? `${base}?next=${encodeURIComponent(next)}` : base);
  const tab = (label, href, key) =>
    h('a', { class: 'auth-tab', href, 'aria-current': active === key ? 'page' : false }, label);

  return h(
    'nav',
    { class: 'auth-tabs', 'aria-label': '登录与注册' },
    tab('登录', withNext('/login.html'), 'login'),
    tab('注册', withNext('/register.html'), 'register')
  );
}

/** The centered Glass panel: brand + tabs + accessible title + content. */
function authShell({ active, title, next, children }) {
  return h(
    'section',
    { class: 'auth-panel', 'aria-labelledby': 'auth-title' },
    h('p', { class: 'auth-panel__brand', text: 'Content Platform' }),
    active ? authTabs(active, next) : null,
    h('h1', { class: 'visually-hidden', id: 'auth-title', text: title }),
    ...children
  );
}

startPage({
  title: '登录',
  async render(container) {
    // Where to go after a successful sign-in, restricted to a same-site path.
    const next = safeInternalPath(queryParam('next')) || '/';

    if (auth.isAuthenticated()) {
      const user = auth.getUser();
      container.append(
        authShell({
          active: null,
          title: '登录',
          children: [
            notice(`你已登录为 ${user.displayName || user.username}。`, 'success'),
            h('a', { class: 'btn btn--primary btn--block', href: next, text: '继续' }),
          ],
        })
      );
      return;
    }

    const feedback = h('div', {});

    const username = h('input', {
      type: 'text',
      id: 'username',
      name: 'username',
      autocomplete: 'username',
      required: true,
      autocapitalize: 'none',
      spellcheck: 'false',
    });

    const password = h('input', {
      type: 'password',
      id: 'password',
      name: 'password',
      autocomplete: 'current-password',
      required: true,
    });

    const submit = h('button', { class: 'btn btn--primary btn--block', type: 'submit', text: '登录' });

    const form = h(
      'form',
      {
        novalidate: true,
        onsubmit: async (event) => {
          event.preventDefault();
          render(feedback);

          const usernameValue = username.value.trim();
          const passwordValue = password.value;

          if (usernameValue === '' || passwordValue === '') {
            render(feedback, notice('请输入用户名和密码。', 'error'));
            return;
          }

          submit.disabled = true;
          submit.textContent = '正在登录…';

          try {
            await auth.login(usernameValue, passwordValue);
            window.location.assign(next);
          } catch (error) {
            // The typed password is not echoed back into the page.
            password.value = '';
            render(feedback, notice(loginErrorMessage(error), 'error'));
            submit.disabled = false;
            submit.textContent = '登录';
            username.focus();
          }
        },
      },
      h('div', { class: 'field' }, h('label', { for: 'username', text: '用户名' }), username),
      h('div', { class: 'field' }, h('label', { for: 'password', text: '密码' }), passwordField(password)),
      submit
    );

    container.append(
      authShell({ active: 'login', title: '登录', next, children: [feedback, form] })
    );
  },
});
