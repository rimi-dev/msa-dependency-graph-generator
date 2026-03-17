import React from 'react';
import type { D3Link, D3Node } from '@/types';
import { getProtocolColor } from '@/utils/colors';

interface EdgeTooltipProps {
  edge: D3Link;
  x: number;
  y: number;
}

export const EdgeTooltip: React.FC<EdgeTooltipProps> = ({ edge, x, y }) => {
  const color = getProtocolColor(edge.protocol);

  const sourceId = typeof edge.source === 'string' ? edge.source : (edge.source as D3Node).id;
  const targetId = typeof edge.target === 'string' ? edge.target : (edge.target as D3Node).id;

  const sourceName =
    typeof edge.source === 'string' ? edge.source : (edge.source as D3Node).displayName;
  const targetName =
    typeof edge.target === 'string' ? edge.target : (edge.target as D3Node).displayName;

  void sourceId;
  void targetId;

  const style: React.CSSProperties = {
    position: 'fixed',
    left: x + 12,
    top: y - 10,
    pointerEvents: 'none',
    zIndex: 9999,
    transform: 'translateY(-50%)',
  };

  if (x > window.innerWidth - 250) {
    style.left = x - 230;
  }
  if (y < 100) {
    style.top = y + 20;
    style.transform = 'none';
  }

  const confidencePct = Math.round(edge.confidence * 100);
  const confidenceColor =
    confidencePct >= 90 ? '#22c55e' : confidencePct >= 75 ? '#f59e0b' : '#ef4444';

  return (
    <div
      style={style}
      className="bg-[var(--tooltip-bg)] border border-[var(--tooltip-border)] rounded-lg shadow-xl p-3 min-w-[200px] max-w-[260px]"
    >
      <div className="flex items-center gap-2 mb-2">
        <span
          className="text-[10px] font-bold px-1.5 py-0.5 rounded uppercase tracking-wide"
          style={{ backgroundColor: color + '22', color }}
        >
          {edge.protocol}
        </span>
        <span className="text-xs text-[var(--text-secondary)] truncate">
          {sourceName} → {targetName}
        </span>
      </div>

      <div className="space-y-1 text-xs text-[var(--text-secondary)]">
        {edge.method && (
          <div className="flex justify-between">
            <span>메서드</span>
            <span className="font-mono font-medium text-[var(--text-primary)]">{edge.method}</span>
          </div>
        )}
        {edge.endpoint && (
          <div className="flex justify-between gap-2">
            <span className="flex-shrink-0">엔드포인트</span>
            <span className="font-mono text-[10px] text-[var(--text-primary)] truncate text-right">
              {edge.endpoint}
            </span>
          </div>
        )}
        <div className="flex justify-between">
          <span>참조 수</span>
          <span className="font-medium text-[var(--text-primary)]">{edge.sourceLocationCount}</span>
        </div>
        <div className="flex justify-between">
          <span>신뢰도</span>
          <span className="font-medium" style={{ color: confidenceColor }}>
            {confidencePct}%
          </span>
        </div>
        <div className="flex justify-between">
          <span>감지 방법</span>
          <span className="font-medium text-[var(--text-primary)]">{edge.detectedBy}</span>
        </div>
      </div>

      <div className="mt-2 pt-2 border-t border-[var(--tooltip-border)] text-[10px] text-[var(--text-muted)] text-center">
        클릭하여 코드 보기
      </div>
    </div>
  );
};
