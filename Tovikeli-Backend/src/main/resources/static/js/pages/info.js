/**
 * Info page — static platform information. No external API, no fabricated
 * company/legal content; only facts the project can confirm.
 */

import { startPage } from '../app.js';
import { h } from '../ui.js';

/** @param {string} title @param {Node[]} body */
function section(title, body) {
  return h('section', { class: 'section' }, h('h2', { text: title }), ...body);
}

startPage({
  title: '信息',
  async render(container) {
    container.append(
      h(
        'section',
        { class: 'page-shell' },
        h('h1', { text: '信息' }),
        section('平台简介', [
          h('p', {
            text: 'Content Platform 是一个内容发现与创作者生态平台，首页由公开内容 Feed、个人主页与各功能入口组成。',
          }),
        ]),
        section('内容来源与数据说明', [
          h('p', {
            text: '平台展示的公开内容来自已连接的外部来源平台（例如 Mastodon、Misskey 等）。内容的版权与许可归原平台与原作者所有，平台仅做展示与索引。',
          }),
        ]),
        section('隐私与使用', [
          h('p', {
            text: '登录使用服务端会话（HttpOnly Cookie），前端不保存令牌或会话 ID。浏览公开内容无需账号。',
          }),
        ]),
        section('版本信息', [
          h('p', { class: 'muted small', text: '当前阶段：UI V1。' }),
        ])
      )
    );
  },
});
