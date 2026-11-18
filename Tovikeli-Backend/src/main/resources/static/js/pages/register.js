/**
 * Registration page: `POST /api/v1/users` followed by `POST /api/v1/auth/login`.
 *
 * Step 3A — immersive auth space: a single centered Glass panel over the shared
 * background video + Acrylic Veil, with a Login/Register switcher. Switching is
 * real navigation between the two existing pages (`/login.html` ↔
 * `/register.html`) — no same-document router.
 *
 * Registration alone does not create a session on the backend, so a successful
 * registration is followed by a sign-in with the same credentials. If that
 * sign-in fails, the user is sent to the login page rather than left in a
 * half-registered state. The password lives only in the form field for the
 * duration of the request; it is never written to storage.
 */

import { startPage } from '../app.js';
import { auth, ApiError } from '../auth.js';
import { h, render, notice, passwordField, safeInternalPath, queryParam } from '../ui.js';

// Immersive auth shell: hide the dashboard chrome before first paint.
document.body.classList.add('auth-page');

/** Maps a registration failure to an accurate, non-technical message. */
function registerErrorMessage(error) {
  if (error instanceof ApiError) {
    if (error.status === 409) return '该用户名已被占用，请换一个。';
    if (error.status === 400) return '请输入用户名和密码。';
  }
  return error?.message || '注册失败，请重试。';
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
  title: '注册',
  async render(container) {
    // Preserved for the Login tab so switching keeps the same post-auth target.
    const next = safeInternalPath(queryParam('next'));

    if (auth.isAuthenticated()) {
      const user = auth.getUser();
      container.append(
        authShell({
          active: null,
          title: '创建账号',
          children: [
            notice(`你已登录为 ${user.displayName || user.username}。`, 'success'),
            h('a', { class: 'btn btn--primary btn--block', href: '/', text: '继续' }),
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
      'aria-describedby': 'username-hint',
    });

    const displayName = h('input', {
      type: 'text',
      id: 'displayName',
      name: 'displayName',
      autocomplete: 'nickname',
      'aria-describedby': 'display-name-hint',
    });

    const password = h('input', {
      type: 'password',
      id: 'password',
      name: 'password',
      autocomplete: 'new-password',
      required: true,
      'aria-describedby': 'password-hint',
    });

    const submit = h('button', { class: 'btn btn--primary btn--block', type: 'submit', text: '创建账号' });

    const form = h(
      'form',
      {
        novalidate: true,
        onsubmit: async (event) => {
          event.preventDefault();
          render(feedback);

          const usernameValue = username.value.trim();
          const passwordValue = password.value;
          const displayNameValue = displayName.value.trim();

          if (usernameValue === '' || passwordValue === '') {
            render(feedback, notice('请输入用户名和密码。', 'error'));
            return;
          }

          submit.disabled = true;
          submit.textContent = '正在创建账号…';

          try {
            const result = await auth.register({
              username: usernameValue,
              password: passwordValue,
              displayName: displayNameValue || null,
            });

            if (result.signedIn) {
              window.location.assign('/');
              return;
            }

            // Account created but the session could not be started.
            window.location.assign('/login.html');
          } catch (error) {
            password.value = '';
            render(feedback, notice(registerErrorMessage(error), 'error'));
            submit.disabled = false;
            submit.textContent = '创建账号';
          }
        },
      },
      h(
        'div',
        { class: 'field' },
        h('label', { for: 'username', text: '用户名' }),
        username,
        h('div', { class: 'field__hint', id: 'username-hint', text: '用于登录，必须唯一。' })
      ),
      h(
        'div',
        { class: 'field' },
        h('label', { for: 'displayName', text: '显示名称（可选）' }),
        displayName,
        h('div', { class: 'field__hint', id: 'display-name-hint', text: '显示在你的资料中，可以不唯一。' })
      ),
      h(
        'div',
        { class: 'field' },
        h('label', { for: 'password', text: '密码' }),
        passwordField(password),
        h('div', { class: 'field__hint', id: 'password-hint', text: '请勿使用在其他网站用过的密码。' })
      ),
      submit
    );

    container.append(
      authShell({ active: 'register', title: '创建账号', next, children: [feedback, form] })
    );
  },
});
