/**
 * ContentPlatformBackend — Cloudflare Pages Advanced Mode worker.
 *
 * This file is the deployment source for the Pages project. It is intentionally
 * placed under deploy/cloudflare/ so it can never be confused with, or
 * overwrite, the unrelated Xray/subscription `_worker.js` in the repository
 * root (which is NOT part of this platform).
 *
 * Routing contract (frozen in P7-B):
 *   Browser
 *     -> Cloudflare Pages (this worker)
 *     -> same-origin "/api/*"  => Cloud Run origin
 *     -> everything else        => Pages static assets (env.ASSETS)
 *
 * The Cloud Run origin is NEVER hardcoded here. It is read from the Pages
 * environment variable `API_ORIGIN` (e.g. "https://<service>-<hash>-<region>.a.run.app").
 * Set it for each Pages environment (Production/Preview) in the Pages dashboard,
 * or with `wrangler pages secret put API_ORIGIN` / project env settings. The
 * value must be an absolute https origin with no trailing slash.
 *
 * This worker adds no CORS headers, does not rewrite API paths, and does not
 * touch the authentication contract. Authentication stays a server-side
 * HttpSession delivered as an HttpOnly cookie, entirely same-origin from the
 * browser's point of view.
 */

const API_PREFIX = '/api/';

export default {
  /**
   * @param {Request} request
   * @param {{API_ORIGIN?: string, ASSETS: {fetch: (req: Request) => Promise<Response>}}} env
   * @param {ExecutionContext} ctx
   * @returns {Promise<Response>}
   */
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (url.pathname === '/api' || url.pathname.startsWith(API_PREFIX)) {
      return proxyToCloudRun(request, env);
    }

    return env.ASSETS.fetch(request);
  },
};

/**
 * Proxies a same-origin "/api/*" request to the configured Cloud Run origin,
 * preserving method, body, headers, cookies and the upstream response
 * (including multiple Set-Cookie headers, Link, Location, Content-Type and the
 * original status code).
 *
 * @param {Request} request
 * @param {{API_ORIGIN?: string}} env
 * @returns {Promise<Response>}
 */
async function proxyToCloudRun(request, env) {
  const origin = (env.API_ORIGIN || '').trim();

  if (!origin) {
    // Fail loudly but without leaking anything: this is a deployment
    // configuration error, not a client error.
    return new Response('Bad Gateway: API_ORIGIN is not configured', {
      status: 502,
      headers: { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' },
    });
  }

  const incoming = new URL(request.url);
  const target = new URL(incoming.pathname + incoming.search, origin);

  // Forward the client's headers (this includes Cookie, Accept, Content-Type
  // and any conditional headers). Hop-by-hop / platform-managed headers are
  // removed so the upstream fetch computes them correctly.
  const upstreamHeaders = new Headers(request.headers);
  for (const name of [
    'host',
    'connection',
    'keep-alive',
    'proxy-authenticate',
    'proxy-authorization',
    'te',
    'trailer',
    'transfer-encoding',
    'upgrade',
    'content-length',
  ]) {
    upstreamHeaders.delete(name);
  }

  /** @type {RequestInit & {cf?: object, duplex?: string}} */
  const init = {
    method: request.method,
    headers: upstreamHeaders,
    // Preserve upstream redirects instead of following them, so the browser
    // receives the original Location header.
    redirect: 'manual',
    // Never let Cloudflare cache API traffic.
    cf: { cacheEverything: false },
  };

  if (request.method !== 'GET' && request.method !== 'HEAD' && request.body) {
    init.body = request.body;
    init.duplex = 'half';
  }

  let upstream;
  try {
    upstream = await fetch(target.toString(), init);
  } catch (cause) {
    return new Response('Bad Gateway: upstream request failed', {
      status: 502,
      headers: { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' },
    });
  }

  // Copy every response header, gathering Set-Cookie separately so that
  // multiple Set-Cookie headers survive intact (Headers iteration would fold
  // them together).
  const responseHeaders = new Headers();
  for (const [key, value] of upstream.headers) {
    if (key.toLowerCase() === 'set-cookie') continue;
    responseHeaders.append(key, value);
  }

  const setCookies =
    typeof upstream.headers.getSetCookie === 'function' ? upstream.headers.getSetCookie() : [];
  for (const cookie of setCookies) {
    responseHeaders.append('Set-Cookie', cookie);
  }

  // API responses are never cacheable. This also stops the browser/CDN from
  // replaying stale auth or session responses.
  responseHeaders.set('Cache-Control', 'no-store');
  responseHeaders.set('CDN-Cache-Control', 'no-store');

  // 204 No Content (logout) and 304 must not carry a body.
  if (upstream.status === 204 || upstream.status === 304) {
    return new Response(null, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: responseHeaders,
    });
  }

  // Body is streamed straight through; 401/403/4xx/5xx status and empty bodies
  // are preserved exactly as the backend produced them.
  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  });
}
