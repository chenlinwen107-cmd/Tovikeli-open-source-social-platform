/**
 * Centralized interaction state: LIKE / FAVORITE / REPOST / BOOKMARK.
 *
 * This is the single place that knows whether the current caller has reacted to
 * a piece of content, and the single place that sends interaction writes. Pages
 * subscribe instead of each keeping their own copy, so a state change can never
 * disagree between the detail page and a future feed.
 *
 * ---------------------------------------------------------------------------
 * THE SERVER IS AUTHORITATIVE
 * ---------------------------------------------------------------------------
 * Everything here is *presentation* state. It is seeded from
 *   GET /api/v1/users/{userId}/contents/{contentId}/interactions
 * and mutated only by
 *   POST   /api/v1/users/{userId}/interactions            {contentId, type}
 *   DELETE /api/v1/users/{userId}/interactions/{contentId}?type=…
 * The backend validates the session and that the path userId *is* the caller.
 * This module never decides authorization; it only decides what to draw.
 *
 * Because the page is reloaded on every navigation, the in-memory maps below
 * are the ONLY store. Nothing is written to `localStorage` or `sessionStorage`:
 * no reaction flags, no session id, no token. A hard refresh re-reads the truth
 * from the server, which is exactly the behaviour P5 requires.
 *
 * The three states a control can be in, per content and per type:
 *   - unauthenticated        -> no session, so no caller state exists
 *   - authenticated, inactive -> session present, reaction not set
 *   - authenticated, active   -> session present, reaction set
 */

import { api } from './api.js';
import { auth } from './auth.js';

/** The four platform interaction types (COMMENT is not an interaction here). */
export const INTERACTION_TYPES = Object.freeze(['LIKE', 'FAVORITE', 'REPOST', 'BOOKMARK']);

/** Human-readable labels, kept in one place so pages stay consistent. */
export const INTERACTION_LABELS = Object.freeze({
  LIKE: '点赞',
  FAVORITE: '收藏',
  REPOST: '转发',
  BOOKMARK: '书签',
});

/** Maps the API's interaction type to the field name in the state response. */
const FIELD_BY_TYPE = Object.freeze({
  LIKE: 'like',
  FAVORITE: 'favorite',
  REPOST: 'repost',
  BOOKMARK: 'bookmark',
});

/** The neutral state used before the server has been asked. */
const EMPTY_STATE = Object.freeze({ like: false, favorite: false, repost: false, bookmark: false });

/** contentId -> {like,favorite,repost,bookmark}. Absent means "not known yet". */
const stateByContent = new Map();

/** contentId -> Promise, so two components never issue the same GET twice. */
const loadInFlight = new Map();

/** contentId -> Set<type> currently being written to the server. */
const pendingByContent = new Map();

/** contentId -> Set<listener>. */
const listenersByContent = new Map();

/** Tracks the identity the cached state belongs to. */
let lastUserId = null;

/** @returns {boolean} whether `type` is one of the four supported types. */
export function isInteractionType(type) {
  return INTERACTION_TYPES.includes(type);
}

/** The pending set for a content, created on demand. */
function pendingSet(contentId) {
  let set = pendingByContent.get(contentId);
  if (!set) {
    set = new Set();
    pendingByContent.set(contentId, set);
  }
  return set;
}

/**
 * An immutable view of one content's interaction state, handed to listeners.
 *
 * @typedef {object} InteractionSnapshot
 * @property {string} contentId
 * @property {boolean} authenticated the caller has a session as far as the UI knows
 * @property {boolean} known the server state has been read
 * @property {{like: boolean, favorite: boolean, repost: boolean, bookmark: boolean}} state
 * @property {Record<string, boolean>} pending per-type "write in flight"
 */

/**
 * @param {string} contentId
 * @returns {InteractionSnapshot}
 */
export function getSnapshot(contentId) {
  const known = stateByContent.has(contentId);
  const stored = stateByContent.get(contentId);
  const pending = pendingByContent.get(contentId);

  /** @type {Record<string, boolean>} */
  const pendingFlags = {};
  for (const type of INTERACTION_TYPES) {
    pendingFlags[type] = pending ? pending.has(type) : false;
  }

  return {
    contentId,
    authenticated: auth.isAuthenticated(),
    known,
    state: stored ? { ...stored } : { ...EMPTY_STATE },
    pending: pendingFlags,
  };
}

/** Notifies every listener of one content. */
function notify(contentId) {
  const listeners = listenersByContent.get(contentId);
  if (!listeners || listeners.size === 0) return;
  const snapshot = getSnapshot(contentId);
  for (const listener of listeners) {
    try {
      listener(snapshot);
    } catch {
      // A broken subscriber must not break the others.
    }
  }
}

/** Writes a state (or clears it when `state` is undefined). */
function setState(contentId, state) {
  if (state === undefined) {
    stateByContent.delete(contentId);
  } else {
    stateByContent.set(contentId, state);
  }
  notify(contentId);
}

/**
 * Subscribes to one content's interaction state. The listener is invoked
 * immediately with the current snapshot.
 *
 * @param {string} contentId
 * @param {(snapshot: InteractionSnapshot) => void} listener
 * @returns {() => void} unsubscribe
 */
export function subscribe(contentId, listener) {
  let listeners = listenersByContent.get(contentId);
  if (!listeners) {
    listeners = new Set();
    listenersByContent.set(contentId, listeners);
  }
  listeners.add(listener);
  listener(getSnapshot(contentId));
  return () => {
    listeners.delete(listener);
  };
}

/**
 * Reads the caller's interaction state for one content, at most once.
 *
 * Signed out, there is nothing to read and the state is cleared so a previously
 * signed-in user's flags can never linger. A failure leaves the state "unknown"
 * (controls render inactive) rather than throwing: the subsequent write is
 * idempotent on the server, so a wrong-looking control self-corrects on click
 * instead of blocking the page.
 *
 * @param {string} contentId
 * @returns {Promise<void>}
 */
export function load(contentId) {
  if (!auth.isAuthenticated()) {
    setState(contentId, undefined);
    return Promise.resolve();
  }
  if (stateByContent.has(contentId)) return Promise.resolve();

  const shared = loadInFlight.get(contentId);
  if (shared) return shared;

  const user = auth.getUser();
  const pending = api
    .get(
      `/users/${encodeURIComponent(user.id)}/contents/${encodeURIComponent(contentId)}/interactions`,
      // A 401 here is "no session"; the global handler already deals with it,
      // and this call must not additionally wipe local auth state.
      { notifyAuthLost: false }
    )
    .then((state) => {
      setState(contentId, {
        like: state?.like === true,
        favorite: state?.favorite === true,
        repost: state?.repost === true,
        bookmark: state?.bookmark === true,
      });
    })
    .catch(() => {
      setState(contentId, undefined);
    })
    .finally(() => {
      loadInFlight.delete(contentId);
    });

  loadInFlight.set(contentId, pending);
  return pending;
}

/**
 * Toggles one interaction type for one content.
 *
 * Optimistic: the control flips immediately, then the server call decides. On
 * failure the previous state is restored, so this can never report success for
 * a request that failed.
 *
 * @param {string} contentId
 * @param {string} type LIKE / FAVORITE / REPOST / BOOKMARK
 * @returns {Promise<{ok: boolean, active?: boolean, reason?: 'unauthenticated'|'pending'|'error', error?: unknown}>}
 */
export async function toggle(contentId, type) {
  if (!isInteractionType(type)) {
    throw new Error(`Unknown interaction type: ${type}`);
  }
  if (!auth.isAuthenticated()) {
    return { ok: false, reason: 'unauthenticated' };
  }
  if (pendingSet(contentId).has(type)) {
    // A second click during an in-flight write is ignored rather than queued.
    return { ok: false, reason: 'pending' };
  }

  const user = auth.getUser();
  const before = stateByContent.get(contentId) ?? { ...EMPTY_STATE };
  const next = !before[FIELD_BY_TYPE[type]];

  const pendingFlags = pendingSet(contentId);
  pendingFlags.add(type);
  // Optimistic flip — visible immediately, and pending blocks further clicks.
  setState(contentId, { ...before, [FIELD_BY_TYPE[type]]: next });

  try {
    if (next) {
      // Idempotent on the server: 201 when created, 200 when it already existed.
      await api.post(`/users/${encodeURIComponent(user.id)}/interactions`, {
        contentId,
        type,
      });
    } else {
      // Idempotent as well: 204 whether or not the interaction was still there.
      await api.del(
        `/users/${encodeURIComponent(user.id)}/interactions/${encodeURIComponent(contentId)}`,
        { query: { type } }
      );
    }
    return { ok: true, active: next };
  } catch (error) {
    // The server refused: undo the optimistic flip and surface the truth.
    setState(contentId, before);
    return { ok: false, reason: 'error', error };
  } finally {
    pendingFlags.delete(type);
    notify(contentId);
  }
}

/**
 * Clears every cached state. Called whenever the signed-in identity changes, so
 * one caller's reactions can never be shown to another.
 */
export function resetAll() {
  const touched = new Set([...stateByContent.keys(), ...listenersByContent.keys()]);
  stateByContent.clear();
  loadInFlight.clear();
  pendingByContent.clear();
  for (const contentId of touched) notify(contentId);
}

// Identity changes invalidate everything: a different caller has different
// reactions, and a signed-out visitor has none.
auth.subscribe((user) => {
  const id = user ? user.id : null;
  if (id === lastUserId) return;
  lastUserId = id;
  resetAll();
});
