/**
 * Settings page — productized UI + local interface preferences.
 *
 * Preferences are LOCAL ONLY (localStorage); there is no settings API. The
 * Account block shows the real signed-in identity, and any control without a
 * backend is genuinely disabled (native `disabled`, no handler, no persistence).
 *
 * Preferences (keys are namespaced and stable):
 *   cp.prefs.theme           'system' | 'light' | 'dark'   (default 'system')
 *   cp.prefs.background      'on' | 'off'                  (default 'on')
 *   cp.prefs.reducedMotion   'on' | 'off' | absent          (default: follow OS)
 * They are applied by toggling `data-theme` / classes on <html>, which the
 * stylesheet uses. Applying them on every page would need the shared bootstrap
 * (app.js), which is out of scope here.
 */

import { startPage } from '../app.js';
import { auth } from '../auth.js';
import { h } from '../ui.js';

document.body.classList.add('is-settings');

const PREF_KEYS = {
  theme: 'cp.prefs.theme',
  background: 'cp.prefs.background',
  reducedMotion: 'cp.prefs.reducedMotion',
};

function readPref(key) {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writePref(key, value) {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Storage is optional; the in-page effect still applies.
  }
}

function systemPrefersReducedMotion() {
  return (
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

function applyTheme(value) {
  const root = document.documentElement;
  if (value === 'light' || value === 'dark') root.setAttribute('data-theme', value);
  else root.removeAttribute('data-theme');
}

function applyBackground(value) {
  document.documentElement.classList.toggle('no-background', value === 'off');
}

/** @returns {boolean} the effective reduced-motion state. */
function applyReducedMotion(value) {
  const on = value === 'on' ? true : value === 'off' ? false : systemPrefersReducedMotion();
  document.documentElement.classList.toggle('reduce-motion', on);
  return on;
}

// Restore + apply immediately on load (default = 跟随系统 / on / 跟随系统).
const themePref = readPref(PREF_KEYS.theme) || 'system';
const backgroundPref = readPref(PREF_KEYS.background) || 'on';
const reducedPref = readPref(PREF_KEYS.reducedMotion);
applyTheme(themePref);
applyBackground(backgroundPref);
const reducedOn = applyReducedMotion(reducedPref);

/** One "label / description / control" row. */
function row(label, description, control) {
  return h(
    'div',
    { class: 'settings-row' },
    h(
      'div',
      { class: 'settings-row__text' },
      h('div', { class: 'settings-row__label', text: label }),
      description ? h('div', { class: 'settings-row__desc', text: description }) : null
    ),
    h('div', { class: 'settings-row__control' }, control ?? null)
  );
}

function section(title, rows) {
  return h(
    'section',
    { class: 'settings-section' },
    h('h2', { class: 'settings-section__title', text: title }),
    ...rows
  );
}

function value(text) {
  return h('span', { class: 'settings-value', text });
}

function status(text) {
  return h('span', { class: 'settings-status', text });
}

/** Light muted note at the end of a section (wrap-friendly). */
function note(text) {
  return h('p', { class: 'settings-note', text });
}

/** Genuinely disabled switch (no backend setting exists for it). */
function disabledSwitch(label) {
  return h('button', {
    type: 'button',
    class: 'settings-switch',
    role: 'switch',
    'aria-checked': 'false',
    'aria-label': label,
    disabled: true,
  });
}

/** Interactive local switch. */
function prefSwitch(id, label, checked, onToggle) {
  const button = h('button', {
    type: 'button',
    class: 'settings-switch',
    role: 'switch',
    id,
    'aria-label': label,
    'aria-checked': checked ? 'true' : 'false',
    onclick: () => {
      const next = button.getAttribute('aria-checked') !== 'true';
      button.setAttribute('aria-checked', next ? 'true' : 'false');
      onToggle(next);
    },
  });
  return button;
}

const themeSelect = h(
  'select',
  {
    class: 'settings-select',
    id: 'pref-theme',
    'aria-label': '主题',
    onchange: (event) => {
      writePref(PREF_KEYS.theme, event.target.value);
      applyTheme(event.target.value);
    },
  },
  h('option', { value: 'system', text: '跟随系统' }),
  h('option', { value: 'light', text: '浅色' }),
  h('option', { value: 'dark', text: '深色' })
);
themeSelect.value = themePref;

const backgroundSwitch = prefSwitch(
  'pref-background',
  '动态背景',
  backgroundPref !== 'off',
  (on) => {
    const next = on ? 'on' : 'off';
    writePref(PREF_KEYS.background, next);
    applyBackground(next);
  }
);

const reducedSwitch = prefSwitch('pref-reduced-motion', '减少动态效果', reducedOn, (on) => {
  const next = on ? 'on' : 'off';
  writePref(PREF_KEYS.reducedMotion, next);
  applyReducedMotion(next);
});

/** Real sign-out: reuses `auth.logout()` (same behaviour as the account menu). */
function logoutButton() {
  const button = h('button', {
    type: 'button',
    class: 'btn',
    text: '退出登录',
    onclick: async () => {
      if (button.disabled) return; // guard against double clicks
      button.disabled = true;
      button.textContent = '正在退出…';
      await auth.logout();
      window.location.assign('/');
    },
  });
  return button;
}

startPage({
  title: '设置',
  async render(container) {
    const user = auth.isAuthenticated() ? auth.getUser() : null;

    const accountRows = user
      ? [
          row('用户名', null, value(`@${user.username}`)),
          row('显示名称', null, value(user.displayName || '未设置')),
          row('用户 ID', null, value(user.id)),
          row('退出登录', '结束当前会话并返回首页。', logoutButton()),
        ]
      : [
          row(
            '未登录',
            '登录后管理你的账户。',
            h('a', { class: 'btn btn--primary', href: '/login.html', text: '登录' })
          ),
        ];

    container.append(
      h(
        'section',
        { class: 'page-shell' },
        h('h1', { text: '设置' }),

        section('账户', accountRows),

        section('界面', [
          row('主题', '默认跟随系统，可手动选择浅色或深色。', themeSelect),
          row('动态背景', '关闭后隐藏背景视频，仅保留页面主体。', backgroundSwitch),
          row('减少动态效果', '减少界面动画与过渡效果。', reducedSwitch),
        ]),

        section('通知', [
          row('互动通知', '当前版本暂未提供点赞、收藏等互动通知推送。', disabledSwitch('互动通知')),
          row('评论通知', '当前版本暂未提供新评论通知推送。', disabledSwitch('评论通知')),
          row('关注通知', '当前版本暂未开放关注关系与关注通知功能。', disabledSwitch('关注通知')),
          note('部分通知能力将在后续版本开放。'),
        ]),

        section('隐私', [
          row(
            '账户信息',
            'Content Platform 当前使用你的账号用户名、显示名称和用户 ID 识别平台账户。公开页面仅展示产品当前允许公开的账户信息。'
          ),
          row(
            '内容可见性',
            'Content Platform 当前主要展示和索引来源平台提供的内容。内容的原始可见性与来源平台规则有关；当前版本不会将来源平台的公开内容规则重新定义为独立的 Content Platform 隐私权限。'
          ),
          row(
            '数据处理',
            '当前版本不会在 Settings 中提供独立的数据导出、删除或隐私级别配置入口。相关能力将在对应产品功能和正式隐私规则完善后提供。'
          ),
          note('详细的数据处理规则应以正式《隐私政策》为准。'),
        ]),

        section('关于', [
          row('应用', null, value('Content Platform')),
          row('版本', null, value('UI V1')),
          row(
            '产品说明',
            'Content Platform 是面向多来源内容浏览与聚合的内容平台界面。当前版本重点提供内容浏览、账户、互动记录及基础产品界面能力。'
          ),
          note('部分创作、视频、通知、社交关系等能力仍处于逐步开放阶段。'),
        ])
      )
    );
  },
});
