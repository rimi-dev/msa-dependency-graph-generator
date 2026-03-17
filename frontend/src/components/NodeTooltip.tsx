import React from 'react';
import type { D3Node } from '@/types';
import { getLanguageColor } from '@/utils/colors';

interface NodeTooltipProps {
  node: D3Node;
  x: number;
  y: number;
}

export const NodeTooltip: React.FC<NodeTooltipProps> = ({ node, x, y }) => {
  const color = getLanguageColor(node.language);

  const style: React.CSSProperties = {
    position: 'fixed',
    left: x + 12,
    top: y - 10,
    pointerEvents: 'none',
    zIndex: 9999,
    transform: 'translateY(-50%)',
  };

  // Adjust to not go off screen
  if (x > window.innerWidth - 220) {
    style.left = x - 200;
  }
  if (y < 80) {
    style.top = y + 20;
    style.transform = 'none';
  }

  return (
    <div
      style={style}
      className="bg-[var(--tooltip-bg)] border border-[var(--tooltip-border)] rounded-lg shadow-xl p-3 min-w-[180px] max-w-[220px]"
    >
      <div className="flex items-center gap-2 mb-2">
        <div
          className="w-3 h-3 rounded-full flex-shrink-0"
          style={{ backgroundColor: color.bg }}
        />
        <span className="font-semibold text-sm text-[var(--text-primary)] truncate">
          {node.displayName}
        </span>
      </div>
      <div className="space-y-1 text-xs text-[var(--text-secondary)]">
        <div className="flex justify-between">
          <span>언어</span>
          <span
            className="font-medium px-1.5 py-0.5 rounded text-[10px]"
            style={{ backgroundColor: color.bg, color: color.text }}
          >
            {node.language}
          </span>
        </div>
        {node.framework && (
          <div className="flex justify-between">
            <span>프레임워크</span>
            <span className="font-medium text-[var(--text-primary)]">{node.framework}</span>
          </div>
        )}
        <div className="flex justify-between pt-1 border-t border-[var(--tooltip-border)]">
          <span>발신 의존성</span>
          <span className="font-medium text-[var(--text-primary)]">
            {node.dependencyCount.outgoing}
          </span>
        </div>
        <div className="flex justify-between">
          <span>수신 의존성</span>
          <span className="font-medium text-[var(--text-primary)]">
            {node.dependencyCount.incoming}
          </span>
        </div>
        {node.pinned && (
          <div className="flex items-center gap-1 pt-1 text-amber-500">
            <span>🔒</span>
            <span>고정됨</span>
          </div>
        )}
      </div>
    </div>
  );
};
