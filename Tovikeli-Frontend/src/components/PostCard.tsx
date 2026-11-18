import { useState, useRef, lazy, Suspense, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Avatar from './Avatar';
import Icon from './Icon';
import RichText from './RichText';
import RichBody from './RichBody';
import MediaGrid from './MediaGrid';
import Poll from './Poll';
import Reactions from './Reactions';
import Comments from './Comments';
import CollectModal from './CollectModal';
import ShareModal from './postcard/ShareModal';
import EditModal from './postcard/EditModal';
import { Button } from './heroui';
import { useDismiss } from '../lib/useDismiss';
import { onImgError } from '../lib/img';
import { copyText } from '../lib/clipboard';
// 懒加载：分享海报用到 html-to-image + qrcode（较重），且仅在点开「分享海报」时才需要，
// 拆成独立 chunk，从首屏主包移除这两个库。
const SharePoster = lazy(() => import('./SharePoster'));
import UserHoverCard from './UserHoverCard';
import { UserName } from './Identity';
import { useAuth } from '../context/AuthContext';
import { useFollow } from '../hooks/useFollow';
import { useToast } from '../context/ToastContext';
import api from '../api/client';
import { createInteraction, deleteInteraction, getInteractionState } from '../api/interactions';
import { editContent } from '../api/contents';
import type { InteractionType } from '../api/types';
import { confirmDialog } from './confirm';
import { reportDialog } from './report';
import { timeAgo, fmtNum, VIS_LABELS } from '../lib/format';

const FOLD_LEN = 220;

function emojiBio(bio: any) {
  if (bio?.startsWith('emoji:')) return '';
  return bio || '';
}

interface PostCardProps {
  post: any;
  onDelete?: (id: number | string) => void;
  canManage?: boolean;
  [k: string]: any;
}

export default function PostCard({ post: initial, onDelete, canManage = false, defaultOpenComments = false, compact = false }: PostCardProps) {
  const { user, setAuthOpen } = useAuth();
  const toast = useToast();
  const nav = useNavigate();
  const [post, setPost] = useState<any>(initial);
  const [liked, setLiked] = useState(initial.liked);
  const [likeCount, setLikeCount] = useState(initial.likeCount);
  const [commentCount, setCommentCount] = useState(initial.commentCount);
  const [showComments, setShowComments] = useState(defaultOpenComments);
  const [expanded, setExpanded] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  useDismiss(menuOpen, () => setMenuOpen(false), menuRef);
  const [pwd, setPwd] = useState('');
  const [bookmarked, setBookmarked] = useState(initial.bookmarked);
  const bmBusy = useRef(false); // 收藏 in-flight 标记（防连点竞态）
  const [editOpen, setEditOpen] = useState(false);
  const [editText, setEditText] = useState(initial.content || '');
  const [collOpen, setCollOpen] = useState(false);
  const [posterOpen, setPosterOpen] = useState(false);

  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    void getInteractionState(String(user.id), String(post.id)).then((state) => {
      if (cancelled) return;
      setLiked(state.like);
      setBookmarked(state.bookmark);
    }).catch(() => {
      // Feed remains usable when interaction state cannot be refreshed.
    });
    return () => { cancelled = true; };
  }, [user, post.id]);

  useEffect(() => {
    const onInteractionChanged = (event: Event) => {
      const detail = (event as CustomEvent).detail;
      if (String(detail?.contentId ?? detail?.id) !== String(post.id)) return;
      if (detail?.type === 'LIKE' && typeof detail.liked === 'boolean') {
        setLiked(detail.liked);
      }
      if (detail?.type === 'BOOKMARK' && typeof detail.bookmarked === 'boolean') {
        setBookmarked(detail.bookmarked);
      }
    };
    window.addEventListener('tovikeli:interaction-changed', onInteractionChanged);
    return () => window.removeEventListener('tovikeli:interaction-changed', onInteractionChanged);
  }, [post.id]);

  const author = post.author;
  const isAnon = post.visibility === 'anonymous';
  const isOwner = canManage || Boolean(user && author?.sourceAuthorId != null && String(author.sourceAuthorId) === String(user.id));
  const follow = useFollow(author?.sourceAuthorId ? String(author.sourceAuthorId) : null, Boolean(user && !isOwner && !isAnon));
  const long = (post.content || '').length > FOLD_LEN;
  const shown = long && !expanded ? post.content.slice(0, FOLD_LEN) : post.content;

  const requireLogin = () => { if (!user) { setAuthOpen(true); return true; } return false; };

  const remove = async () => {
    setMenuOpen(false);
    if (!(await confirmDialog('删除后不可恢复', { title: '删除这条动态？', confirmText: '删除' }))) return;
    try { await api.post(`/contents/${post.id}/delete`); toast.ok('已删除'); onDelete?.(post.id); window.dispatchEvent(new CustomEvent('tovikeli:content-changed', { detail: { action: 'delete', id: String(post.id) } })); }
    catch (e: any) { toast.err(e.message); }
  };

  const bookmark = async () => {
    if (requireLogin()) return;
    const currentUser = user;
    if (!currentUser) return;
    if (bmBusy.current) return; // in-flight：忽略请求未完成前的重复点击（防连点竞态）
    bmBusy.current = true;
    const nextBookmarked = !bookmarked;
    setBookmarked(nextBookmarked);
    try {
      if (!bookmarked) {
        await createInteraction(String(currentUser.id), { contentId: String(post.id), type: 'BOOKMARK' });
        toast.show('已收藏');
      } else {
        await deleteInteraction(String(currentUser.id), String(post.id), 'BOOKMARK');
        toast.show('已取消收藏');
      }
      window.dispatchEvent(new CustomEvent('tovikeli:interaction-changed', { detail: { contentId: String(post.id), type: 'BOOKMARK', bookmarked: nextBookmarked } }));
    }
    catch (e: any) { setBookmarked(bookmarked); toast.err(e.message); }
    finally { bmBusy.current = false; }
  };

  const report = async () => {
    if (requireLogin()) return;
    setMenuOpen(false);
    const reason = await reportDialog();
    if (reason === null) return;
    try { await api.post('/reports', { targetType: 'post', targetId: post.id, reason }); toast.ok('举报已提交，感谢反馈'); }
    catch (e: any) { toast.err(e.message); }
  };

  const block = async () => {
    if (requireLogin()) return;
    setMenuOpen(false);
    if (!(await confirmDialog('之后将不再看到 TA 的内容', { title: `拉黑 @${author.nickname}？`, confirmText: '拉黑' }))) return;
    try { await api.post(`/users/${author.sourceAuthorId}`, { }); toast.ok('已拉黑'); onDelete?.(post.id); }
    catch (e: any) { toast.err(e.message); }
  };

  const saveEdit = async () => {
    try {
      const updated = await editContent(String(post.id), { text: editText });
      setPost((current: any) => ({ ...current, content: updated.text, updatedAt: updated.updatedAt }));
      setEditOpen(false);
      toast.ok('已更新');
    } catch (e: any) { toast.err(e.message); }
  };

  const openCollect = () => {
    setMenuOpen(false);
    if (!user) return setAuthOpen(true);
    setCollOpen(true);
  };

  const copyLink = async () => {
    setMenuOpen(false);
    const url = `${window.location.origin}/post/${post.id}`;
    if (await copyText(url)) toast.ok('链接已复制'); else toast.show(url);
  };

  return (
    <article className={`ui-card post rise${compact ? ' post-compact' : ''}${menuOpen ? ' menu-open' : ''}`}>
      <div className="post-head">
        <UserHoverCard user={author}><Avatar user={author} size={compact ? 36 : 46} showV /></UserHoverCard>
        <div className="meta">
          <div className="row gap-6">
            <UserHoverCard user={author}><UserName user={author} /></UserHoverCard>
            {VIS_LABELS[post.visibility] && post.visibility !== 'public' && (
              <span className="faint" style={{ fontSize: 12 }} title={VIS_LABELS[post.visibility].label}>
                {VIS_LABELS[post.visibility].icon}
              </span>
            )}
          </div>
          <div className="umeta">
            <span>{timeAgo(post.createdAt)}</span>
            {post.edited && <span className="dot">已编辑</span>}
          </div>
          {user && !isOwner && !isAnon && author?.id && (
            <button
              className={`haha-follow-btn${follow.following ? ' is-following' : ''}`}
              onClick={() => void follow.toggle()}
              disabled={follow.loading}
            >
              {follow.following ? '已关注' : '关注'}
            </button>
          )}
        </div>
        {(!isAnon || canManage) && (
          <div style={{ position: 'relative' }} ref={menuRef}>
            <button className="post-menu" onClick={() => setMenuOpen((m) => !m)} aria-label="更多操作"><Icon name="more" size={18} /></button>
            {menuOpen && (
              <div className="ui-card menu-pop">
                <button className="menu-item" onClick={copyLink}><Icon name="share" size={16} /> 复制链接</button>
                <button className="menu-item" onClick={() => { setMenuOpen(false); setPosterOpen(true); }}><Icon name="image" size={16} /> 分享海报</button>
                <button className="menu-item" onClick={openCollect}><Icon name="grid" size={16} /> 加入专题</button>
                {isOwner ? (
                  <>
                    <button className="menu-item" onClick={() => { setEditText(post.content || ''); setEditOpen(true); setMenuOpen(false); }}><Icon name="edit" size={16} /> 编辑</button>
                    <button className="menu-item danger" onClick={remove}><Icon name="close" size={16} /> 删除帖子</button>
                  </>
                ) : (
                  <>
                    <button className="menu-item" onClick={report}><Icon name="flag" size={16} /> 举报</button>
                    <button className="menu-item danger" onClick={block}><Icon name="ban" size={16} /> 拉黑作者</button>
                  </>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      {/* repost source — a quote card that navigates to the original (no nested interactive els) */}
      {post.shared && (
        <div className="repost" role="link" tabIndex={0}
          onClick={() => nav(`/post/${post.shared.id}`)}
          onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), nav(`/post/${post.shared.id}`))}>
          <div className="row gap-6"><UserName user={post.shared.author} showBadges={false} /></div>
          <div className="post-body" style={{ fontSize: 14 }}><RichText text={(post.shared.content || '').slice(0, 120)} />{(post.shared.content || '').length > 120 ? '…' : ''}</div>
          {post.shared.media?.length > 0 && (
            <div className="repost-media">
              {post.shared.media.slice(0, 3).map((m: any, i: number) => (
                m.type === 'image'
                  ? <img key={i} src={m.url} alt="" loading="lazy" onError={onImgError} />
                  : <span key={i} className="repost-media-ph"><Icon name={m.type === 'video' ? 'video' : 'music'} size={18} /></span>
              ))}
              {post.shared.media.length > 3 && <span className="repost-media-more">+{post.shared.media.length - 3}</span>}
            </div>
          )}
        </div>
      )}
      {post.sharedDeleted && (
        <div className="repost" style={{ color: 'var(--ink-4)', fontStyle: 'italic', cursor: 'default' }}>原动态已删除</div>
      )}

      {shown && (
        <div className="post-body">
          {long && !expanded ? (
            <><RichText text={shown} /> … <span className="post-fulltext" role="button" tabIndex={0} onClick={() => setExpanded(true)} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setExpanded(true); } }}>查看全文</span></>
          ) : (
            <RichBody text={shown} />
          )}
        </div>
      )}

      {post.topic && !(post.content || '').includes(`#${post.topic.name}#`) && (
        <Link to={`/topic/${encodeURIComponent(post.topic.name)}`} className="topic-chip">
          <Icon name="fire" size={13} /> #{post.topic.name}#
        </Link>
      )}

      {post.media?.length > 0 && <MediaGrid media={post.media} />}

      {post.poll && <Poll poll={post.poll} postId={post.id} />}

      {/* context: location + views */}
      {(post.location || post.views > 0) && (
        <div className="post-context">
          {post.location && <span className="pc"><Icon name="location" size={13} /> {post.location}</span>}
          {post.views > 0 && <span className="pc"><Icon name="eye" size={13} /> {fmtNum(post.views)} 浏览</span>}
        </div>
      )}

      <div className="divider" style={{ margin: '14px 0 4px' }} />
      <div className="post-actions">
        <Reactions id={post.id} initialReaction={post.myReaction ?? (liked ? 'like' : null)} initialCount={likeCount} initialReactions={post.reactions} />
        <button className="act act-comment" onClick={() => setShowComments((s: boolean) => !s)}>
          <Icon name="comment" size={18} className="ico" /> {commentCount > 0 ? fmtNum(commentCount) : '评论'}
        </button>
        <button className="act share" onClick={() => (requireLogin() ? null : setShareOpen(true))}>
          <Icon name="share" size={17} className="ico" /> {post.shareCount > 0 ? fmtNum(post.shareCount) : '转发'}
        </button>
        <button className={`act bookmark${bookmarked ? ' on' : ''}`} onClick={bookmark} title="收藏">
          <Icon name="bookmark" size={17} fill={bookmarked} className="ico" /> {bookmarked ? '已收藏' : '收藏'}
        </button>
      </div>

      {showComments && (
        <Comments postId={post.id} onCountChange={(d: any) => setCommentCount((c: any) => c + d)} />
      )}

      <CollectModal open={collOpen} onClose={() => setCollOpen(false)} targetType="post" targetId={post.id} />
      {posterOpen && <Suspense fallback={null}><SharePoster open={posterOpen} onClose={() => setPosterOpen(false)} post={post} /></Suspense>}

      <ShareModal open={shareOpen} onClose={() => setShareOpen(false)} post={post} />
      <EditModal open={editOpen} onClose={() => setEditOpen(false)} value={editText} onChange={setEditText} onSave={saveEdit} />
    </article>
  );
}
