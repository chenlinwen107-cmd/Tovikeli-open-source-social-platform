import { createContext, useContext, useState, useCallback, useEffect } from 'react';
import type { ReactNode } from 'react';
import api from '../api/client';
import type { PublicUser } from '../types';

const GUEST_KEY = 'haha_guest';

export interface AuthValue {
  user: PublicUser | null;
  loading: boolean;
  authOpen: boolean;
  setAuthOpen: (open: boolean) => void;
  isGuest: boolean;
  enterGuest: () => void;
  exitGuest: () => void;
  requireLogin: () => boolean;
  login: (username: string, password: string) => Promise<PublicUser>;
  register: (payload: Record<string, unknown>) => Promise<PublicUser>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  patchUser: (partial: Partial<PublicUser>) => void;
}

const AuthContext = createContext<AuthValue | null>(null);

function readGuestFlag(): boolean {
  try {
    return localStorage.getItem(GUEST_KEY) === '1';
  } catch {
    return false;
  }
}

function mapUser(data: any): PublicUser {
  return {
    id: data.id,
    username: data.username,
    nickname: data.displayName || data.username,
    avatar: data.avatar || '',
    bio: data.bio || '',
    location: data.location || '',
    gender: data.gender || 'secret',
    cover: data.cover || '',
    role: 'user',
    verified: false,
    postCount: 0,
    following: 0,
    followers: 0,
  };
}

export function AuthProvider({ children }: { children?: ReactNode }) {
  const [user, setUser] = useState<PublicUser | null>(null);
  const [loading, setLoading] = useState(false);
  const [authOpen, setAuthOpen] = useState(false);
  const [guest, setGuest] = useState(readGuestFlag);

  const refresh = useCallback(async () => {
    setLoading(true);

    try {
      const { data } = await api.get('/auth/me');
      setUser(mapUser(data));

      try { localStorage.removeItem(GUEST_KEY); } catch { /* */ }
      setGuest(false);
    } catch {
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  const enterGuest = useCallback(() => {
    try { localStorage.setItem(GUEST_KEY, '1'); } catch { /* */ }
    setGuest(true);
  }, []);

  const exitGuest = useCallback(() => {
    try { localStorage.removeItem(GUEST_KEY); } catch { /* */ }
    setGuest(false);
  }, []);

  const requireLogin = useCallback(() => {
    if (user) return true;
    setAuthOpen(true);
    return false;
  }, [user]);

  const login = async (username: string, password: string): Promise<PublicUser> => {
    const { data } = await api.post('/auth/login', { username, password });
    const nextUser = mapUser(data);
    try { localStorage.removeItem(GUEST_KEY); } catch { /* */ }
    setGuest(false);
    setUser(nextUser);
    setAuthOpen(false);
    return nextUser;
  };

  const register = async (payload: Record<string, unknown>): Promise<PublicUser> => {
    const { username, password, nickname } = payload as {
      username?: string;
      password?: string;
      nickname?: string;
    };

    const { data } = await api.post('/users', {
      username,
      password,
      displayName: nickname || username,
    });

    const createdUser = mapUser(data);

    // 创建账号后立即登录，建立后端 Session。
    const loginResponse = await api.post('/auth/login', {
      username,
      password,
    });

    const nextUser = mapUser(loginResponse.data || createdUser);

    try { localStorage.removeItem(GUEST_KEY); } catch { /* */ }
    setGuest(false);
    setUser(nextUser);
    setAuthOpen(false);
    return nextUser;
  };

  const logout = async () => {
    try {
      await api.post('/auth/logout');
    } finally {
      try { localStorage.removeItem(GUEST_KEY); } catch { /* */ }
      setGuest(false);
      setUser(null);
    }
  };

  const patchUser = (partial: Partial<PublicUser>) => {
    setUser((u) => (u ? { ...u, ...partial } : u));
  };

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const isGuest = !user && guest;

  return (
    <AuthContext.Provider value={{
      user,
      loading,
      login,
      register,
      logout,
      refresh,
      patchUser,
      authOpen,
      setAuthOpen,
      isGuest,
      enterGuest,
      exitGuest,
      requireLogin,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = (): AuthValue => useContext(AuthContext) as AuthValue;
