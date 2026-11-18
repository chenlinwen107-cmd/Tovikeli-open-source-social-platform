import { useEffect, useState } from 'react';
import { useFeed } from '../hooks/useFeed';
import type { FeedType } from '../lib/feed/types';
import { FEED_TABS } from '../lib/feed/registry';
import Shell from '../components/Shell';
import SiteNotice from '../components/SiteNotice';
import PostCard from '../components/PostCard';
import { PostSkeleton, Empty, LoadError } from '../components/States';
import { WhoToFollow } from '../components/Widgets';
import { useAuth } from '../context/AuthContext';
import { useSite } from '../context/SiteContext';
import { Tabs, Tab, Button } from '../components/heroui';
import { normalizeFeedLayout } from '../lib/feedLayout';

export default function Home() {
  const { user, setAuthOpen, isGuest } = useAuth();
  const { homeTabs, feedLayout, guestFeedLimit } = useSite();
  const layout = normalizeFeedLayout(feedLayout);
  const isWaterfall = layout === 'waterfall';
  // 站长可在后台隐藏可选信息流 tab（视频/同城/关注）；核心 tab（推荐/最新）恒显
  const tabs = FEED_TABS.filter((f) => homeTabs[f.key] !== false);
  const [filter, setFilter] = useState<FeedType>('recommend');
  const [deletedIds, setDeletedIds] = useState<Set<string>>(() => new Set());

  const {
    items: posts,
    loading,
    error,
    statusCode,
    hasMore,
    loadMore,
    reload,
  } = useFeed(filter, true);

  useEffect(() => {
    const onContentChanged = (event: Event) => {
      const detail = (event as CustomEvent<{ action?: string; id?: number }>).detail;
      if (detail?.action === 'delete' && detail.id != null) {
        setDeletedIds((current) => new Set(current).add(String(detail.id)));
        return;
      }
      if (detail?.action === 'publish') {
        void reload();
      }
    };
    window.addEventListener('tovikeli:content-changed', onContentChanged);
    return () => window.removeEventListener('tovikeli:content-changed', onContentChanged);
  }, [reload]);

  const onFilter = (next: FeedType) => {
    const tab = tabs.find((item) => item.key === next);
    if (tab?.requiresAuth && !user) {
      setAuthOpen(true);
      return;
    }
    setFilter(next);
  };

  const onDelete = (id: number | string) => {
    setDeletedIds((current) => new Set(current).add(String(id)));
  };

  const visiblePosts = posts.filter((post) => !deletedIds.has(String(post.id)));

  return (
    <Shell right={isWaterfall ? false : undefined} wide={isWaterfall || undefined}>
      <SiteNotice />

      <Tabs aria-label="动态筛选" className="ui-card haha-feed-tabs"
        selectedKey={filter} onSelectionChange={(k: any) => { onFilter(k as FeedType) }}>
        {tabs.map((f) => <Tab key={f.key} title={f.label} />)}
      </Tabs>

      {loading ? (
        <>{[1, 2, 3].map((i) => <PostSkeleton key={i} />)}</>
      ) : error ? (
        statusCode === 501 ? (
          <div className="ui-card">
            <Empty icon="🛠️" text={filter === 'recommend' ? '推荐频道正在建设中' : '这个动态频道暂未开放'} />
          </div>
        ) : statusCode === 401 || statusCode === 403 ? (
          <div className="ui-card">
            <Empty icon="🔒" text="请登录后查看这个动态频道" />
            <div style={{ display: 'flex', justifyContent: 'center', paddingBottom: 20 }}>
              <Button color="primary" className="haha-btn-app" onClick={() => setAuthOpen(true)}>登录 / 注册</Button>
            </div>
          </div>
        ) : (
          <div className="ui-card"><LoadError onRetry={reload} /></div>
        )
      ) : visiblePosts.length === 0 ? (
        <>
          <div className="ui-card"><Empty icon={filter === 'following' ? '👀' : '🍃'} text={
            filter === 'following' ? '关注更多有趣的人，这里会出现他们的动态' :
            filter === 'video' ? '还没有视频动态' : '还没有动态，来发布第一条吧'
          } /></div>
          {filter === 'following' && <WhoToFollow />}
        </>
      ) : (
        <>
          <div className={isWaterfall ? 'feed-waterfall' : 'feed-list'}>
            {(isGuest && guestFeedLimit > 0 ? visiblePosts.slice(0, guestFeedLimit) : visiblePosts).map((p) => (
              <div key={p.id} className={isWaterfall ? 'feed-waterfall-item' : undefined}>
                <PostCard post={p} onDelete={onDelete} compact={isWaterfall} />
              </div>
            ))}
          </div>
          {isGuest && guestFeedLimit > 0 && posts.length >= guestFeedLimit ? (
            <div className="ui-card guest-wall">
              <div className="guest-wall-title">注册解锁全部内容</div>
              <div className="guest-wall-sub">登录后可查看更多动态，并发帖、评论、参与互动。</div>
              <Button color="primary" className="haha-btn-app" onClick={() => setAuthOpen(true)}>登录 / 注册</Button>
            </div>
          ) : (
            <>
              <div
                ref={(node) => {
                  if (!node || !hasMore || loading) return;

                  const observer = new IntersectionObserver(
                    entries => {
                      if (entries[0]?.isIntersecting) {
                        loadMore();
                      }
                    },
                    { rootMargin: '400px' }
                  );

                  observer.observe(node);

                  return () => observer.disconnect();
                }}
              />
              {hasMore && <PostSkeleton />}
              {!hasMore && <div className="empty" style={{ padding: '24px 0', fontSize: 13 }}>· 没有更多了 ·</div>}
            </>
          )}
        </>
      )}
    </Shell>
  );
}
