/**
 * Public user profile lookup by id.
 *
 * `GET /api/v1/users/{userId}` is a PUBLIC endpoint returning only the public
 * profile (`id`, `username`, `displayName`, `status`). It never returns a
 * password, a hash or anything else secret, so calling it from the browser
 * leaks nothing.
 *
 * WHY THIS MODULE EXISTS
 * Comment and interaction payloads carry only a `userId`. Painting the raw
 * `usr_…` identifier is unreadable, but this frontend is mobile-first and a
 * request per rendered row would be an N+1 pattern (20 comments = 20 extra
 * requests). This module therefore:
 *   - caches resolved profiles for the page's lifetime,
 *   - de-duplicates concurrent lookups of the same id, so rendering 20 comments
 *     written by 3 people costs 3 requests, not 20,
 *   - never blocks rendering: callers paint a placeholder immediately and let
 *     `resolveUser` upgrade it when the profile arrives.
 *
 * This is presentation only. A profile here is never used for authorization:
 * the server decides ownership (`403`) and the frontend merely decides whether
 * to *offer* an edit/delete control.
 */

import { api } from './api.js';
import { auth } from './auth.js';

/** userId -> {id, username, displayName} */
const cache = new Map();

/** Ids that are known to be unresolvable (404) for this page's lifetime. */
const unresolvable = new Set();

/** userId -> in-flight Promise, so N callers share one request. */
const inFlight = new Map();

/**
 * Normalises an API `UserResponse` to the three fields this UI displays.
 * Anything malformed is treated as "not resolvable" rather than rendered.
 * @param {any} profile
 * @returns {{id: string, username: string, displayName: string|null}|null}
 */
function normalise(profile) {
  if (!profile || typeof profile !== 'object') return null;
  if (typeof profile.id !== 'string' || profile.id === '') return null;
  if (typeof profile.username !== 'string' || profile.username === '') return null;
  return {
    id: profile.id,
    username: profile.username,
    displayName: typeof profile.displayName === 'string' ? profile.displayName : null,
  };
}

/**
 * Returns an already-resolved profile, or null. Never performs a request.
 * @param {string} userId
 * @returns {{id: string, username: string, displayName: string|null}|null}
 */
export function cachedUser(userId) {
  return cache.get(userId) ?? null;
}

/**
 * Resolves a user profile, de-duplicating concurrent lookups.
 *
 * Resolves to `null` instead of throwing when the profile cannot be shown:
 * authorship is cosmetic and must never break the surrounding page.
 *
 * @param {string} userId
 * @returns {Promise<{id: string, username: string, displayName: string|null}|null>}
 */
export function resolveUser(userId) {
  if (typeof userId !== 'string' || userId === '') return Promise.resolve(null);
  if (cache.has(userId)) return Promise.resolve(cache.get(userId));
  if (unresolvable.has(userId)) return Promise.resolve(null);

  const shared = inFlight.get(userId);
  if (shared) return shared;

  const pending = api
    // The auth-lost handler must not fire for this decorative request: a 401
    // (or any failure) here just means "no name to show".
    .get(`/users/${encodeURIComponent(userId)}`, { notifyAuthLost: false })
    .then((profile) => {
      const normalised = normalise(profile);
      if (normalised) {
        cache.set(userId, normalised);
      } else {
        unresolvable.add(userId);
      }
      return normalised;
    })
    .catch(() => {
      // 404, offline, or the server is unhappy. Mark it so a broken network
      // cannot turn into a retry storm, and carry on without a name.
      unresolvable.add(userId);
      return null;
    })
    .finally(() => {
      inFlight.delete(userId);
    });

  inFlight.set(userId, pending);
  return pending;
}

/**
 * The label to show for a user id.
 *
 * Always returns something renderable *synchronously*, so callers never have to
 * wait on the network to paint a comment.
 *
 * @param {string} userId
 * @returns {string}
 */
export function userLabel(userId) {
  const profile = cache.get(userId);
  if (profile) return profile.displayName || profile.username;
  return shortId(userId);
}

/**
 * A compact, recognisable placeholder for an unresolved id
 * (`usr_95879f…1c76` -> `usr_95879f…`). Keeps long identifiers from breaking
 * the layout on a narrow screen.
 * @param {string} userId
 * @returns {string}
 */
export function shortId(userId) {
  if (typeof userId !== 'string' || userId === '') return '未知用户';
  return userId.length <= 16 ? userId : `${userId.slice(0, 14)}…`;
}

/**
 * Paints `userId`'s label into an element and upgrades it once resolved.
 *
 * @param {HTMLElement} element
 * @param {string} userId
 * @param {{self?: string}} [options] text used when this is the current caller
 */
export function decorateUserLabel(element, userId, options = {}) {
  const self = options.self;
  const cached = cache.get(userId);
  if (cached) {
    element.textContent = cached.displayName || cached.username;
    return;
  }

  element.textContent = self ?? userLabel(userId);

  resolveUser(userId)
    .then((profile) => {
      // The row may have been removed (deleted, re-rendered) while we waited.
      if (!profile || !element.isConnected) return;
      element.textContent = profile.displayName || profile.username;
    })
    .catch(() => {
      // Leave the placeholder label in place.
    });
}

/** Drops the cache. Called when the signed-in identity changes. */
export function clearUserCache() {
  cache.clear();
  unresolvable.clear();
  inFlight.clear();
}

// Identity changes invalidate every cached profile. Today each identity change
// navigates (so this only guards a page that is still alive when it happens),
// but the invariant must hold regardless: resolved names are shown next to
// authors, and one caller's view must never be painted with another's data.
let lastUserId = null;

auth.subscribe((user) => {
  const id = user ? user.id : null;
  if (id === lastUserId) return;
  lastUserId = id;
  clearUserCache();
});
