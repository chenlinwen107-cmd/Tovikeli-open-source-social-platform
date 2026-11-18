import { h, render, loadingState, errorState } from './ui.js';
import { icon } from './icons.js';

let sheet = null;
let surface = null;
let contentHost = null;

/** Teardown returned by renderContentDetail (releases its interaction subscription). */
let contentTeardown = null;

/** Runs and clears the current detail teardown, if any. */
function releaseContent() {
  if (typeof contentTeardown === 'function') {
    try {
      contentTeardown();
    } catch {
      // A broken teardown must never block opening/closing the sheet.
    }
  }
  contentTeardown = null;
}

function ensureSheet() {
  if (sheet) return;

  const closeButton = h(
    'button',
    {
      type: 'button',
      class: 'icon-btn content-sheet__close',
      'aria-label': '关闭内容',
      title: '关闭',
      onclick: closeContentSheet,
    },
    icon('close')
  );

  const header = h(
    'header',
    {
      class: 'content-sheet__header',
    },
    closeButton
  );

  contentHost = h('div', {
    class: 'content-sheet__content',
  });

  surface = h(
    'section',
    {
      class: 'content-sheet__surface',
      role: 'dialog',
      'aria-modal': 'true',
      'aria-label': '内容详情',
    },
    header,
    contentHost
  );

  sheet = h(
    'div',
    {
      class: 'content-sheet',
      'aria-hidden': 'true',
    },
    surface
  );

  document.body.append(sheet);
}

export async function openContentSheet(contentId) {
  const id = typeof contentId === 'string' ? contentId.trim() : '';
  if (!id) return;

  ensureSheet();

  // Release the previous detail before replacing it, so subscriptions from an
  // earlier open do not accumulate.
  releaseContent();

  sheet.classList.add('is-open');
  sheet.setAttribute('aria-hidden', 'false');
  document.body.classList.add('content-sheet-open');

  surface.classList.remove('is-ready');
  render(contentHost, loadingState('正在加载内容…'));

  requestAnimationFrame(() => {
    surface.classList.add('is-ready');
  });

  try {
    const module = await import('./pages/content.js');

    if (typeof module.renderContentDetail !== 'function') {
      throw new Error('内容详情模块不可用。');
    }

    const teardown = await module.renderContentDetail(contentHost, id);
    contentTeardown = typeof teardown === 'function' ? teardown : null;
  } catch (error) {
    render(
      contentHost,
      errorState(
        error?.message || '内容加载失败。',
        h(
          'button',
          {
            type: 'button',
            class: 'btn',
            onclick: () => openContentSheet(id),
          },
          '重试'
        )
      )
    );
  }
}

export function closeContentSheet() {
  if (!sheet) return;

  releaseContent();

  surface.classList.remove('is-ready');
  sheet.classList.remove('is-open');
  sheet.setAttribute('aria-hidden', 'true');
  document.body.classList.remove('content-sheet-open');
}
