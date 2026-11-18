import { useState } from 'react';
import Modal from '../Modal';
import { Button } from '../heroui';
import { UserName } from '../Identity';
import { useToast } from '../../context/ToastContext';
import { useAuth } from '../../context/AuthContext';
import { createInteraction } from '../../api/interactions';

// 转发弹窗：可选评论 + 原动态摘要 + 发布。受控 open/onClose；
// shareText 为弹窗局部 state（组件常驻挂载，跨开关保留，与抽离前行为一致）。
export interface ShareModalProps {
  open: boolean;
  onClose: () => void;
  post: any;
}

export default function ShareModal({ open, onClose, post }: ShareModalProps) {
  const toast = useToast();
  const { user, setAuthOpen } = useAuth();
  const [shareText, setShareText] = useState('');

  const doShare = async () => {
    if (!user) { setAuthOpen(true); return; }
    try {
      await createInteraction(String(user.id), { contentId: String(post.id), type: 'REPOST' });
      onClose(); setShareText(''); toast.ok('转发成功');
    }
    catch (e: any) { toast.err(e.message); }
  };

  return (
    <Modal open={open} onClose={onClose}>
      <div className="modal-head"><div className="modal-title">转发动态</div></div>
      <div className="modal-body">
        <textarea className="field" style={{ width: '100%', minHeight: 80, padding: 12, border: '1.5px solid var(--line-2)', borderRadius: 10 }}
          value={shareText} onChange={(e) => setShareText(e.target.value)} placeholder="说点什么…（可选）" />
        <div className="repost" style={{ marginTop: 4 }}>
          <UserName user={post.author} showBadges={false} />
          <div className="post-body" style={{ fontSize: 13 }}>{(post.content || '').slice(0, 80)}</div>
        </div>
        <Button size="lg" color="primary" fullWidth className="haha-btn-app" style={{ marginTop: 14 }} onClick={doShare}>转发</Button>
      </div>
    </Modal>
  );
}
