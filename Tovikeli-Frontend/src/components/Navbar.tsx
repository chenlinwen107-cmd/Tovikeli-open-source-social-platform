import { useState, useEffect, useRef } from 'react';
import { NavLink, Link, useNavigate, useLocation } from 'react-router-dom';
import Avatar from './Avatar';
import Icon from './Icon';
import ThemeSwitcher from './ThemeSwitcher';
import MobileDrawer from './MobileDrawer';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useTheme } from '../context/ThemeContext';
import { useSite, moduleOn } from '../context/SiteContext';
import { Button } from './heroui';
import api from '../api/client';

export function BrandMark({ size = 33, logo }: { size?: number; logo?: string }) {
  if (logo) {
    return <img className="brand-mark brand-mark-img" src={logo} alt=""
      style={{ height: size, width: 'auto', ['--brand-logo-h' as string]: `${size}px` }} aria-hidden />;
  }
  return (
    <span
      className="brand-mark brand-mark-tovikeli"
      style={{ width: size, height: size, color: 'var(--brand)' }}
      aria-hidden
    >
      <svg viewBox="0 0 32 32" width={size} height={size} fill="none" aria-hidden="true">
        <defs>
          <linearGradient id="tovikeli-mark-gradient" x1="4" y1="4" x2="28" y2="28" gradientUnits="userSpaceOnUse">
            <stop offset="0" stopColor="var(--brand)" />
            <stop offset="1" stopColor="color-mix(in srgb, var(--brand) 45%, white)" />
          </linearGradient>
        </defs>
        <path
          fill="url(#tovikeli-mark-gradient)"
          d="M5.5 3.8h18.2c1.9 0 2.9 1.7 1.9 3.2l-1.7 2.3c-.5.8-1.3 1.2-2.2 1.2H3.6c-1.9 0-2.9-1.7-1.9-3.2l1.7-2.3c.5-.8 1.2-1.2 2.1-1.2Z"
        />
        <path
          fill="url(#tovikeli-mark-gradient)"
          d="M8.7 12.8c1.5 0 2.8.8 3.6 2l4.2 6.8 4.5-7.3c.8-1.2 2-1.9 3.4-1.9h.8c2.2 0 3.5 2.4 2.3 4.2l-6.7 10.4c-1.9 2.9-6.2 2.9-8.1 0L6.1 16.7c-1.3-1.9.1-3.9 2.6-3.9Z"
        />
      </svg>
    </span>
  );
}

// 站名渲染：Tovikeli 等自定义品牌整体高亮；兼容旧的 *SNS 双色站名。
export function BrandName({ name }: { name: string }) {
  const m = name.match(/^(.*)(SNS)$/i);
  if (m && m[1]) {
    return <span className="brand-name"><b>{m[1]}</b><span>{m[2]}</span></span>;
  }
  return <span className={`brand-name${/^Tovikeli$/i.test(name) ? " brand-name-tovikeli" : ""}`}><b>{name}</b></span>;
}

/** LOGO 高度 clamp 到 [24,64]，缺省 33。纯函数便于单测。 */
export function logoHeightOf(h?: number | null): number {
  const n = Number(h);
  if (!Number.isFinite(n)) return 33;
  return Math.max(24, Math.min(64, Math.round(n)));
}

/** 有 logo 且 logoOnly 时只显图；否则 LOGO+文字。 */
export function showBrandText(logo: string | undefined, logoOnly: boolean | undefined): boolean {
  return !(logoOnly && !!logo);
}

export default function Navbar() {
  const { user, logout, setAuthOpen } = useAuth();
  const { isDark, toggle } = useTheme();
  const site = useSite();
  const toast = useToast();
  const nav = useNavigate();
  const loc = useLocation();
  const [q, setQ] = useState('');
  const [unread, setUnread] = useState<{ notif: number; msg: number }>({ notif: 0, msg: 0 });
  const [menuOpen, setMenuOpen] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement | null>(null);

  // poll unread counts
  useEffect(() => {
    if (!user) { setUnread({ notif: 0, msg: 0 }); return; }
    let alive = true;
    const tick = async () => {
      try {
        const [n, m] = await Promise.all([api.get('/notifications/unread'), api.get('/messages/unread')]);
        if (alive) setUnread({ notif: n.data.unread, msg: m.data.unread });
      } catch {}
    };
    tick();
    const id = setInterval(tick, 15000);
    return () => { alive = false; clearInterval(id); };
  }, [user, loc.pathname]);

  useEffect(() => {
    const onClick = (e: MouseEvent) => { if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false); };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const search = (e: React.FormEvent) => {
    e.preventDefault();
    if (q.trim()) nav(`/search?q=${encodeURIComponent(q.trim())}`);
  };

  return (
    <>
    <header className="nav">
      <div className="nav-inner">
        <button type="button" className="nav-burger" onClick={() => setDrawerOpen(true)} aria-label="打开菜单"><Icon name="menu" size={22} /></button>
        <Link to="/" className="brand">
          <BrandMark logo={site.logo} size={logoHeightOf(site.logoHeight)} />
          {showBrandText(site.logo, site.logoOnly) && <BrandName name={site.name} />}
        </Link>

        <nav className="nav-links">
          <NavLink to="/" end className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}><Icon name="home" size={18} /> 首页</NavLink>
          {moduleOn(site.modules, 'discover') && (
            <NavLink to="/discover" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}><Icon name="compass" size={18} /> 发现</NavLink>
          )}
          {moduleOn(site.modules, 'forum') && (
            <NavLink to="/forum" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}><Icon name="forum" size={18} /> 论坛</NavLink>
          )}
        </nav>

        <div className="spacer" />

        <form className="nav-search" onSubmit={search}>
          <Icon name="search" size={17} style={{ color: 'var(--ink-4)' }} />
          <input value={q} onChange={(e: React.ChangeEvent<HTMLInputElement>) => setQ(e.target.value)} placeholder="搜索用户、动态、帖子、话题…" aria-label="搜索" />
        </form>

        <Link to="/search" className="nav-icon-btn nav-search-btn" aria-label="搜索"><Icon name="search" size={20} /></Link>

        <ThemeSwitcher />

        {user ? (
          <>
            <Link to="/notifications" className="nav-icon-btn" title="通知">
              <Icon name="bell" size={21} />
              {unread.notif > 0 && <span className="nav-dot">{unread.notif > 99 ? '99+' : unread.notif}</span>}
            </Link>
            <Link to="/messages" className="nav-icon-btn nav-msg-btn" title="私信">
              <Icon name="mail" size={21} />
              {unread.msg > 0 && <span className="nav-dot">{unread.msg > 99 ? '99+' : unread.msg}</span>}
            </Link>
            <div ref={menuRef} style={{ position: 'relative' }}>
              <button type="button" onClick={() => setMenuOpen((m) => !m)} style={{ display: 'flex' }} aria-label="账号菜单">
                <Avatar user={user} size={38} showV ring noLink />
              </button>
              {menuOpen && (
                <div className="ui-card" style={{ position: 'absolute', right: 0, top: 48, zIndex: 60, width: 200, padding: 8, boxShadow: 'var(--shadow-pop)' }}>
                  <Link to={`/u/${user.username}`} className="row gap-8" style={{ padding: '8px 10px' }} onClick={() => setMenuOpen(false)}>
                    <Avatar user={user} size={40} showV noLink />
                    <div className="nowrap">
                      <div className="uname">{user.nickname}</div>
                      <div className="faint" style={{ fontSize: 12 }}>@{user.username}</div>
                    </div>
                  </Link>
                  <div className="divider" style={{ margin: '6px 0' }} />
                  <Link to={`/u/${user.username}`} className="rail-item" style={{ height: 40, fontSize: 14 }} onClick={() => setMenuOpen(false)}><Icon name="user" size={18} className="ico" /> 个人主页</Link>
                  <Link to="/bookmarks" className="rail-item" style={{ height: 40, fontSize: 14 }} onClick={() => setMenuOpen(false)}><Icon name="bookmark" size={18} className="ico" /> 我的收藏</Link>
                  <Link to="/history" className="rail-item" style={{ height: 40, fontSize: 14 }} onClick={() => setMenuOpen(false)}><Icon name="clock" size={18} className="ico" /> 浏览足迹</Link>
                  <Link to="/settings" className="rail-item" style={{ height: 40, fontSize: 14 }} onClick={() => setMenuOpen(false)}><Icon name="settings" size={18} className="ico" /> 编辑资料</Link>
                  {user.role === 'admin' && <Link to="/admin" className="rail-item" style={{ height: 40, fontSize: 14, color: 'var(--brand)' }} onClick={() => setMenuOpen(false)}><Icon name="shield" size={18} className="ico" /> 管理后台</Link>}
                  <div className="divider" style={{ margin: '6px 0' }} />
                  <button type="button" className="rail-item" style={{ height: 40, fontSize: 14, color: 'var(--like)' }} onClick={() => { logout(); setMenuOpen(false); toast.show('已退出登录'); nav('/'); }}>
                    <Icon name="logout" size={18} className="ico" /> 退出登录
                  </button>
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="row gap-8">
            <Button variant="flat" className="haha-btn-app" onClick={() => setAuthOpen(true)}>登录</Button>
            <Button color="primary" className="haha-btn-app" onClick={() => setAuthOpen(true)}>注册</Button>
          </div>
        )}
      </div>
    </header>
    <MobileDrawer open={drawerOpen} onClose={() => setDrawerOpen(false)} />
    </>
  );
}
