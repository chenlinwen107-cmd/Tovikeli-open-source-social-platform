import { useState, useRef, useEffect } from 'react';
import Icon from './Icon';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { fmtNum } from '../lib/format';
import { createInteraction, deleteInteraction, getInteractionState } from '../api/interactions';
import { likeComment, unlikeComment } from '../api/comments';

export interface ReactionDef { key: string; label: string; icon: string; color: string; }

// Restrained SVG reaction set (no emoji) — LinkedIn-style, on-brand
export const REACTIONS: ReactionDef[] = [
  { key: 'like', label: '赞', icon: 'thumbsup', color: '#2b6fff' },
  { key: 'love', label: '爱了', icon: 'heart', color: '#e0245e' },
  { key: 'haha', label: '哈哈', icon: 'smile', color: '#e6a019' },
  { key: 'wow', label: '哇', icon: 'spark', color: '#8b5cf6' },
  { key: 'support', label: '加油', icon: 'fire', color: '#f5703a' },
];
const byKey = (k: string | null | undefined) => REACTIONS.find((r) => r.key === k);

export default function Reactions({ id, target = 'post', initialReaction, initialCount, initialReactions, simple = false }: { id: number; target?: 'post' | 'comment'; initialReaction: string | null; initialCount: number; initialReactions?: Record<string, number> | null; simple?: boolean }) {
  const { user, setAuthOpen } = useAuth();
  const toast = useToast();
  const [mine, setMine] = useState<string | null>(initialReaction);
  const [count, setCount] = useState<number>(initialCount);
  const [open, setOpen] = useState(false);
  const hoverT = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const pressT = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const longPressed = useRef(false);
  const busy = useRef(false); // in-flight 标记：忽略请求未完成前的重复点击（防连点竞态）

  useEffect(() => {
    setMine(initialReaction);
    setCount(initialCount);
  }, [initialReaction, initialCount, initialReactions]);

  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    const storageKey = `tovikeli:reaction:${user.id}:${target}:${id}`;
    const saved = window.localStorage.getItem(storageKey);
    if (saved && byKey(saved)) setMine(saved);

    const loadState = target === 'post'
      ? getInteractionState(String(user.id), String(id)).then((state) => ({
          liked: state.like,
          likeCount: undefined as number | undefined,
        }))
      : import('../api/comments').then(({ getCommentLike }) => getCommentLike(String(id)));

    void loadState.then((state) => {
      if (cancelled) return;
      setCount((value) => typeof state.likeCount === 'number' ? state.likeCount : value);
      if (!state.liked) {
        setMine(null);
        window.localStorage.removeItem(storageKey);
      } else if (saved && byKey(saved)) {
        setMine(saved);
      } else {
        setMine(initialReaction || 'like');
      }
    }).catch(() => {});
    return () => { cancelled = true; };
  }, [user, target, id, initialReaction]);

  useEffect(() => {
    const onInteractionChanged = (event: Event) => {
      const detail = (event as CustomEvent).detail;
      if (String(detail?.contentId ?? detail?.id) !== String(id) || detail?.type !== 'LIKE') return;
      if (typeof detail.liked === 'boolean') {
        setMine(detail.liked ? (mine || 'like') : null);
      }
      if (typeof detail.likeCount === 'number') setCount(detail.likeCount);
    };
    window.addEventListener('tovikeli:interaction-changed', onInteractionChanged);
    return () => window.removeEventListener('tovikeli:interaction-changed', onInteractionChanged);
  }, [id, mine]);

  useEffect(() => {
    const onCommentLikeChanged = (event: Event) => {
      const detail = (event as CustomEvent).detail;
      if (target !== 'comment' || String(detail?.commentId) !== String(id)) return;
      if (typeof detail.liked === 'boolean') setMine(detail.liked ? (mine || 'like') : null);
      if (typeof detail.likeCount === 'number') setCount(detail.likeCount);
    };
    window.addEventListener('tovikeli:comment-like-changed', onCommentLikeChanged);
    return () => window.removeEventListener('tovikeli:comment-like-changed', onCommentLikeChanged);
  }, [target, id, mine]);

  const cur = byKey(mine);


  const react = async (key: string) => {
    if (!user) return setAuthOpen(true);
    setOpen(false);
    if (busy.current) return;
    busy.current = true;
    const prevMine = mine;
    const prevCount = count;
    try {
      if (target === 'post') {
        if (prevMine === key) {
          await deleteInteraction(String(user.id), String(id), 'LIKE');
          setMine(null);
          setCount((v) => Math.max(0, v - 1));
          window.dispatchEvent(new CustomEvent('tovikeli:interaction-changed', { detail: { contentId: String(id), type: 'LIKE', liked: false } }));
          window.localStorage.removeItem(`tovikeli:reaction:${user.id}:${target}:${id}`);
        } else if (!prevMine) {
          await createInteraction(String(user.id), { contentId: String(id), type: 'LIKE' });
          setMine(key);
          setCount((v) => v + 1);
          window.dispatchEvent(new CustomEvent('tovikeli:interaction-changed', { detail: { contentId: String(id), type: 'LIKE', liked: true } }));
          window.localStorage.setItem(`tovikeli:reaction:${user.id}:${target}:${id}`, key);
        } else {
          // The visual reaction is client-side; the backend remains a single LIKE.
          setMine(key);
          window.localStorage.setItem(`tovikeli:reaction:${user.id}:${target}:${id}`, key);
        }
      } else {
        const data = prevMine === key
          ? await unlikeComment(String(id))
          : await likeComment(String(id));
        setMine(data.liked ? key : null);
        setCount(data.likeCount);
        window.dispatchEvent(new CustomEvent('tovikeli:comment-like-changed', { detail: { commentId: String(id), liked: data.liked, likeCount: data.likeCount } }));
        const storageKey = `tovikeli:reaction:${user.id}:${target}:${id}`;
        if (data.liked) window.localStorage.setItem(storageKey, key);
        else window.localStorage.removeItem(storageKey);
      }
    } catch (e) {
      setMine(prevMine);
      setCount(prevCount);
      toast.err((e as Error).message);
    } finally {
      busy.current = false;
    }
  };


  const quick = () => {
    if (longPressed.current) { longPressed.current = false; return; } // ignore the click that ends a long-press
    if (!user) return setAuthOpen(true);
    react(mine || 'like');
  };

  // desktop hover-to-open
  const onEnter = () => { clearTimeout(hoverT.current); hoverT.current = setTimeout(() => setOpen(true), 280); };
  const onLeave = () => { clearTimeout(hoverT.current); hoverT.current = setTimeout(() => setOpen(false), 200); };
  // mobile long-press-to-open
  const onTouchStart = () => { longPressed.current = false; pressT.current = setTimeout(() => { longPressed.current = true; setOpen(true); }, 360); };
  const onTouchEnd = () => { clearTimeout(pressT.current); };

  return (
    <div className="react-wrap" onMouseEnter={onEnter} onMouseLeave={onLeave}>
      {open && (
        <div className="react-picker" onMouseEnter={() => clearTimeout(hoverT.current)} onMouseLeave={onLeave}>
          {REACTIONS.map((r, i) => (
            <button key={r.key} className={`react-opt${mine === r.key ? ' on' : ''}`} title={r.label}
              style={{ '--rc': r.color, animationDelay: `${i * 28}ms` } as React.CSSProperties}
              onClick={() => react(r.key)}>
              <Icon name={r.icon} size={23} fill />
              <span className="react-opt-label">{r.label}</span>
            </button>
          ))}
        </div>
      )}
      <button className={`${simple ? 'clike' : 'act like'}${mine ? ' on' : ''}`} onClick={quick}
        onTouchStart={onTouchStart} onTouchEnd={onTouchEnd}
        style={cur ? { color: cur.color } : undefined}>
        <Icon name={cur ? cur.icon : 'thumbsup'} size={simple ? 13 : 18} fill={!!mine} className="ico" style={simple ? { verticalAlign: '-2px', marginRight: 3 } : undefined} />
        {simple ? '' : ' '}{count > 0 ? fmtNum(count) : (cur ? cur.label : '赞')}
      </button>

    </div>
  );
}
