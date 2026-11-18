import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import Shell from '../components/Shell';
import { useDismiss } from '../lib/useDismiss';
import Avatar from '../components/Avatar';
import Icon from '../components/Icon';
import PostCard from '../components/PostCard';
import FollowButton from '../components/FollowButton';
import ThreadRow from '../components/ThreadRow';
import { Badges } from '../components/Identity';
import { Loading, Empty, ProfileSkeleton, PostSkeleton, ListEnd, LoadError } from '../components/States';
import { Button } from '../components/heroui';
import useInfiniteScroll from '../hooks/useInfiniteScroll';
import { TrendingSearch, Footer } from '../components/Widgets';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useCompose } from '../context/ComposeContext';
import { useSite, widgetOn } from '../context/SiteContext';
import api from '../api/client';
import { getMyInteractionHistory, getUser, getUserByUsername } from '../api/users';
import { getFollowerUsers, getFollowingUsers } from '../api/follows';
import { getAuthor, getAuthorContents, getLocalAuthorByUserId } from '../api/authors';
import { adaptContentToPost } from '../lib/contentPostAdapter';
import { getContent, getPollBatch } from '../api/contents';
import { getMediaBatch } from '../api/media';
import { confirmDialog } from '../components/confirm';
import { reportDialog } from '../components/report';
import { fmtNum, GENDER, timeAgo } from '../lib/format';

function UserList({ userId, rel, isMe }: { userId: string; rel: string; isMe: boolean }) {
  const nav = useNavigate();
  const [users, setUsers] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setFailed(false);
    const loader = rel === 'followers' ? getFollowerUsers : getFollowingUsers;
    void loader(String(userId), 50)
      .then((items) => {
        if (!cancelled) {
          setUsers(items.map((u) => ({
            ...u,
            nickname: u.displayName || u.username,
            followers: 0,
          })));
        }
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [userId, rel]);

  if (loading) return <Loading />;
  if (failed) return <LoadError onRetry={() => window.location.reload()} />;
  if (!users.length) return (
    <Empty icon="👥" text={rel === 'followers' ? '还没有粉丝' : '还没有关注任何人'}>
      {isMe && rel === 'following' && <Button size="sm" color="primary" className="haha-btn-app" onClick={() => nav('/discover')}>去发现感兴趣的人</Button>}
    </Empty>
  );

  return (
    <div style={{ padding: '4px 18px' }}>
      {users.map((u: any) => (
        <div className="user-row" key={u.id} style={{ borderTop: '1px solid var(--line)' }}>
          <Avatar user={u} size={46} showV />
          <div className="meta nowrap">
            <Link to={`/u/${u.username}`} className="nm uname" style={{ fontSize: 15 }}>
              {u.nickname} <Badges user={u} showTitle={false} />
            </Link>
            <div className="sub nowrap">
              {u.bio && !u.bio.startsWith('emoji:') ? u.bio : u.username}
            </div>
          </div>
          <FollowButton user={u} />
        </div>
      ))}
    </div>
  );
}

export default function Profile() {
  const { username } = useParams();
  const nav = useNavigate();
  const { user: me, setAuthOpen } = useAuth();
  const toast = useToast();
  const { openCompose } = useCompose();
  const { widgets } = useSite();
  const [user, setUser] = useState<any>(null);
  const [error, setError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [tab, setTab] = useState('posts');
  const [loading, setLoading] = useState(true);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  useDismiss(menuOpen, () => setMenuOpen(false), menuRef);
  const [threads, setThreads] = useState<any>(null);
  const [visitors, setVisitors] = useState<any[]>([]);
  const [visitorTotal, setVisitorTotal] = useState(0);

  // 动态 / 赞过两列 C 端信息流统一走 useInfiniteScroll（对齐 Home：按窗口推进 offset、
  // 代次丢弃在途竞态、底部哨兵自动续拉），删掉手写「加载更多」。切用户 / 重试自动重置。
  const localAuthorRef = useRef<any>(null);
  const authorCursorRef = useRef<string | null>(null);

  const { items: posts, loading: postsLoading, hasMore: postsMore, sentinelRef: postsSentinel, setItems: setPosts } = useInfiniteScroll<any>(
    async (offset, limit) => {
      if (!user?.id) {
        return { items: [], hasMore: false };
      }

      if (offset === 0) {
        authorCursorRef.current = null;
        localAuthorRef.current = null;
      }

      if (!localAuthorRef.current) {
        localAuthorRef.current = await getLocalAuthorByUserId(String(user.id));
      }

      const page = await getAuthorContents(
        localAuthorRef.current.id,
        {
          limit,
          cursor: authorCursorRef.current ?? undefined,
        },
      );

      authorCursorRef.current = page.nextCursor ?? null;

      const posts = page.items.map((content) =>
        adaptContentToPost(content, localAuthorRef.current),
      );

      return {
        items: posts,
        hasMore: !!page.nextCursor,
      };
    },
    [username, reloadKey, user?.id],
    20,
  );
  const { items: likedPosts, loading: likedLoading, hasMore: likedMore, sentinelRef: likedSentinel } = useInfiniteScroll<any>(
    async (offset, limit) => {
      if (!me || me.username !== username) {
        return { items: [], hasMore: false };
      }
      const pageIndex = Math.floor(offset / limit);
      let cursor: string | undefined;
      let page = { items: [] as any[], nextCursor: null as string | null };
      for (let i = 0; i <= pageIndex; i++) {
        page = await getMyInteractionHistory(String(me.id), 'LIKE', limit, cursor);
        cursor = page.nextCursor ?? undefined;
        if (!cursor && i < pageIndex) break;
      }
      const contentIds = page.items.map((item) => item.contentId);
      if (contentIds.length === 0) return { items: [], hasMore: false };
      const contents = await Promise.all(contentIds.map(async (id) => {
        try { return await getContent(id); } catch { return null; }
      }));
      const valid = contents.filter((item): item is NonNullable<typeof item> => item !== null);
      const authorIds = [...new Set(valid.map((item) => item.authorId).filter((id): id is string => Boolean(id)))];
      const authors = await Promise.all(authorIds.map(async (id) => {
        try { return [id, await getAuthor(id)] as const; } catch { return [id, null] as const; }
      }));
      const authorMap = new Map(authors.filter((x): x is [string, any] => Boolean(x[1])));
      const mediaIds = [...new Set(valid.flatMap((item) => item.mediaIds ?? []))];
      let mediaById = new Map<string, any>();
      if (mediaIds.length) {
        try { mediaById = new Map((await getMediaBatch(mediaIds)).map((m) => [m.id, m])); } catch {}
      }
      let pollByContentId = new Map<string, any>();
      try { pollByContentId = new Map(Object.entries(await getPollBatch(contentIds))); } catch {}
      return {
        items: valid.map((content) =>
          adaptContentToPost(content, content.authorId ? authorMap.get(content.authorId) ?? null : null, mediaById, pollByContentId),
        ),
        hasMore: Boolean(page.nextCursor),
      };
    },
    [username, reloadKey, me?.id, me?.username],
    20,
  );

  const loadProfile = async () => {
    try {
      const data = me && username === me.username
        ? await getUser(String(me.id))
        : await getUserByUsername(String(username));

      let stats = { postCount: 0, following: 0, followers: 0 };
      try {
        const response = await api.get(`/users/${encodeURIComponent(data.id)}/stats`);
        stats = {
          postCount: response.data.postCount ?? 0,
          following: response.data.following ?? 0,
          followers: response.data.followers ?? 0,
        };
      } catch {
        // Profile existence must not depend on the optional statistics endpoint.
      }

      setUser({
        id: data.id,
        username: data.username,
        nickname: data.displayName || data.username,
        avatar: data.avatar || '',
        bio: data.bio || '',
        gender: data.gender || 'secret',
        location: data.location || '',
        cover: data.cover || '',
        role: 'user',
        verified: false,
        postCount: 0,
        following: stats.following,
        followers: stats.followers,
      });
    } catch (e: any) {
      setUser(null);
      if (e?.status !== 404) setError(true);
    }
  };
  useEffect(() => {
    const onFollowChanged = (event: Event) => {
      const detail = (event as CustomEvent).detail;
      if (String(detail?.userId) !== String(user?.id)) return;
      setUser((current: any) => current ? {
        ...current,
        followers: Math.max(0, Number(current.followers || 0) + (detail.following ? 1 : -1)),
      } : current);
    };
    window.addEventListener('tovikeli:follow-changed', onFollowChanged);
    return () => window.removeEventListener('tovikeli:follow-changed', onFollowChanged);
  }, [user?.id]);

  useEffect(() => {
    setLoading(true); setError(false); setTab('posts'); setThreads(null);
    loadProfile().finally(() => setLoading(false));
  }, [username, reloadKey, me?.id, me?.username]);

  useEffect(() => {
    if (tab === 'threads' && threads === null)
      api.get(`/forum/threads/user/${username}`).then(({ data }) => setThreads(data.threads)).catch(() => setThreads([]));
  }, [tab, username, threads]);

  // 最近访客 — only the profile owner sees who's visited
  useEffect(() => {
    setVisitors([]); setVisitorTotal(0);
    if (!user?.id || me?.id !== user.id) return;
    api.get(`/users/${user.username}/visitors`)
      .then(({ data }) => { setVisitors(data.visitors || []); setVisitorTotal(data.total || 0); })
      .catch(() => {});
  }, [user?.id, me?.id, user?.username]);

  if (loading) return <Shell right={false}><ProfileSkeleton /></Shell>;
  if (error) return <Shell right={false}><div className="ui-card"><LoadError onRetry={() => setReloadKey((k) => k + 1)} /></div></Shell>;
  if (!user) return <Shell right={false}><div className="ui-card"><Empty icon="🔍" text="用户不存在" /></div></Shell>;

  const isMe = me?.id === user.id;
  const cover = user.cover && !user.cover.startsWith('emoji') ? { backgroundImage: `url(${user.cover})` } : {};

  const blockUser = async () => {
    setMenuOpen(false);
    if (!(await confirmDialog('之后将不再看到 TA 的内容', { title: `拉黑 @${user.nickname}？`, confirmText: '拉黑' }))) return;
    try { await api.post(`/users/${user.id}/block`); toast.ok('已拉黑'); setUser((u: any) => ({ ...u, isFollowing: false })); }
    catch (e: any) { toast.err(e.message); }
  };
  const reportUser = async () => {
    setMenuOpen(false);
    const reason = await reportDialog();
    if (reason === null) return;
    try { await api.post('/reports', { targetType: 'user', targetId: user.id, reason }); toast.ok('举报已提交，感谢反馈'); }
    catch (e: any) { toast.err(e.message); }
  };
  const right = (
    <>
      {widgetOn(widgets, 'trending') && <TrendingSearch />}
      <Footer />
    </>
  );

  return (
    <Shell right={right}>
      <div className="ui-card profile-hero">
        <div className="profile-cover" style={cover} />
        <div className="profile-main">
          <div className="profile-top">
            <div className="profile-avatar"><Avatar user={user} size={88} showV /></div>
            <div className="profile-id">
              <div className="profile-name">
                <span className="pname-text">{user.nickname}</span>
                {user.gender && GENDER[user.gender] && <span style={{ fontSize: 16, color: user.gender === 'female' ? '#f06595' : '#4c6ef5' }}>{GENDER[user.gender]}</span>}
                <Badges user={user} />
              </div>
              <div className="profile-handle">@{user.username}</div>
            </div>
            <div className="row gap-8" style={{ paddingBottom: 4 }}>
              {isMe ? (
                <Button variant="bordered" className="haha-btn-app" onClick={() => nav('/settings')}><Icon name="edit" size={15} style={{ width: 15, height: 15 }} /> 编辑资料</Button>
              ) : (
                <>
                  <Button variant="flat" className="haha-btn-app" onClick={async () => {
                    try {
                      const { createOrGetConversation } = await import('../api/messaging');
                      const conversationId = await createOrGetConversation(String(user.id));
                      nav(`/messages/${conversationId}`);
                    } catch (err: any) {
                      toast.err(err.message);
                    }
                  }}><Icon name="mail" size={15} style={{ width: 15, height: 15 }} /> 私信</Button>
                  <FollowButton user={user} />
                  {me && (
                    <div style={{ position: 'relative' }} ref={menuRef}>
                      <button className="post-menu" onClick={() => setMenuOpen((m) => !m)} aria-label="更多操作"><Icon name="more" size={18} /></button>
                      {menuOpen && (
                        <div className="ui-card menu-pop">
                          <button className="menu-item" onClick={reportUser}><Icon name="flag" size={16} /> 举报</button>
                          <button className="menu-item danger" onClick={blockUser}><Icon name="ban" size={16} /> 拉黑</button>
                        </div>
                      )}
                    </div>
                  )}
                </>
              )}
            </div>
          </div>

          {!user.bio?.startsWith('emoji:') && user.bio && <div className="profile-bio">{user.bio}</div>}
          <div className="profile-tags">
            {user.location && <span className="pt"><Icon name="location" size={14} /> {user.location}</span>}
            {user.verified && <span className="pt"><Icon name="check" size={14} style={{ color: 'var(--verify)' }} /> {user.verifiedNote || '认证用户'}</span>}
          </div>

          <div className="profile-stats">
            <button className="pstat" onClick={() => setTab('following')}><b>{fmtNum(user.following)}</b><span>关注</span></button>
            <button className="pstat" onClick={() => setTab('followers')}><b>{fmtNum(user.followers)}</b><span>粉丝</span></button>
          </div>
          </div>
        </div>

      {isMe && visitors.length > 0 && (
        <div className="ui-card pv-card">
          <div className="pv-head">
            <span className="pv-title"><Icon name="eye" size={16} /> 最近访客 <span className="pv-n">{visitorTotal}</span></span>
          </div>
          <div className="pv-list">
            {visitors.slice(0, 12).map((v) => (
              <Link key={v.id} to={`/u/${v.username}`} className="pv-item" title={`${v.nickname} · ${timeAgo(v.visitedAt)}看过`}>
                <Avatar user={v} size={44} showV />
                <span className="pv-name nowrap">{v.nickname}</span>
              </Link>
            ))}
          </div>
        </div>
      )}

      <div className="ui-card" style={{ overflow: 'hidden' }}>
        <div className="subtabs">
          <button className={`subtab${tab === 'posts' ? ' active' : ''}`} onClick={() => setTab('posts')}>动态</button>
          <button className={`subtab${tab === 'threads' ? ' active' : ''}`} onClick={() => setTab('threads')}>帖子</button>
          <button className={`subtab${tab === 'liked' ? ' active' : ''}`} onClick={() => setTab('liked')}>赞过</button>
          <button className={`subtab${tab === 'following' ? ' active' : ''}`} onClick={() => setTab('following')}>关注 {user.following}</button>
          <button className={`subtab${tab === 'followers' ? ' active' : ''}`} onClick={() => setTab('followers')}>粉丝 {user.followers}</button>
        </div>
        {(tab === 'following' || tab === 'followers') && <UserList userId={String(user.id)} rel={tab} isMe={isMe} />}
        {tab === 'threads' && (threads === null ? <Loading />
          : threads.length === 0 ? <Empty icon="📋" text={isMe ? '你还没有发过帖子' : 'TA 还没有发过帖子'}>{isMe && <Button size="sm" color="primary" className="haha-btn-app" onClick={() => nav('/forum')}>去论坛发帖</Button>}</Empty>
          : threads.map((t: any, i: number) => <div key={t.id}>{i > 0 && <div className="divider" />}<ThreadRow thread={t} /></div>))}
      </div>

      {tab === 'posts' && (posts.length === 0 ? <div className="ui-card"><Empty icon="✍️" text={isMe ? '你还没有发布动态' : 'TA 还没有发布动态'}>
        {isMe && <Button size="sm" color="primary" className="haha-btn-app" onClick={openCompose}><Icon name="edit" size={14} style={{ width: 14, height: 14 }} /> 发布第一条动态</Button>}
      </Empty></div>
        : <>
          {posts.map((p: any) => <PostCard key={p.id} post={p} canManage={isMe} onDelete={(id) => setPosts((x) => x.filter((y) => String(y.id) !== String(id)))} />)}
          <div ref={postsSentinel} />
          {postsMore && <PostSkeleton />}
          {!postsMore && <ListEnd />}
        </>)}

      {tab === 'liked' && (likedLoading ? <Loading />
        : likedPosts.length === 0 ? <div className="ui-card"><Empty icon="❤️" text={isMe ? '你还没有赞过动态' : 'TA 还没有公开的点赞'}>{isMe && <Button size="sm" color="primary" className="haha-btn-app" onClick={() => nav('/discover')}>去发现好内容</Button>}</Empty></div>
        : <>
          {likedPosts.map((p: any) => <PostCard key={p.id} post={p} />)}
          <div ref={likedSentinel} />
          {likedMore && <PostSkeleton />}
          {!likedMore && <ListEnd />}
        </>)}
    </Shell>
  );
}
