/**
 * Shared API helper.
 *
 * Single place that talks to the ContentPlatformBackend HTTP API.
 *
 * Contracts this file encodes (verified against the real controllers):
 *  - API prefix is `/api/v1`.
 *  - Responses are FLAT JSON (a bare object or a bare array). There is no
 *    `{ data, meta, error }` envelope anywhere in the backend.
 *  - Lists use keyset pagination and expose the next page via a standard
 *    `Link: <...>; rel="next"` response header (readable same-origin).
 *  - Errors respond with an EMPTY body and a meaningful status code, so
 *    user-facing messages are derived from the status, never from a body.
 *  - Auth is the server-side HttpSession delivered as an HttpOnly cookie.
 *    Same-origin fetch sends it automatically; this file never reads, writes
 *    or stores a session id, token or password.
 *  - `X-User-Id` does not exist in this codebase and is never sent.
 *
 * Nothing here is a token abstraction: there is no Authorization header,
 * no bearer logic and no client-side session state.
 */

/** Base path of the backend API. Same-origin by design (no CORS setup). */
const API_BASE = '/api/v1';

/** Error thrown for any non-2xx HTTP response. */
export class ApiError extends Error {
  /**
   * @param {number} status HTTP status code
   * @param {string} message user-facing message
   */
  constructor(status, message) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/**
 * Maps an HTTP status to a safe, user-facing message.
 *
 * Backend error bodies are empty, and internal details are never surfaced.
 * @param {number} status
 * @returns {string}
 */
function messageForStatus(status) {
  switch (status) {
    case 400:
      return '请求无效，请检查输入后重试。';
    case 401:
      return '请先登录后再进行该操作。';
    case 403:
      return '你的账号无权执行该操作。';
    case 404:
      return '未找到该内容，可能已被删除。';
    case 409:
      return '与已有内容冲突。';
    case 422:
      return '该操作不适用于此内容。';
    case 429:
      return '请求过于频繁，请稍后再试。';
    default:
      if (status >= 500) {
        return '服务器出现问题，请稍后重试。';
      }
      return '出错了，请重试。';
  }
}

/** Handler invoked when the server reports the session is no longer valid. */
let authLostHandler = null;

/**
 * Registers the callback invoked on a 401 from a protected request, so the
 * shell can clear stale client-side auth state. A 401 on the login endpoint
 * means "wrong credentials" instead, and is excluded there explicitly.
 * @param {(status: number) => void} handler
 */
export function onAuthLost(handler) {
  authLostHandler = handler;
}

/** Handler invoked when the server refuses an action for the current caller. */
let forbiddenHandler = null;

/**
 * Registers the callback invoked on a 403 from a protected request, so the
 * shell can explain that the action was refused instead of leaving the user
 * with a silent failure.
 *
 * A 403 is NOT a successful authentication and NOT an expired session: the
 * caller is recognised but not permitted (or is not the owner of the resource).
 * It is therefore reported as its own state and never converted into either.
 *
 * Endpoints where 403 carries a different meaning (a disabled account at login,
 * for instance) opt out with `notifyForbidden: false`.
 *
 * @param {(status: number) => void} handler
 */
export function onForbidden(handler) {
  forbiddenHandler = handler;
}

/**
 * Builds a URL from a path and optional query parameters.
 * Null/undefined/empty values are omitted rather than sent as "null".
 * @param {string} path e.g. "/contents"
 * @param {Record<string, unknown>} [query]
 * @returns {string}
 */
function buildUrl(path, query) {
  const url = API_BASE + path;
  if (!query) return url;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue;
    params.append(key, String(value));
  }
  const qs = params.toString();
  return qs ? `${url}?${qs}` : url;
}

/**
 * Extracts the `rel="next"` target from an HTTP `Link` header.
 * @param {string|null} headerValue e.g. '</api/v1/contents?cursor=x>; rel="next"'
 * @returns {string|null} the raw URL, or null when there is no next page
 */
function parseNextLink(headerValue) {
  if (!headerValue) return null;
  for (const part of headerValue.split(',')) {
    const match = part.match(/<([^>]+)>\s*;\s*rel\s*=\s*"?next"?/i);
    if (match) return match[1];
  }
  return null;
}

/**
 * Performs one HTTP request.
 * @param {string} method
 * @param {string} path
 * @param {{query?: object, body?: unknown, notifyAuthLost?: boolean, notifyForbidden?: boolean}} [options]
 * @returns {Promise<{data: any, response: Response}>}
 */
async function request(method, path, options = {}) {
  const { query, body, notifyAuthLost = true, notifyForbidden = true } = options;

  /** @type {RequestInit} */
  const init = {
    method,
    // The session cookie is HttpOnly and managed entirely by the browser.
    // `same-origin` sends it for same-origin requests and is the default;
    // it is stated explicitly so intent is unambiguous.
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  };

  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(buildUrl(path, query), init);
  } catch (cause) {
    // Network-level failure (offline, server down, DNS). Never a status code.
    throw new ApiError(0, '无法连接到服务器，请检查网络后重试。');
  }

  if (response.status === 401 && notifyAuthLost && authLostHandler) {
    authLostHandler(response.status);
  }

  // A refusal is reported once for the page, so the user learns why an action
  // did nothing. It is deliberately separate from the 401 path: being forbidden
  // does not mean the session ended, and it must never read as "signed in".
  if (response.status === 403 && notifyForbidden && forbiddenHandler) {
    forbiddenHandler(response.status);
  }

  if (!response.ok) {
    throw new ApiError(response.status, messageForStatus(response.status));
  }

  if (response.status === 204) {
    return { data: null, response };
  }

  const text = await response.text();
  if (!text) {
    return { data: null, response };
  }

  try {
    return { data: JSON.parse(text), response };
  } catch (cause) {
    throw new ApiError(response.status, '服务器返回了异常响应。');
  }
}

/**
 * HTTP verbs. `getPage` additionally resolves the `Link` next-page URL.
 */
export const api = {
  /**
   * GET returning the parsed JSON body.
   * @param {string} path
   * @param {{query?: object, notifyAuthLost?: boolean}} [options]
   */
  async get(path, options) {
    const { data } = await request('GET', path, options);
    return data;
  },

  /**
   * GET a paginated list, resolving the next-page URL from the `Link` header.
   * @param {string} path
   * @param {{query?: object, notifyAuthLost?: boolean}} [options]
   * @returns {Promise<{items: any[], nextUrl: string|null}>}
   */
  async getPage(path, options) {
    const { data, response } = await request('GET', path, options);
    return {
      items: Array.isArray(data) ? data : [],
      nextUrl: parseNextLink(response.headers.get('Link')),
    };
  },

  /**
   * GET a next-page URL produced by the backend (already absolute-from-root).
   * @param {string} url e.g. "/api/v1/contents?limit=20&cursor=..."
   */
  async getPageByUrl(url) {
    const response = await fetch(url, {
      method: 'GET',
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
    });
    if (!response.ok) {
      throw new ApiError(response.status, messageForStatus(response.status));
    }
    const data = await response.json();
    return {
      items: Array.isArray(data) ? data : [],
      nextUrl: parseNextLink(response.headers.get('Link')),
    };
  },

  /**
   * POST with a JSON body.
   * @param {string} path
   * @param {unknown} [body]
   * @param {{notifyAuthLost?: boolean}} [options]
   */
  async post(path, body, options) {
    const { data } = await request('POST', path, { ...options, body });
    return data;
  },

  /**
   * PATCH with a JSON body.
   * @param {string} path
   * @param {unknown} [body]
   * @param {{notifyAuthLost?: boolean}} [options]
   */
  async patch(path, body, options) {
    const { data } = await request('PATCH', path, { ...options, body });
    return data;
  },

  /**
   * DELETE.
   * @param {string} path
   * @param {{query?: object, notifyAuthLost?: boolean}} [options]
   */
  async del(path, options) {
    const { data } = await request('DELETE', path, options);
    return data;
  },
};
