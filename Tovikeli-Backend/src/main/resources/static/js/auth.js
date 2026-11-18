/**
 * Shared authentication state.
 *
 * Authentication is 100% server-side: logging in creates an HttpSession and the
 * browser stores the session id in an HttpOnly cookie that JavaScript cannot
 * read. This module therefore never creates, holds or transmits a credential.
 *
 * What this module stores:
 *  - The public user profile returned by `POST /api/v1/auth/login`
 *    (`id`, `username`, `displayName`) in `sessionStorage`, purely so the shared
 *    header can keep showing who is signed in across the full page navigations
 *    of this multi-page site.
 *
 * What this module never stores (enforced by design, no field ever contains it):
 *  - passwords
 *  - session ids / cookies
 *  - authentication tokens of any kind
 *  - anything in `localStorage` (sessionStorage is per-tab and cleared on close)
 *
 * The cached profile is DISPLAY ONLY and is never trusted for authorization.
 * Every protected request is authorized by the backend against the session
 * cookie. If the session has ended server-side, the next protected request
 * answers 401 and `handleAuthLost()` clears the cached profile.
 *
 * Because the backend exposes no "who am I" endpoint (and none is invented
 * here), this cache is optimistic: it cannot detect expiry until a request is
 * actually made. That is the intended trade-off for a cookie-only session.
 */

import { api, ApiError } from './api.js';

/** sessionStorage key for the display-only profile cache. */
const PROFILE_KEY = 'cp.auth.profile';

/** @type {{id: string, username: string, displayName: string|null}|null} */
let currentUser = null;

/** @type {Set<(user: object|null) => void>} */
const listeners = new Set();

/**
 * Reads back a previously cached profile, validating every field so tampered or
 * malformed storage can never reach the DOM.
 * @returns {{id: string, username: string, displayName: string|null}|null}
 */
function readCachedProfile() {
  try {
    const raw = sessionStorage.getItem(PROFILE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (typeof parsed?.id !== 'string' || parsed.id === '') return null;
    if (typeof parsed?.username !== 'string' || parsed.username === '') return null;
    return {
      id: parsed.id,
      username: parsed.username,
      displayName: typeof parsed.displayName === 'string' ? parsed.displayName : null,
    };
  } catch {
    // Storage unavailable or corrupted: behave as "not signed in".
    return null;
  }
}

/**
 * Writes (or clears) the display-only profile cache.
 * @param {{id: string, username: string, displayName: string|null}|null} user
 */
function writeCachedProfile(user) {
  try {
    if (user) {
      sessionStorage.setItem(
        PROFILE_KEY,
        JSON.stringify({ id: user.id, username: user.username, displayName: user.displayName })
      );
    } else {
      sessionStorage.removeItem(PROFILE_KEY);
    }
  } catch {
    // Storage is optional: failures must never break signing in or out.
  }
}

/** Notifies subscribers of an auth state change. */
function emit() {
  for (const listener of listeners) {
    try {
      listener(currentUser);
    } catch {
      // A broken subscriber must not break the others.
    }
  }
}

/**
 * Sets the in-memory + cached identity.
 * @param {{id: string, username: string, displayName: string|null}|null} user
 */
function setUser(user) {
  currentUser = user;
  writeCachedProfile(user);
  emit();
}

/** Maps the backend `UserResponse` DTO onto the fields this UI needs. */
function toProfile(userResponse) {
  return {
    id: userResponse.id,
    username: userResponse.username,
    displayName: userResponse.displayName ?? null,
  };
}

export const auth = {
  /** Restores the cached profile at page load. Call once per page. */
  restore() {
    currentUser = readCachedProfile();
    emit();
  },

  /** @returns {{id: string, username: string, displayName: string|null}|null} */
  getUser() {
    return currentUser;
  },

  /** @returns {boolean} */
  isAuthenticated() {
    return currentUser !== null;
  },

  /**
   * Subscribes to auth state changes (called immediately with the current state).
   * @param {(user: object|null) => void} listener
   * @returns {() => void} unsubscribe
   */
  subscribe(listener) {
    listeners.add(listener);
    listener(currentUser);
    return () => listeners.delete(listener);
  },

  /**
   * Signs in via `POST /api/v1/auth/login`.
   *
   * The request carries only the credentials the user typed; the server
   * responds with a session cookie (HttpOnly, invisible here) plus the public
   * profile.
   *
   * @param {string} username
   * @param {string} password
   * @returns {Promise<object>} the profile
   * @throws {ApiError} 401 wrong credentials, 403 account disabled
   */
  async login(username, password) {
    const user = await api.post(
      '/auth/login',
      { username, password },
      // A 401 here means "wrong credentials", not "session expired", and a 403
      // means "account disabled" — both are shown by the login form itself, so
      // neither may trigger the global session/forbidden notices.
      { notifyAuthLost: false, notifyForbidden: false }
    );
    const profile = toProfile(user);
    setUser(profile);
    return profile;
  },

  /**
   * Registers via `POST /api/v1/users`, then signs in with the same
   * credentials so the user lands on the site already authenticated.
   *
   * Registration itself does not create a session on the backend.
   *
   * @param {{username: string, password: string, displayName?: string|null}} input
   * @returns {Promise<{signedIn: boolean, profile: object|null}>}
   * @throws {ApiError} 400 invalid input, 409 username taken
   */
  async register({ username, password, displayName }) {
    const body = { username, password, displayName: displayName || null };
    await api.post('/users', body, { notifyAuthLost: false, notifyForbidden: false });

    // The account exists; now establish the session. If this fails (for
    // example the account is disabled) the user is sent to the login page
    // instead of being left in a half-registered state.
    try {
      const profile = await this.login(username, password);
      return { signedIn: true, profile };
    } catch {
      return { signedIn: false, profile: null };
    }
  },

  /**
   * Signs out via `POST /api/v1/auth/logout`.
   *
   * The endpoint invalidates the server-side session and is safe to call
   * without a session. Local state is cleared even when the call fails, so the
   * UI can never be left showing a signed-in state after the user asks to
   * leave.
   *
   * @returns {Promise<void>}
   */
  async logout() {
    try {
      await api.post('/auth/logout', undefined, { notifyAuthLost: false });
    } catch {
      // Ignored on purpose: signing out locally must always succeed.
    } finally {
      setUser(null);
    }
  },

  /**
   * Clears local auth state without calling the backend.
   * Used when a protected request answers 401 (expired/removed session).
   */
  clear() {
    setUser(null);
  },
};

/** Type-only re-export so pages can `catch (e) { if (e instanceof ApiError) ... }`. */
export { ApiError };
