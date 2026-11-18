/**
 * Feed media hydration.
 *
 * List endpoints (ContentResponse) expose only opaque `mediaIds`; the image
 * URLs live on MediaResponse. This module resolves those ids for a rendered
 * list in ONE batched request per page of media ids
 * (`GET /api/v1/media/batch?ids=...`, max 50 ids per request) and fills the
 * `.card__media` placeholders produced by content-card.js.
 *
 * Guarantees (unchanged):
 *  - never one request per card (ids are collected across the whole list and
 *    de-duplicated before requesting);
 *  - module-level cache: a media id resolved once on a page is never fetched
 *    again;
 *  - best-effort: any failure leaves the card's text/badge/author intact;
 *  - only `previewUrl` (fallback `thumbnailUrl`) is used. `originalUrl` is never
 *    used here, and nothing is proxied or downloaded by our server.
 *
 * Presentation (UI V1, Step 6-C):
 *   1 image  -> `.media-single` (native ratio, full width, no crop)
 *   2+ images -> `.media-rail`   (ONE horizontal track, stable height, native
 *                                  per-image ratios, no crop)
 * The gallery height is fixed by CSS and never depends on image dimensions, so
 * swiping never changes the surrounding layout height.
 */

import { api } from './api.js';
import { h, render, safeUrl } from './ui.js';
import { icon } from './icons.js';

/** Media ids already resolved during this page load: mediaId -> MediaResponse. */
const cache = new Map();

/** Max ids per /media/batch request (mirrors MediaController.MAX_BATCH_SIZE). */
const BATCH_SIZE = 50;

/**
 * Hydrates every not-yet-filled `.card__media` placeholder inside `root`.
 *
 * @param {ParentNode} root container that holds the rendered content cards
 * @returns {Promise<void>}
 */
export async function hydrateMedia(root) {
  if (!root || typeof root.querySelectorAll !== 'function') return;

  const hosts = Array.from(
    root.querySelectorAll('.card__media[data-media-ids]:not([data-media-hydrated])')
  );
  if (hosts.length === 0) return;

  // Reserve the gallery shape before the media API resolves. The rail height is
  // fixed, so only item widths depend on the images — no vertical jump.
  for (const host of hosts) {
    render(host, buildLoading(idsOf(host).length));
  }

  // Collect the unique ids that still need a lookup, across ALL cards of this
  // list, so the whole page is resolved with a small number of requests.
  const wanted = [];
  const seen = new Set();
  for (const host of hosts) {
    for (const id of idsOf(host)) {
      if (cache.has(id) || seen.has(id)) continue;
      seen.add(id);
      wanted.push(id);
    }
  }

  if (wanted.length > 0) {
    try {
      for (let i = 0; i < wanted.length; i += BATCH_SIZE) {
        const chunk = wanted.slice(i, i + BATCH_SIZE);
        const media = await api.get('/media/batch', { query: { ids: chunk.join(',') } });
        if (Array.isArray(media)) {
          for (const item of media) {
            if (item && typeof item.id === 'string') {
              cache.set(item.id, item);
            }
          }
        }
      }
    } catch {
      // Keep the already-reserved loading galleries in place on lookup failure.
    }
  }

  for (const host of hosts) {
    const items = idsOf(host)
      .map((id) => cache.get(id))
      .filter((item) => item != null);
    render(host, buildMediaGallery(items));
    host.dataset.mediaHydrated = '1';
  }
}

/** Reads the media ids carried by a placeholder (`data-media-ids`). */
function idsOf(host) {
  const raw = host.dataset ? host.dataset.mediaIds : '';
  if (!raw) return [];
  return raw
    .split(',')
    .map((value) => value.trim())
    .filter((value) => value !== '');
}

/**
 * Builds the media presentation for one post:
 *   1 image  -> a single native-ratio tile (`.media-single`)
 *   2+ images -> ONE horizontal rail (`.media-rail` > `.media-rail__track`)
 *
 * Display-only: the source image is never modified and is not cropped. Also
 * reused by the Content Detail renderer so there is a single implementation.
 *
 * @param {object[]} items MediaResponse items, order preserved
 * @returns {HTMLElement|null} null when nothing is renderable
 */
export function buildMediaGallery(items) {
  const tiles = items.map((item) => mediaTile(item)).filter((tile) => tile !== null);
  if (tiles.length === 0) return null;

  if (tiles.length === 1) {
    tiles[0].classList.add('media-single');
    return tiles[0];
  }

  for (const tile of tiles) tile.classList.add('media-rail__item');
  return h('div', { class: 'media-rail' }, h('div', { class: 'media-rail__track' }, tiles));
}

/** Loading placeholder matching the final gallery shape (reserves rail height). */
function buildLoading(count) {
  if (count <= 1) {
    return h('div', {
      class: 'media-item media-single media-item--loading',
      'aria-hidden': 'true',
    });
  }
  const items = Array.from({ length: count }, () =>
    h('div', {
      class: 'media-item media-rail__item media-item--loading',
      'aria-hidden': 'true',
    })
  );
  return h('div', { class: 'media-rail' }, h('div', { class: 'media-rail__track' }, items));
}

/** Loading / retry glyph: the existing refresh icon, optionally spinning. */
function refreshIcon(spin) {
  return h(
    'span',
    { class: spin ? 'media-status__icon media-status__icon--spin' : 'media-status__icon' },
    icon('refresh')
  );
}

/**
 * One image tile. The caller adds `.media-single` or `.media-rail__item`.
 *
 * @param {object} item a MediaResponse
 * @returns {HTMLElement|null} null when the item must be skipped
 */
function mediaTile(item) {
  const source = safeUrl(item.previewUrl) || safeUrl(item.thumbnailUrl);
  const status = typeof item.status === 'string' ? item.status : '';
  const unavailable = status !== '' && status !== 'ACTIVE';

  // A media item with no renderable URL and an ACTIVE status is skipped.
  if (!source && !unavailable) return null;

  const statusLayer = h('div', { class: 'media-status' });
  const tile = h('div', { class: 'media-item' }, statusLayer);

  if (unavailable) {
    tile.dataset.state = 'unavailable';
    render(
      statusLayer,
      h('span', { class: 'media-status__icon' }, icon('image')),
      h('span', { class: 'visually-hidden', text: '媒体不可用' })
    );
    return tile;
  }

  /** Starts (or restarts) this tile's image load. */
  function startLoad() {
    tile.dataset.state = 'loading';
    render(statusLayer, refreshIcon(true));
    tile.querySelector('img')?.remove();

    const img = h('img', {
      class: 'media-image',
      alt: item.altText || '',
      loading: 'lazy',
      decoding: 'async',
      // Open the shared lightbox (native-ratio, contain) on a plain left click.
      onclick: (event) => {
        if (
          event.button !== 0 ||
          event.metaKey ||
          event.ctrlKey ||
          event.shiftKey ||
          event.altKey
        ) {
          return;
        }
        if (!img.complete || img.naturalWidth === 0) return;
        const src = img.currentSrc || img.src;
        if (!src) return;
        event.preventDefault();
        openLightbox(src, img.alt || '');
      },
    });

    img.addEventListener('load', () => {
      tile.dataset.state = 'loaded';
      render(statusLayer);
    });

    img.addEventListener('error', () => {
      // Keep the reserved space; show a static, keyboard-reachable retry.
      tile.dataset.state = 'error';
      img.remove();
      render(
        statusLayer,
        h(
          'button',
          {
            type: 'button',
            class: 'media-status__retry',
            'aria-label': '重新加载图片',
            onclick: (event) => {
              event.preventDefault();
              event.stopPropagation();
              startLoad();
            },
          },
          icon('refresh')
        )
      );
    });

    img.src = source;
    tile.prepend(img);
  }

  startLoad();
  return tile;
}

/* ---------------------------------------------------------------- lightbox */

/**
 * A SINGLE lightbox node is created lazily on first use and reused for every
 * image on the page. It shows the exact preview URL the tile already loaded
 * (never `originalUrl`) and issues no extra API request.
 */
let lightbox = null;

/** Guards the one-time Escape binding. */
let lightboxEscapeBound = false;

/** Binds the Escape key once (closing the preview). */
function ensureLightboxEscape() {
  if (lightboxEscapeBound) return;
  if (typeof document === 'undefined' || typeof document.addEventListener !== 'function') return;
  lightboxEscapeBound = true;

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeLightbox();
  });
}

/** Builds the shared lightbox DOM (once) and appends it to the document body. */
function buildLightbox() {
  const image = h('img', { class: 'lightbox__image', alt: '' });
  const close = h(
    'button',
    {
      type: 'button',
      class: 'lightbox__close',
      'aria-label': '关闭',
      title: '关闭',
      onclick: closeLightbox,
    },
    icon('close')
  );
  const overlay = h(
    'div',
    { class: 'lightbox', role: 'dialog', 'aria-modal': 'true', 'aria-label': '图片预览' },
    close,
    image
  );
  // Clicking the backdrop (but not the image) closes the preview.
  overlay.addEventListener('click', (event) => {
    if (event.target === overlay) closeLightbox();
  });
  document.body.append(overlay);
  return { overlay, image };
}

/**
 * Opens the shared preview for one already-loaded image URL.
 * @param {string} source an image URL a tile has already rendered
 * @param {string} [alt]
 */
export function openLightbox(source, alt = '') {
  if (typeof document === 'undefined' || !document.body || !source) return;
  if (!lightbox) lightbox = buildLightbox();

  lightbox.image.src = source;
  lightbox.image.alt = alt || '';
  lightbox.overlay.classList.add('lightbox--open');
  document.documentElement.classList.add('lightbox-open');
}

/** Closes the preview and restores page scrolling. Safe to call when closed. */
export function closeLightbox() {
  if (!lightbox) return;
  lightbox.overlay.classList.remove('lightbox--open');
  lightbox.image.removeAttribute('src');
  document.documentElement.classList.remove('lightbox-open');
}

// The Escape binding is installed once per page load.
ensureLightboxEscape();
