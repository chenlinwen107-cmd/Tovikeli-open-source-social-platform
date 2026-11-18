import { Link } from 'react-router-dom';
import { GENDER } from '../lib/format';
import type { PublicUser } from '../types';

type IdentityUser = Partial<PublicUser> & {
  username?: string;
  nickname?: string;
  verified?: boolean;
  verifiedNote?: string;
  title?: string;
  gender?: string;
  anonymous?: boolean;
};


export function Badges({ user, showTitle = true }: { user?: IdentityUser | null; showTitle?: boolean }) {
  if (!user) return null;
  return (
    <>
      {user.verified && <span className="ui-badge badge-v" title={user.verifiedNote || '认证'}>V</span>}

      {showTitle && user.title && <span className="ui-badge badge-title" title="头衔">{user.title}</span>}
    </>
  );
}

// Name + inline badges
export function UserName({ user, className = 'uname', showBadges = true }: { user?: IdentityUser | null; className?: string; showBadges?: boolean }) {
  if (!user) return null;
  if (user.anonymous) return <span className={className} style={{ color: 'var(--ink-2)' }}>匿名用户</span>;
  return (
    <Link to={`/u/${user.username}`} className={className}>
      {user.nickname}
      {showBadges && <Badges user={user} showTitle={false} />}
    </Link>
  );
}
