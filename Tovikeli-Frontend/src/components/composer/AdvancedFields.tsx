import Icon from '../Icon';
import { Input } from '../heroui';

// 高级选项行：访问密码 / 所在位置。受控组件，state 留在 Composer。
export interface AdvancedFieldsProps {
  showLoc: boolean;
  location: string;
  onLocationChange: (v: string) => void;
  onLocationClear: () => void;
}

export default function AdvancedFields({ showLoc, location, onLocationChange, onLocationClear }: AdvancedFieldsProps) {
  return (
    <>
      {showLoc && (
        <div className="row gap-8" style={{ marginTop: 10, fontSize: 13 }}>
          <Icon name="location" size={15} style={{ color: 'var(--brand)' }} />
          <Input value={location} onChange={(e: any) => onLocationChange(e.target.value)} placeholder="所在城市，如：上海" maxLength={20}
            className="haha-inp haha-inp-sm" style={{ width: 210 }} />
          {location && <button className="faint" style={{ fontSize: 12 }} onClick={onLocationClear}>清除</button>}
        </div>
      )}
    </>
  );
}
