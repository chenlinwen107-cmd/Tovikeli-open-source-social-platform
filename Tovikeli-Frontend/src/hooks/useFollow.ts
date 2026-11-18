import { useCallback, useEffect, useState } from 'react';
import { followUser, getFollowState, unfollowUser } from '../api/follows';

export function useFollow(userId: string | null, enabled = true) {
  const [following, setFollowing] = useState(false);
  const [loading, setLoading] = useState(false);

  const reload = useCallback(async () => {
    if (!userId || !enabled) return;
    setLoading(true);
    try {
      const state = await getFollowState(userId);
      setFollowing(state.following);
    } finally {
      setLoading(false);
    }
  }, [userId, enabled]);

  useEffect(() => { void reload(); }, [reload]);

  const toggle = useCallback(async () => {
    if (!userId || loading) return;
    setLoading(true);
    try {
      if (following) {
        await unfollowUser(userId);
        setFollowing(false);
      } else {
        await followUser(userId);
        setFollowing(true);
      }
    } finally {
      setLoading(false);
    }
  }, [userId, following, loading]);

  return { following, loading, toggle, reload };
}
