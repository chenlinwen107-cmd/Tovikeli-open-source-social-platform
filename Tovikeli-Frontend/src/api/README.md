# Backend API layer (`src/api`)

Thin, typed wrappers over the **real** `ContentPlatformBackend` HTTP contract
(`/api/v1`, Spring Session `JSESSIONID`). Nothing here invents endpoints, and no
mock data is used.

- `client.ts` – existing axios instance (`baseURL: '/api/v1'`, `withCredentials: true`).
- `types.ts` – DTOs mirroring the backend `api/**Response.kt` / `*Request.kt`.
- `pagination.ts` – parses the standard `Link: <...>; rel="next"` header into a `Page<T>`.
- One module per backend controller: `auth`, `users`, `contents`, `feed`,
  `comments`, `interactions`, `media`, `search`, `tags`, `authors`, `instances`.
- `index.ts` – barrel re-export.

These files are additive. No existing page/component was changed in this step.

## Auth model

- Login: `POST /auth/login` → sets the server-side session cookie.
- Logout: `POST /auth/logout` → invalidates the session.
- **There is no `GET /auth/me`.** After a hard refresh the client cannot restore
  the current user from the backend; this must be treated as a backend gap, not
  faked. Session cookie survives, so subsequent authenticated calls still work,
  but the user profile must be re-fetched by id if known.
- Protected endpoints are self-only: the path `{userId}` must equal the
  authenticated user's id, otherwise the backend answers `403`.

## Pagination

Browse/feed/comments/tag-contents/author-contents/interaction-history use
stateless keyset pagination and return the next page via the `Link` response
header only. Use the `Page<T>` result:

```ts
import { getFeed } from '../api';

const first = await getFeed({ limit: 20 });
const second = await getFeed({ limit: 20, cursor: first.nextCursor ?? undefined });
```

## Hydration caveat

`ContentResponse` carries `authorId` and `mediaIds` only — no embedded author
object and no media URLs, and no per-viewer `liked/bookmarked` flags. Rendering a
card like the current `PostCard` requires extra reads:

- author: `getAuthor(authorId)`
- media: `getMediaBatch(content.mediaIds)` (≤ 50 ids per call)
- viewer interaction state: `getInteractionState(currentUserId, contentId)`

Counts are only present on the **detail** endpoint (`getContent`); browse/feed/
search responses report zeroed `commentCount` / `interactionStatistics`.

## Endpoints exposed

| Function | Backend endpoint |
| --- | --- |
| `login`, `logout` | `POST /auth/login`, `POST /auth/logout` |
| `createUser`, `getUser` | `POST /users`, `GET /users/{userId}` |
| `browseContents` | `GET /contents?limit&instanceId&authorId&hasMedia&cursor` |
| `getContent` | `GET /contents/{id}` |
| `getContentMedia` | `GET /contents/{contentId}/media` |
| `getContentRelationships` | `GET /contents/{contentId}/relationships` |
| `deleteContent`, `hideContent`, `restoreContent` | `POST /contents/{contentId}/{delete\|hide\|restore}` |
| `getFeed` | `GET /feed?limit&cursor` |
| `createComment`, `listComments` | `POST/GET /contents/{contentId}/comments` |
| `getComment`, `listReplies` | `GET /comments/{commentId}`, `GET /comments/{commentId}/replies` |
| `editComment`, `deleteComment` | `PATCH/DELETE /comments/{commentId}` |
| `createInteraction`, `getUserInteractions` | `POST/GET /users/{userId}/interactions` |
| `deleteInteraction` | `DELETE /users/{userId}/interactions/{contentId}?type=` |
| `getInteractionState` | `GET /users/{userId}/contents/{contentId}/interactions` |
| `getMedia`, `getMediaBatch` | `GET /media/{mediaId}`, `GET /media/batch?ids=` |
| `searchContents` | `GET /search?q&limit&instanceId&authorId&sourceId` |
| `searchTags`, `getTag`, `getTagContents` | `GET /tags`, `GET /tags/{id}`, `GET /tags/{tagId}/contents` |
| `getAuthor`, `getAuthorContents` | `GET /authors/{id}`, `GET /authors/{authorId}/contents` |
| `getInstance` | `GET /instances/{id}` |

## Backend capability gaps (legacy frontend APIs with no backend counterpart)

These are used by the current UI but **do not exist** in `ContentPlatformBackend`.
They must not be recreated or faked; the affected pages remain structurally
intact but their operations are unavailable until the backend grows them.

- Content authoring: `POST /posts`, `PUT /posts/{id}`, `/posts/{id}/unlock|pin|global-pin`,
  and `POST /upload` (no media upload endpoint at all).
- Content feed variants: `filter=recommend|following|video`; `/posts/{id}/related`,
  `/posts/{id}/siblings`.
- Comment extras: comment likes (`POST /comments/{id}/like`), comment sort (`sort=latest|hot`).
- Reactions beyond LIKE/FAVORITE/REPOST/BOOKMARK; `/posts/{id}/reactions`.
- Follow / block / report: `POST /users/{id}/follow|block`, `GET /users/me/blocks`,
  `GET /users/{username}/{followers|following}`, `GET /users/{username}/visitors`,
  `POST /reports`.
- User profile: `GET /users/{username}` (backend is id-only), `PUT /users/me/profile`,
  `POST /auth/password`.
- Site config: `GET /site`.
- Messages / notifications / history: `/messages*`, `/notifications*`, `/history*`,
  `/messages/unread`.
- Bookmarks list shortcut `GET /users/me/bookmarks` (only the generic
  `getUserInteractions(userId, { type: 'BOOKMARK' })` exists, self-only).
- Topics as hashtags `/topics*`, `/topics/{name}/follow` (backend has a different
  `Tag` aggregate).
- Forums `/forum/*`, articles `/articles*`, events `/events*`, circles `/circles*`,
  Q&A `/qa*`, collections `/collections*`, flash `/flash*`, nav `/nav*`, AI `/ai/*`,
  and the whole `/admin/*` surface.
