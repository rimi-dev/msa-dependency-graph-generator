import React, { useState, useRef, useEffect } from 'react';
import type { D3Node } from '@/types';

interface ServiceDetailPopupProps {
  node: D3Node;
  onClose: () => void;
  onRename: (serviceId: string, newName: string) => Promise<void>;
}

export const ServiceDetailPopup: React.FC<ServiceDetailPopupProps> = ({
  node,
  onClose,
  onRename,
}) => {
  const [isEditing, setIsEditing] = useState(false);
  const [editName, setEditName] = useState(node.displayName);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setEditName(node.displayName);
    setIsEditing(false);
  }, [node.id, node.displayName]);

  useEffect(() => {
    if (isEditing) inputRef.current?.focus();
  }, [isEditing]);

  const handleSave = async () => {
    const trimmed = editName.trim();
    if (trimmed && trimmed !== node.displayName) {
      await onRename(node.id, trimmed);
    }
    setIsEditing(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleSave();
    if (e.key === 'Escape') {
      setEditName(node.displayName);
      setIsEditing(false);
    }
  };

  return (
    <div
      style={{ position: 'fixed', top: 80, right: 24, zIndex: 50, width: 320 }}
      className="bg-[var(--surface-1)] border border-[var(--border)] rounded-xl shadow-2xl"
    >
      {/* Header */}
      <div className="flex items-center justify-between px-4 pt-3 pb-2 border-b border-[var(--border)]">
        {isEditing ? (
          <input
            ref={inputRef}
            value={editName}
            onChange={(e) => setEditName(e.target.value)}
            onKeyDown={handleKeyDown}
            onBlur={handleSave}
            className="flex-1 text-sm font-semibold bg-[var(--surface-2)] text-[var(--text-primary)] px-2 py-1 rounded border border-blue-500 outline-none"
          />
        ) : (
          <div className="flex items-center gap-2 flex-1 min-w-0">
            <h3 className="text-sm font-semibold text-[var(--text-primary)] truncate">
              {node.displayName}
            </h3>
            <button
              onClick={() => setIsEditing(true)}
              className="text-[var(--text-muted)] hover:text-[var(--text-secondary)] transition-colors flex-shrink-0"
              title="이름 수정"
            >
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
              </svg>
            </button>
          </div>
        )}
        <button
          onClick={onClose}
          className="text-[var(--text-muted)] hover:text-[var(--text-primary)] transition-colors ml-2 flex-shrink-0"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      {/* Body */}
      <div className="px-4 py-3 space-y-3">
        {/* Language / Framework badges */}
        <div className="flex flex-wrap gap-1.5">
          <span className="text-[10px] px-2 py-0.5 rounded-full bg-blue-500/10 text-blue-600 dark:text-blue-400 font-medium">
            {node.language}
          </span>
          {node.framework && (
            <span className="text-[10px] px-2 py-0.5 rounded-full bg-purple-500/10 text-purple-600 dark:text-purple-400 font-medium">
              {node.framework}
            </span>
          )}
        </div>

        {/* Repo URL */}
        {node.repoUrl && (
          <div className="flex items-center gap-2 text-xs">
            <span className="text-[var(--text-muted)]">레포</span>
            <a
              href={node.repoUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-500 hover:text-blue-400 truncate transition-colors"
              title={node.repoUrl}
            >
              {node.repoUrl.replace(/^https?:\/\/(www\.)?github\.com\//, '')}
            </a>
          </div>
        )}

        {/* Dependency count */}
        <div className="bg-[var(--surface-2)] rounded-lg px-3 py-2 text-center">
          <div className="text-lg font-bold text-[var(--text-primary)]">
            {node.dependencyCount}
          </div>
          <div className="text-[10px] text-[var(--text-muted)]">Dependencies</div>
        </div>
      </div>
    </div>
  );
};
