import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Shell from '../components/Shell';
import Avatar from '../components/Avatar';
import Icon from '../components/Icon';
import { UserName } from '../components/Identity';
import { Empty, Loading, ChatListSkeleton } from '../components/States';
import { Button } from '../components/heroui';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { listConversations, listMessages, markConversationRead, sendMessage } from '../api/messaging';
import type { DirectConversation, DirectMessage, DirectUser } from '../api/messaging';
import type { PublicUser } from '../types';
import { clockTime, timeAgo } from '../lib/format';
import EmojiPanel from '../components/composer/EmojiPanel';

type Peer = PublicUser;
type ConversationView = DirectConversation & { peer: Peer };

function toPeer(user: DirectUser): Peer {
  return {
    id: user.id,
    username: user.username,
    nickname: user.displayName || user.username,
    avatar: user.avatar || undefined,
  };
}

export default function Messages() {
  const { peerId: conversationId } = useParams();
  const nav = useNavigate();
  const { user, loading: authLoading, setAuthOpen } = useAuth();
  const toast = useToast();

  const [convos, setConvos] = useState<ConversationView[]>([]);
  const [active, setActive] = useState<{ conversation: DirectConversation; peer: Peer; messages: DirectMessage[] } | null>(null);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [showEmoji, setShowEmoji] = useState(false);
  const [loadingList, setLoadingList] = useState(true);
  const endRef = useRef<HTMLDivElement | null>(null);
  const textRef = useRef<HTMLTextAreaElement | null>(null);

  useEffect(() => {
    if (!authLoading && !user) setAuthOpen(true);
  }, [authLoading, user, setAuthOpen]);

  const loadConvos = async () => {
    try {
      const data = await listConversations();
      setConvos(data.map((c) => ({ ...c, peer: toPeer(c.user) })));
    } catch (err: any) {
      toast.err(err.message);
    } finally {
      setLoadingList(false);
    }
  };

  useEffect(() => {
    if (user) loadConvos();
  }, [user?.id]);

  const openChat = async (id: string) => {
    try {
      const conversation = convos.find((c) => c.id === id);
      if (!conversation) {
        const data = await listConversations();
        const found = data.find((c) => c.id === id);
        if (!found) throw new Error('会话不存在或已删除');
        const peer = toPeer(found.user);
        const messages = await listMessages(id);
        setActive({ conversation: found, peer, messages });
      } else {
        const messages = await listMessages(id);
        setActive({ conversation, peer: conversation.peer, messages });
      }
      await markConversationRead(id);
      setConvos((cs) => cs.map((c) => c.id === id ? { ...c, unreadCount: 0 } : c));
      setTimeout(() => endRef.current?.scrollIntoView(), 50);
    } catch (err: any) {
      toast.err(err.message);
      nav('/messages', { replace: true });
    }
  };

  useEffect(() => {
    if (conversationId && user && !loadingList) openChat(conversationId);
    else if (!conversationId) setActive(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversationId, user?.id, loadingList]);

  useEffect(() => {
    const el = textRef.current;
    if (el) {
      el.style.height = 'auto';
      el.style.height = Math.min(el.scrollHeight, 120) + 'px';
    }
  }, [text]);

  const pushMessage = (message: DirectMessage) => {
    setActive((a) => a ? { ...a, messages: [...a.messages, message] } : a);
    setTimeout(() => endRef.current?.scrollIntoView({ behavior: 'smooth' }), 30);
    loadConvos();
  };

  const send = async () => {
    if (!text.trim() || !active || sending) return;
    setSending(true);
    try {
      const message = await sendMessage(active.conversation.id, text.trim());
      pushMessage(message);
      setText('');
    } catch (err: any) {
      toast.err(err.message);
    } finally {
      setSending(false);
    }
  };

  if (authLoading) return <Shell right={false}><Loading /></Shell>;
  if (!user) return <Shell right={false}><div className="ui-card"><Empty icon="🔒" text="登录后查看私信" /></div></Shell>;

  return (
    <Shell wide>
      <div className="ui-card page-title" style={{ marginBottom: 0, borderBottomLeftRadius: 0, borderBottomRightRadius: 0 }}>
        <Icon name="mail" size={20} /> 私信
      </div>
      <div className={`ui-card chat-shell${active ? ' has-active' : ''}`} style={{ borderTopLeftRadius: 0, borderTopRightRadius: 0, marginTop: -1 }}>
        <div className="chat-list">
          {loadingList ? <ChatListSkeleton /> : convos.length === 0 ? (
            <div style={{ padding: 24 }}>
              <Empty icon="💬" text="还没有会话">
                <Button size="sm" color="primary" className="haha-btn-app" onClick={() => nav('/search')}>找人聊聊</Button>
              </Empty>
            </div>
          ) : convos.map((c) => (
            <div key={c.id} className={`chat-list-item${active?.conversation.id === c.id ? ' active' : ''}`} onClick={() => nav(`/messages/${c.id}`)}>
              <Avatar user={c.peer} size={46} showV />
              <div className="chat-li-main">
                <div className="chat-li-name">
                  <span className="chat-li-nm">{c.peer.nickname}</span>
                  <span className="t">{timeAgo(c.lastMessage?.createdAt)}</span>
                </div>
                <div className="chat-li-preview nowrap">
                  {c.lastMessage?.body || '暂无消息'}
                </div>
              </div>
              {c.unreadCount > 0 && <span className="nav-dot" style={{ position: 'static', border: 'none' }}>{c.unreadCount}</span>}
            </div>
          ))}
        </div>

        <div className="chat-window">
          {!active ? (
            <div className="center" style={{ flex: 1, flexDirection: 'column', color: 'var(--ink-4)', gap: 12 }}>
              <span className="empty-ico-tile"><Icon name="mail" size={28} /></span>
              <div>{convos.length === 0 ? '搜索用户并开启第一段对话' : '选择一个会话开始聊天'}</div>
            </div>
          ) : (
            <>
              <div className="chat-header">
                <button className="back-btn chat-back" onClick={() => { setActive(null); nav('/messages'); }} aria-label="返回会话列表"><Icon name="back" size={19} /></button>
                <Avatar user={active.peer} size={34} showV />
                <UserName user={active.peer} />
              </div>
              <div className="chat-msgs">
                {active.messages.map((m) => {
                  const mine = String(m.senderUserId) === String(user.id);
                  return (
                    <div key={m.id}>
                      <div className={`bubble-row ${mine ? 'me' : 'them'}`}>
                        {!mine && <Avatar user={active.peer} size={32} />}
                        <div className="bubble">{m.body}</div>
                        <span className="bubble-time">{clockTime(m.createdAt)}</span>
                      </div>
                    </div>
                  );
                })}
                <div ref={endRef} />
              </div>
              <div className="chat-input">
                <div style={{ position: 'relative' }}>
                  <button
                    className="chat-img-btn"
                    onClick={() => setShowEmoji((current) => !current)}
                    aria-label="表情"
                    title="表情"
                  >
                    <Icon name="smile" size={20} />
                  </button>
                  {showEmoji && <EmojiPanel onPick={(emoji) => {
                    setText((current) => current + emoji);
                    setShowEmoji(false);
                    textRef.current?.focus();
                  }} />}
                </div>
                <textarea ref={textRef} maxLength={5000} rows={1} value={text} onChange={(e) => setText(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } }}
                  placeholder="输入消息…" />
                <Button color="primary" className="haha-btn-app" onClick={send} isDisabled={!text.trim() || sending}>发送</Button>
              </div>
            </>
          )}
        </div>
      </div>
    </Shell>
  );
}
