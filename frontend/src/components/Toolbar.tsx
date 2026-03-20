import React, { useState, useRef, useEffect } from 'react';
import type { LayoutType, Protocol } from '@/types';
import { getProtocolColor } from '@/utils/colors';

interface ToolbarProps {
  zoom: number;
  onZoomIn: () => void;
  onZoomOut: () => void;
  onFitAll: () => void;
  layout: LayoutType;
  onLayoutChange: (layout: LayoutType) => void;
  protocolFilter: Set<Protocol>;
  onProtocolFilterChange: (protocols: Set<Protocol>) => void;
  lockNodes: boolean;
  onLockToggle: () => void;
  edgeCounts: { HTTP: number };
}

const LAYOUTS: { value: LayoutType; label: string; icon: string }[] = [
  { value: 'force', label: '물리 시뮬레이션', icon: '⚛️' },
  { value: 'dagre', label: '계층형', icon: '📊' },
  { value: 'radial', label: '방사형', icon: '🎯' },
];

const PROTOCOLS: Protocol[] = ['HTTP'];

export const Toolbar: React.FC<ToolbarProps> = ({
  zoom,
  onZoomIn,
  onZoomOut,
  onFitAll,
  layout,
  onLayoutChange,
  protocolFilter,
  onProtocolFilterChange,
  lockNodes,
  onLockToggle,
  edgeCounts,
}) => {
  const [layoutOpen, setLayoutOpen] = useState(false);
  const [filterOpen, setFilterOpen] = useState(false);
  const layoutRef = useRef<HTMLDivElement>(null);
  const filterRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (layoutRef.current && !layoutRef.current.contains(e.target as Node)) {
        setLayoutOpen(false);
      }
      if (filterRef.current && !filterRef.current.contains(e.target as Node)) {
        setFilterOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  const toggleProtocol = (protocol: Protocol) => {
    const next = new Set(protocolFilter);
    if (next.has(protocol)) {
      if (next.size > 1) next.delete(protocol);
    } else {
      next.add(protocol);
    }
    onProtocolFilterChange(next);
  };

  const currentLayout = LAYOUTS.find((l) => l.value === layout);
  const activeFilterCount = PROTOCOLS.filter((p) => !protocolFilter.has(p)).length;

  return (
    <div className="flex items-center gap-2 flex-wrap">
      {/* Zoom Controls */}
      <div className="flex items-center gap-1 bg-[var(--surface-2)] rounded-lg px-1 py-1">
        <button
          onClick={onZoomOut}
          className="w-7 h-7 flex items-center justify-center rounded-md hover:bg-[var(--surface-3)] text-[var(--text-secondary)] transition-colors text-sm font-bold"
          title="축소"
        >
          −
        </button>
        <span className="text-xs font-mono text-[var(--text-secondary)] min-w-[40px] text-center select-none">
          {Math.round(zoom * 100)}%
        </span>
        <button
          onClick={onZoomIn}
          className="w-7 h-7 flex items-center justify-center rounded-md hover:bg-[var(--surface-3)] text-[var(--text-secondary)] transition-colors text-sm font-bold"
          title="확대"
        >
          +
        </button>
      </div>

      {/* Fit All */}
      <button
        onClick={onFitAll}
        className="h-9 px-3 text-xs font-medium rounded-lg bg-[var(--surface-2)] hover:bg-[var(--surface-3)] text-[var(--text-secondary)] transition-colors flex items-center gap-1.5"
        title="전체 보기"
      >
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
          <path d="M3 3h6M3 3v6M21 3h-6M21 3v6M3 21h6M3 21v-6M21 21h-6M21 21v-6" />
        </svg>
        전체
      </button>

      {/* Layout Dropdown */}
      <div ref={layoutRef} className="relative">
        <button
          onClick={() => setLayoutOpen(!layoutOpen)}
          className="h-9 px-3 text-xs font-medium rounded-lg bg-[var(--surface-2)] hover:bg-[var(--surface-3)] text-[var(--text-secondary)] transition-colors flex items-center gap-1.5"
        >
          <span>{currentLayout?.icon}</span>
          <span>레이아웃</span>
          <svg
            className={`w-3 h-3 transition-transform ${layoutOpen ? 'rotate-180' : ''}`}
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
          >
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </button>

        {layoutOpen && (
          <div className="absolute bottom-full mb-2 left-0 bg-[var(--surface-1)] border border-[var(--border)] rounded-xl shadow-xl py-1 z-50 min-w-[160px]">
            {LAYOUTS.map((l) => (
              <button
                key={l.value}
                onClick={() => {
                  onLayoutChange(l.value);
                  setLayoutOpen(false);
                }}
                className={`w-full text-left px-3 py-2 text-xs flex items-center gap-2 hover:bg-[var(--surface-2)] transition-colors
                  ${layout === l.value ? 'text-blue-500 font-medium' : 'text-[var(--text-secondary)]'}
                `}
              >
                <span>{l.icon}</span>
                <span>{l.label}</span>
                {layout === l.value && <span className="ml-auto">✓</span>}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Filter Dropdown */}
      <div ref={filterRef} className="relative">
        <button
          onClick={() => setFilterOpen(!filterOpen)}
          className={`h-9 px-3 text-xs font-medium rounded-lg transition-colors flex items-center gap-1.5
            ${activeFilterCount > 0 ? 'bg-amber-500/10 text-amber-600 border border-amber-500/30' : 'bg-[var(--surface-2)] hover:bg-[var(--surface-3)] text-[var(--text-secondary)]'}
          `}
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
            <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" />
          </svg>
          <span>필터</span>
          {activeFilterCount > 0 && (
            <span className="bg-amber-500 text-white text-[9px] w-4 h-4 rounded-full flex items-center justify-center font-bold">
              {activeFilterCount}
            </span>
          )}
          <svg
            className={`w-3 h-3 transition-transform ${filterOpen ? 'rotate-180' : ''}`}
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
          >
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </button>

        {filterOpen && (
          <div className="absolute bottom-full mb-2 left-0 bg-[var(--surface-1)] border border-[var(--border)] rounded-xl shadow-xl py-2 z-50 min-w-[160px]">
            <div className="px-3 pb-1 text-[10px] text-[var(--text-muted)] uppercase tracking-wider font-medium">
              프로토콜 필터
            </div>
            {PROTOCOLS.map((p) => {
              const color = getProtocolColor(p);
              const isActive = protocolFilter.has(p);
              const count = edgeCounts[p];
              return (
                <button
                  key={p}
                  onClick={() => toggleProtocol(p)}
                  className="w-full text-left px-3 py-2 text-xs flex items-center gap-2 hover:bg-[var(--surface-2)] transition-colors"
                >
                  <div
                    className={`w-3 h-3 rounded-full border-2 flex items-center justify-center transition-all`}
                    style={{
                      borderColor: color,
                      backgroundColor: isActive ? color : 'transparent',
                    }}
                  >
                    {isActive && <div className="w-1.5 h-1.5 rounded-full bg-white" />}
                  </div>
                  <span
                    className={`font-medium ${isActive ? 'text-[var(--text-primary)]' : 'text-[var(--text-muted)] line-through'}`}
                  >
                    {p}
                  </span>
                  <span className="ml-auto text-[var(--text-muted)]">{count}</span>
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* Lock Toggle */}
      <button
        onClick={onLockToggle}
        title={lockNodes ? '노드 잠금 해제' : '노드 잠금'}
        className={`h-9 w-9 flex items-center justify-center rounded-lg text-base transition-all
          ${lockNodes ? 'bg-amber-500/10 text-amber-500 border border-amber-500/30 shadow-inner' : 'bg-[var(--surface-2)] hover:bg-[var(--surface-3)] text-[var(--text-secondary)]'}
        `}
      >
        {lockNodes ? '🔒' : '🔓'}
      </button>

      {/* Legend */}
      <div className="flex items-center gap-3 ml-auto text-xs text-[var(--text-secondary)]">
        {PROTOCOLS.map((p) => {
          const color = getProtocolColor(p);
          return (
            <span key={p} className="flex items-center gap-1.5">
              <svg width="20" height="8" viewBox="0 0 20 8">
                <line x1="0" y1="4" x2="20" y2="4" stroke={color} strokeWidth="2" />
              </svg>
              <span style={{ color }}>
                {p} ({edgeCounts[p]})
              </span>
            </span>
          );
        })}
      </div>
    </div>
  );
};
