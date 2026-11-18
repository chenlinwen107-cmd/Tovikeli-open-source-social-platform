/**
 * Comments: list, thread, composer, reply, edit and delete.
 *
 * Real endpoints used (all verified against the running backend):
 *   GET    /api/v1/contents/{contentId}/comments?limit=&cursor=   public, keyset + Link
 *   POST   /api/v1/contents/{contentId}/comments                  {text, parentCommentId}
 *   PATCH  /api/v1/comments/{commentId}                           {text}   200|403|404|422
 *   DELETE /api/v1/comments/{commentId}                           204|403
 *
 * ---------------------------------------------------------------------------
 * THREAD SEMANTICS — MATCHING THE BACKEND EXACTLY
 * ---------------------------------------------------------------------------
 * `findByContentId` selects `WHERE content_id = ? AND status = 'ACTIVE'` with no
 * parent filter, so the list already contains top-level comments AND replies,
 * ordered by (created_at ASC, id ASC). Consequences this module honours:
 *
 *  - Threads are assembled on the client from data already fetched: a reply is
 *    nested under its parent when the parent is present in the loaded set.
 *    No extra request per comment.
 *  - A reply is never returned before its parent (a reply cannot be created
 *    before the thing it replies to, and ordering is by creation time), so
 *    nesting works even when parent and reply fall on different pages.
 *  - DELETED comments are FILTERED OUT of list, reply and count queries. So a
 *    deleted parent simply is not in the payload, while its replies still are.
 *    Those orphans are rendered inline with a "reply" marker — which is exactly
 *    what a fresh page load shows, so deleting locally must reproduce it.
 *  - Deleting a comment must therefore NOT delete its replies: they are
 *    re-parented to the surrounding list so they stay visible, and only the one
 *    comment is subtracted from the count (the backend counts ACTIVE rows).
 *  - `GET /api/v1/comments/{id}` does return DELETED rows, but this module never
 *    fetches single comments, so a tombstone can only be met defensively: the
 *    renderer handles `status === 'DELETED'`, and editing one answers 422, which
 *    is surfaced as "this comment has been deleted".
 *
 * `paged-list.js` is deliberately not used here: it appends every item to a flat
 * container, which cannot express nesting, and it has no notion of removing an
 * item or re-parenting children. Threaded, mutable comments need the small
 * purpose-built loader below. Everywhere a flat, append-only list is wanted
 * (home, search, author, tag) `paged-list.js` is still used unchanged.
 *
 * Ownership is PRESENTATION ONLY. `userId === auth.getUser().id` decides whether
 * an edit/delete control is *offered*; the server decides whether it succeeds
 * and answers 403 otherwise. No ownership flag is stored, and none is trusted.
 */

import { api, ApiError } from './api.js';
import { auth } from './auth.js';
import {
  h,
  render,
  notice,
  badge,
  loadingState,
  emptyState,
  errorState,
  formatRelative,
} from './ui.js';
import { decorateUserLabel } from './users.js';

/** Comments per page. The backend caps this at 100. */
const PAGE_SIZE = 20;

/** Reads the current page as a same-site path, for the post-login return. */
function currentPath() {
  return `${window.location.pathname}${window.location.search}`;
}

/**
 * Maps a comment API failure to an accurate, non-technical message. Raw server
 * output is never shown.
 * @param {unknown} error
 * @returns {string}
 */
export function commentErrorMessage(error) {
  if (!(error instanceof ApiError)) {
    return '评论保存失败，请重试。';
  }
  switch (error.status) {
    case 0:
      return '无法连接到服务器，请检查网络后重试。';
    case 400:
      return '请输入内容后再发布。';
    case 401:
      return '登录状态已失效，请重新登录后再评论。';
    case 403:
      return '你只能修改自己的评论。';
    case 404:
      return '该内容或评论已不可用。';
    case 409:
      return '该内容不允许评论。';
    case 422:
      return '该评论已被删除，无法再编辑。';
    default:
      return error.message || '评论保存失败，请重试。';
  }
}

/** True when the comment belongs to the signed-in caller (presentation only). */
function isOwnComment(comment) {
  const user = auth.getUser();
  return user !== null && comment.userId === user.id;
}

/**
 * A small plain-text composer, reused for "new comment", "reply" and "edit".
 *
 * `onSubmit` resolves on success (the composer clears itself, or the caller
 * collapses it) and rejects to have the mapped error shown while keeping the
 * user's text. Plain text only — no rich-text editor.
 *
 * @param {{
 *   label?: string,
 *   placeholder?: string,
 *   submitLabel: string,
 *   initial?: string,
 *   onCancel?: () => void,
 *   onSubmit: (text: string) => Promise<void>,
 * }} options
 * @returns {{element: HTMLElement, focus: () => void}}
 */
function createComposer(options) {
  const { label, placeholder = '', submitLabel, initial = '', onCancel, onSubmit } = options;

  const feedback = h('div', {});
  // No maxlength is imposed: the backend column is unbounded TEXT and only
  // requires non-blank, so the client must not reject text the server accepts.
  const textarea = h('textarea', {
    class: 'composer__input',
    placeholder,
    'aria-label': label || submitLabel,
    rows: '3',
  });
  textarea.value = initial;

  const submit = h('button', {
    class: 'btn btn--primary btn--inline',
    type: 'submit',
    text: submitLabel,
  });

  const cancel = onCancel
    ? h('button', { class: 'btn btn--inline', type: 'button', text: '取消', onclick: () => onCancel() })
    : null;

  const form = h(
    'form',
    {
      class: 'composer',
      novalidate: true,
      onsubmit: async (event) => {
        event.preventDefault();
        render(feedback);

        const text = textarea.value.trim();
        if (text === '') {
          render(feedback, notice('请输入内容后再发布。', 'error'));
          textarea.focus();
          return;
        }

        submit.disabled = true;
        const originalLabel = submit.textContent;
        submit.textContent = '正在保存…';

        try {
          await onSubmit(text);
          textarea.value = '';
        } catch (error) {
          // The typed text is preserved and the failure is stated plainly.
          render(feedback, notice(commentErrorMessage(error), 'error'));
        } finally {
          submit.disabled = false;
          submit.textContent = originalLabel;
        }
      },
    },
    label ? h('label', { class: 'composer__label', text: label }) : null,
    textarea,
    h('div', { class: 'composer__actions' }, submit, cancel),
    feedback
  );

  return {
    element: form,
    focus() {
      textarea.focus();
    },
  };
}

/**
 * Mounts the whole comments experience into `host`.
 *
 * @param {HTMLElement} host
 * @param {{
 *   contentId: string,
 *   commentCount?: number,
 *   onCountChange?: (count: number) => void,
 * }} options
 * @returns {Promise<{reload: () => Promise<void>}>}
 */
export async function mountComments(host, options) {
  const { contentId, onCountChange } = options;

  /** Displayed number of comments. Server value from the content detail. */
  let count = Number(options.commentCount) || 0;

  /** commentId -> record, for nesting, editing and deletion. */
  const records = new Map();

  /** Next page URL from the `Link` header, or null on the last page. */
  let nextUrl = null;

  /** Whether any comment is currently displayed (controls empty state). */
  let anyComments = false;

  const statusHost = h('div', { class: 'comments__status', role: 'status', 'aria-live': 'polite' });
  const stateHost = h('div', {});
  const listEl = h('div', { class: 'comment-list' });
  const tailHost = h('div', {});
  const composerHost = h('div', {});

  const section = h(
    'section',
    { class: 'section' },
    h('h2', { text: '评论' }),
    composerHost,
    statusHost,
    stateHost,
    listEl,
    tailHost
  );

  render(host, section);

  /** Publishes the current comment count to the page. */
  function publishCount() {
    onCountChange?.(count);
  }

  /** Replaces the inline action feedback. */
  function setStatus(message, tone) {
    render(statusHost, message ? notice(message, tone) : null);
  }

  /** Adds a comment to the displayed set and nests it if its parent is present. */
  function insertComment(comment) {
    const record = buildComment(comment);
    records.set(comment.id, record);

    const parent = comment.parentCommentId ? records.get(comment.parentCommentId) : null;
    if (parent && parent.repliesEl.isConnected && parent.comment.status === 'ACTIVE') {
      // Nest under the parent. Replies are ordered by creation time, so
      // appending keeps the backend's (created_at ASC) order.
      parent.repliesEl.append(record.root);
      parent.root.classList.add('comment--has-replies');
    } else {
      // No visible parent: a top-level comment, or a reply whose parent was
      // deleted (the backend filters deleted parents out of this payload).
      listEl.append(record.root);
    }

    if (!anyComments) {
      anyComments = true;
      // A previous "no comments yet" placeholder is now wrong.
      render(stateHost);
    }

    return record;
  }

  /**
   * Removes one comment from the view, exactly as the backend would on reload:
   * the comment goes, its replies stay.
   */
  function detachComment(record) {
    const container = record.root.parentElement ?? listEl;

    // Replies are independent ACTIVE rows: deleting a parent does not delete
    // them, so they must not vanish here. Promote them into the parent's own
    // container so they remain readable and keep their order.
    for (const child of Array.from(record.repliesEl.children)) {
      container.insertBefore(child, record.root);
    }

    record.root.remove();
    records.delete(record.comment.id);
  }

  /* ------------------------------------------------------------ rendering */

  /** Builds one comment's DOM and wires its actions. */
  function buildComment(comment) {
    const record = {
      comment,
      root: null,
      textEl: null,
      editorEl: null,
      repliesEl: null,
      activeEditor: null,
    };

    const deleted = comment.status === 'DELETED';
    const own = !deleted && isOwnComment(comment);

    const authorEl = h('span', { class: 'comment__author' });
    decorateUserLabel(authorEl, comment.userId, { self: '我' });

    const meta = h(
      'div',
      { class: 'comment__meta' },
      authorEl,
      comment.createdAt ? h('span', { text: formatRelative(comment.createdAt) }) : null,
      comment.updatedAt && comment.updatedAt !== comment.createdAt
        ? h('span', { text: '已编辑' })
        : null,
      comment.parentCommentId && !records.has(comment.parentCommentId)
        ? h('span', { class: 'reply-tag', text: '回复' })
        : null,
      deleted ? badge('已删除') : null
    );

    const textEl = h('p', { class: 'comment__text', text: comment.text });
    if (deleted) {
      textEl.classList.add('muted');
      textEl.textContent = '该评论已被删除。';
    }

    const actionsEl = h('div', { class: 'comment__actions' });
    const editorEl = h('div', { class: 'comment__editor' });
    const repliesEl = h('div', { class: 'comment__replies' });

    if (!deleted) {
      actionsEl.append(
        h('button', {
          class: 'linkbtn',
          type: 'button',
          text: '回复',
          onclick: () => openReply(record),
        })
      );

      if (own) {
        actionsEl.append(
          h('button', {
            class: 'linkbtn',
            type: 'button',
            text: '编辑',
            onclick: () => openEdit(record),
          }),
          h('button', {
            class: 'linkbtn linkbtn--danger',
            type: 'button',
            text: '删除',
            onclick: () => removeComment(record),
          })
        );
      }
    }

    const root = h(
      'div',
      { class: 'comment', dataset: { commentId: comment.id } },
      meta,
      textEl,
      actionsEl,
      editorEl,
      repliesEl
    );

    record.root = root;
    record.textEl = textEl;
    record.editorEl = editorEl;
    record.repliesEl = repliesEl;

    return record;
  }

  /* --------------------------------------------------------------- reply */

  /** Opens the inline reply composer for a comment. */
  function openReply(record) {
    if (!auth.isAuthenticated()) {
      setStatus('请登录后再回复。', 'error');
      render(
        composerHost,
        h(
          'div',
          { class: 'notice' },
          h('a', {
            href: `/login.html?next=${encodeURIComponent(currentPath())}`,
            text: '登录',
          }),
          ' 后即可回复。'
        )
      );
      return;
    }

    if (record.activeEditor === 'reply') {
      record.editorEl.querySelector('textarea')?.focus();
      return;
    }

    const composer = createComposer({
      label: '回复',
      placeholder: '写下你的回复…',
      submitLabel: '回复',
      onCancel: () => closeEditor(record),
      onSubmit: async (text) => {
        const created = await api.post(`/contents/${encodeURIComponent(contentId)}/comments`, {
          text,
          parentCommentId: record.comment.id,
        });
        closeEditor(record);
        insertComment(created);
        count += 1;
        publishCount();
        setStatus('回复已发布。', 'success');
      },
    });

    record.activeEditor = 'reply';
    render(record.editorEl, composer.element);
    composer.focus();
  }

  /* ---------------------------------------------------------------- edit */

  /**
   * Swaps a comment's text for an editor holding the ORIGINAL text. The stored
   * text is only replaced after the server confirms the update, so a failed
   * save can never leave the page showing an edit that was not applied.
   */
  function openEdit(record) {
    if (record.activeEditor === 'edit') return;

    const composer = createComposer({
      label: '编辑评论',
      submitLabel: '保存修改',
      initial: record.comment.text,
      onCancel: () => closeEditor(record),
      onSubmit: async (text) => {
        const updated = await api.patch(`/comments/${encodeURIComponent(record.comment.id)}`, {
          text,
        });
        record.comment = {
          ...record.comment,
          text: typeof updated?.text === 'string' ? updated.text : text,
          updatedAt: typeof updated?.updatedAt === 'number' ? updated.updatedAt : record.comment.updatedAt,
        };
        record.textEl.textContent = record.comment.text;
        closeEditor(record);
        refreshMeta(record);
        setStatus('评论已更新。', 'success');
      },
    });

    record.activeEditor = 'edit';
    record.textEl.hidden = true;
    render(record.editorEl, composer.element);
    composer.focus();
  }

  /** Closes whichever editor is open on a comment, restoring display mode. */
  function closeEditor(record) {
    if (record.activeEditor === null) return;
    record.activeEditor = null;
    render(record.editorEl);
    record.textEl.hidden = false;
  }

  /** Re-renders the "edited" marker after a successful edit. */
  function refreshMeta(record) {
    const meta = record.root.querySelector('.comment__meta');
    if (!meta) return;
    const hasEdited = Array.from(meta.children).some((el) => el.textContent === '已编辑');
    if (hasEdited) return;
    meta.append(h('span', { text: '已编辑' }));
  }

  /* -------------------------------------------------------------- delete */

  /**
   * Soft-deletes a comment after an explicit confirmation.
   *
   * The backend answers 204 for a successful delete, a repeat delete, and an
   * already-deleted comment; 403 when the caller is not the owner. Both are
   * honoured: success removes exactly one comment from the view, a refusal
   * changes nothing and says why.
   */
  async function removeComment(record) {
    const confirmed = window.confirm('确定删除该评论？此操作无法撤销。');
    if (!confirmed) return;

    setStatus('');

    try {
      await api.del(`/comments/${encodeURIComponent(record.comment.id)}`);
    } catch (error) {
      setStatus(commentErrorMessage(error), 'error');
      return;
    }

    detachComment(record);
    count = Math.max(0, count - 1);
    publishCount();
    // Replies may have been promoted, so the empty state must be re-evaluated.
    if (records.size === 0 && listEl.childElementCount === 0) {
      anyComments = false;
        render(stateHost, emptyState('暂无评论，来发表第一条评论吧。'));
    }
    setStatus('评论已删除。', 'success');
  }

  /* ---------------------------------------------------------- pagination */

  /** Renders the "Load more" control, or nothing on the last page. */
  function renderTail() {
    if (!nextUrl) {
      render(tailHost);
      return;
    }

    const button = h('button', {
      class: 'btn',
      type: 'button',
      text: '加载更多评论',
      onclick: async () => {
        button.disabled = true;
        button.textContent = '正在加载…';
        render(statusHost);
        try {
          const page = await api.getPageByUrl(nextUrl);
          for (const item of page.items) insertComment(item);
          nextUrl = page.nextUrl;
          renderTail();
        } catch (error) {
          button.disabled = false;
          button.textContent = '重试';
          setStatus(commentErrorMessage(error), 'error');
        }
      },
    });

    render(tailHost, h('div', { class: 'load-more' }, button));
  }

  /** Loads and renders the first page. */
  async function reload() {
    records.clear();
    anyComments = false;
    nextUrl = null;
    render(listEl);
    render(tailHost);
    render(statusHost);
    render(stateHost, loadingState('正在加载评论…'));

    try {
      const page = await api.getPage(`/contents/${encodeURIComponent(contentId)}/comments`, {
        query: { limit: PAGE_SIZE },
      });

      if (page.items.length === 0) {
      render(stateHost, emptyState('暂无评论，来发表第一条评论吧。'));
        return;
      }

      render(stateHost);
      for (const item of page.items) insertComment(item);
      nextUrl = page.nextUrl;
      renderTail();
    } catch (error) {
      render(stateHost, errorState(commentErrorMessage(error)));
    }
  }

  /* ------------------------------------------------------------ composer */

  /** Top-level composer for signed-in users, a login prompt otherwise. */
  function renderComposer() {
    if (!auth.isAuthenticated()) {
      render(
        composerHost,
        h(
          'div',
          { class: 'notice' },
          h('a', {
            href: `/login.html?next=${encodeURIComponent(currentPath())}`,
            text: '登录',
          }),
          ' 后即可参与讨论。'
        )
      );
      return;
    }

    const composer = createComposer({
      label: '发表评论',
      placeholder: '写下你的评论…',
      submitLabel: '发表评论',
      onSubmit: async (text) => {
        const created = await api.post(`/contents/${encodeURIComponent(contentId)}/comments`, {
          text,
          parentCommentId: null,
        });
        insertComment(created);
        count += 1;
        publishCount();
        setStatus('评论已发布。', 'success');
        // The composer intentionally keeps its place and the list keeps its
        // state: posting must not reset unrelated page content.
      },
    });

    render(composerHost, h('div', { class: 'card' }, composer.element));
  }

  renderComposer();
  publishCount();
  await reload();

  return { reload };
}
