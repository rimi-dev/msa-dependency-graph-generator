import React, { useState, useEffect, useCallback, useRef } from 'react';
import Editor from '@monaco-editor/react';
import type { D3Link, D3Node, SourceDetail, SourceLocationDetail } from '@/types';
import { getDependencySource } from '@/api/projects';
import { MOCK_SOURCE_DETAIL } from '@/utils/mockData';
import { getProtocolColor } from '@/utils/colors';

interface CodePreviewModalProps {
  edge: D3Link | null;
  projectId: string;
  isDark: boolean;
  onClose: () => void;
}

export const CodePreviewModal: React.FC<CodePreviewModalProps> = ({
  edge,
  projectId,
  isDark,
  onClose,
}) => {
  const [sourceDetail, setSourceDetail] = useState<SourceDetail | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [activeLocationIdx, setActiveLocationIdx] = useState(0);
  const [configExpanded, setConfigExpanded] = useState(true);
  const [copied, setCopied] = useState(false);
  const overlayRef = useRef<HTMLDivElement>(null);

  const sourceName =
    edge
      ? typeof edge.source === 'string'
        ? edge.source
        : (edge.source as D3Node).displayName
      : '';
  const targetName =
    edge
      ? typeof edge.target === 'string'
        ? edge.target
        : (edge.target as D3Node).displayName
      : '';

  const loadSource = useCallback(async () => {
    if (!edge) return;
    setIsLoading(true);
    setActiveLocationIdx(0);
    setConfigExpanded(true);
    try {
      const res = await getDependencySource(projectId, edge.id);
      if (res.success) {
        setSourceDetail(res.data);
      } else {
        // Fall back to mock
        setSourceDetail(MOCK_SOURCE_DETAIL);
      }
    } catch {
      setSourceDetail(MOCK_SOURCE_DETAIL);
    } finally {
      setIsLoading(false);
    }
  }, [edge, projectId]);

  useEffect(() => {
    if (edge) {
      loadSource();
    } else {
      setSourceDetail(null);
    }
  }, [edge, loadSource]);

  // ESC key
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [onClose]);

  const handleOverlayClick = (e: React.MouseEvent) => {
    if (e.target === overlayRef.current) onClose();
  };

  const activeLocation: SourceLocationDetail | undefined = sourceDetail?.locations[activeLocationIdx];

  const handleCopy = async () => {
    if (activeLocation) {
      await navigator.clipboard.writeText(activeLocation.content);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const getLanguageForMonaco = (lang: string): string => {
    const map: Record<string, string> = {
      kotlin: 'kotlin',
      java: 'java',
      typescript: 'typescript',
      javascript: 'javascript',
      python: 'python',
      go: 'go',
      rust: 'rust',
    };
    return map[lang.toLowerCase()] ?? 'plaintext';
  };

  if (!edge) return null;

  const protocolColor = getProtocolColor(edge.protocol);

  return (
    <div
      ref={overlayRef}
      className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4"
      onClick={handleOverlayClick}
    >
      <div
        className="bg-[var(--surface-1)] border border-[var(--border)] rounded-2xl shadow-2xl flex flex-col overflow-hidden"
        style={{ width: '60%', height: '70%', maxWidth: 860, maxHeight: 680, minWidth: 600, minHeight: 440 }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex-shrink-0 px-5 py-3 border-b border-[var(--border)] flex items-center gap-3">
          <div className="flex items-center gap-2 flex-1 min-w-0">
            <span className="font-semibold text-sm text-[var(--text-primary)] truncate">{sourceName}</span>
            <span className="flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-bold" style={{ backgroundColor: protocolColor + '22', color: protocolColor }}>
              ──{edge.protocol}──▶
            </span>
            <span className="font-semibold text-sm text-[var(--text-primary)] truncate">{targetName}</span>
          </div>

          {/* Location tabs */}
          {sourceDetail && sourceDetail.locations.length > 1 && (
            <div className="flex gap-1">
              {sourceDetail.locations.map((_, i) => (
                <button
                  key={i}
                  onClick={() => setActiveLocationIdx(i)}
                  className={`text-xs px-2 py-0.5 rounded-md transition-colors font-medium
                    ${activeLocationIdx === i ? 'bg-blue-500 text-white' : 'bg-[var(--surface-2)] text-[var(--text-secondary)] hover:bg-[var(--surface-3)]'}
                  `}
                >
                  #{i + 1}
                </button>
              ))}
            </div>
          )}

          {/* Close */}
          <button
            onClick={onClose}
            className="w-7 h-7 flex items-center justify-center rounded-lg hover:bg-[var(--surface-2)] text-[var(--text-secondary)] transition-colors flex-shrink-0"
          >
            ✕
          </button>
        </div>

        {/* File path */}
        {activeLocation && (
          <div className="flex-shrink-0 px-5 py-2 border-b border-[var(--border)] flex items-center gap-2 text-xs text-[var(--text-secondary)] bg-[var(--surface-2)]">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
            </svg>
            <span className="font-mono truncate">{activeLocation.filePath}</span>
            <span className="flex-shrink-0 text-[var(--text-muted)]">
              L{activeLocation.startLine}–{activeLocation.endLine}
            </span>
          </div>
        )}

        {/* Monaco Editor */}
        <div className="flex-1 overflow-hidden">
          {isLoading ? (
            <div className="h-full flex items-center justify-center text-[var(--text-secondary)]">
              <div className="flex flex-col items-center gap-3">
                <svg className="animate-spin w-8 h-8 text-blue-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="10" strokeOpacity="0.25" />
                  <path d="M12 2a10 10 0 0 1 10 10" />
                </svg>
                <span className="text-sm">소스 코드 불러오는 중...</span>
              </div>
            </div>
          ) : activeLocation ? (
            <Editor
              height="100%"
              language={getLanguageForMonaco(activeLocation.language)}
              value={activeLocation.content}
              theme={isDark ? 'vs-dark' : 'light'}
              options={{
                readOnly: true,
                minimap: { enabled: false },
                scrollBeyondLastLine: false,
                fontSize: 13,
                lineNumbers: 'on',
                glyphMargin: false,
                folding: false,
                lineDecorationsWidth: 0,
                overviewRulerLanes: 0,
                hideCursorInOverviewRuler: true,
                renderLineHighlight: 'none',
                wordWrap: 'off',
                fontFamily: 'JetBrains Mono, Fira Code, Cascadia Code, monospace',
                fontLigatures: true,
                padding: { top: 8, bottom: 8 },
              }}
              onMount={(editor, monaco) => {
                // Highlight lines
                if (activeLocation.highlightLines.length > 0) {
                  // Offset by startLine since editor shows content starting at line 1
                  const offset = activeLocation.startLine - 1;
                  const decorations = activeLocation.highlightLines.map((line) => ({
                    range: new monaco.Range(line - offset, 1, line - offset, 1),
                    options: {
                      isWholeLine: true,
                      className: 'highlighted-line',
                      glyphMarginClassName: 'highlighted-glyph',
                      inlineClassName: 'highlighted-inline',
                    },
                  }));
                  editor.createDecorationsCollection(decorations);

                  // Scroll to first highlight
                  const firstHighlight = activeLocation.highlightLines[0];
                  editor.revealLineInCenter(firstHighlight - offset);
                }
              }}
            />
          ) : (
            <div className="h-full flex items-center justify-center text-[var(--text-secondary)] text-sm">
              소스 코드를 찾을 수 없습니다
            </div>
          )}
        </div>

        {/* Related Config */}
        {sourceDetail && sourceDetail.relatedConfig.length > 0 && (
          <div className="flex-shrink-0 border-t border-[var(--border)] max-h-[160px] overflow-y-auto">
            <button
              onClick={() => setConfigExpanded(!configExpanded)}
              className="w-full px-5 py-2 flex items-center justify-between text-xs font-medium text-[var(--text-secondary)] hover:bg-[var(--surface-2)] transition-colors"
            >
              <span className="flex items-center gap-2">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="3" />
                  <path d="M12 1v4M12 19v4M4.22 4.22l2.83 2.83M16.95 16.95l2.83 2.83M1 12h4M19 12h4M4.22 19.78l2.83-2.83M16.95 7.05l2.83-2.83" />
                </svg>
                관련 설정 ({sourceDetail.relatedConfig.length})
              </span>
              <svg
                className={`w-3 h-3 transition-transform ${configExpanded ? 'rotate-180' : ''}`}
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.5"
              >
                <polyline points="6 9 12 15 18 9" />
              </svg>
            </button>

            {configExpanded && (
              <div className="px-5 pb-3 space-y-1.5">
                {sourceDetail.relatedConfig.map((entry, i) => (
                  <div key={i} className="flex items-start gap-2 text-xs">
                    <span className="font-mono text-[var(--text-muted)] flex-shrink-0 text-[10px] mt-0.5">
                      {entry.filePath.split('/').pop()}
                    </span>
                    <span className="text-blue-500 font-mono flex-shrink-0">{entry.key}</span>
                    <span className="text-[var(--text-muted)]">=</span>
                    <span className="font-mono text-[var(--text-primary)] truncate">{entry.value}</span>
                    {entry.githubUrl && (
                      <a
                        href={entry.githubUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex-shrink-0 text-[var(--text-muted)] hover:text-blue-500 transition-colors"
                      >
                        ↗
                      </a>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Footer */}
        <div className="flex-shrink-0 px-5 py-2.5 border-t border-[var(--border)] flex items-center justify-between bg-[var(--surface-2)]">
          <div className="flex items-center gap-3">
            {activeLocation?.githubUrl && (
              <a
                href={activeLocation.githubUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-1.5 text-xs text-blue-500 hover:text-blue-400 transition-colors font-medium"
              >
                <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z" />
                </svg>
                GitHub에서 보기 ↗
              </a>
            )}
          </div>

          <div className="flex items-center gap-2">
            {sourceDetail && (
              <span className="text-xs text-[var(--text-muted)]">
                {sourceDetail.locations.length}개 위치
              </span>
            )}
            <button
              onClick={handleCopy}
              className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg bg-[var(--surface-3)] hover:bg-[var(--surface-1)] text-[var(--text-secondary)] transition-all"
            >
              {copied ? (
                <>
                  <span>✓</span>
                  <span>복사됨</span>
                </>
              ) : (
                <>
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                  </svg>
                  <span>코드 복사</span>
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
