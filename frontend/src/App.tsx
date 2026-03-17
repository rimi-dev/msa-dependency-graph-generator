import React, { useState, useEffect } from 'react';
import { Layout } from '@/components/Layout';
import { RepoInput } from '@/components/RepoInput';
import { GraphViewer } from '@/components/GraphViewer';
import { CodePreviewModal } from '@/components/CodePreviewModal';
import { useAnalysis } from '@/hooks/useAnalysis';
import { useGraph } from '@/hooks/useGraph';
import { useTheme } from '@/hooks/useTheme';
import type { D3Link } from '@/types';
import { listProjects } from '@/api/projects';
import type { Project } from '@/types';

const App: React.FC = () => {
  const { isDark } = useTheme();
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedEdge, setSelectedEdge] = useState<D3Link | null>(null);

  const analysis = useAnalysis();
  const graph = useGraph(selectedProjectId);

  // Load projects on mount
  useEffect(() => {
    listProjects()
      .then((res) => {
        if (res.success && res.data.length > 0) {
          setProjects(res.data);
        }
      })
      .catch(() => {
        // Backend not available — demo mode
      });
  }, []);

  // When analysis completes, switch to the new project
  useEffect(() => {
    if (analysis.completedProjectId) {
      setSelectedProjectId(analysis.completedProjectId);
      // Refresh project list
      listProjects()
        .then((res) => {
          if (res.success) setProjects(res.data);
        })
        .catch(() => {});
    }
  }, [analysis.completedProjectId]);

  const sidebar = (
    <>
      <RepoInput
        onAnalyzeRepo={analysis.analyzeRepo}
        onAnalyzeZip={analysis.analyzeZip}
        isRunning={analysis.isRunning}
        step={analysis.step}
        progress={analysis.progress}
        message={analysis.message}
        error={analysis.error}
        onReset={analysis.reset}
      />

      {/* Project List */}
      {projects.length > 0 && (
        <div className="bg-[var(--surface-1)] border border-[var(--border)] rounded-xl p-4 shadow-sm">
          <h2 className="text-sm font-semibold text-[var(--text-secondary)] uppercase tracking-wider mb-3">
            프로젝트 목록
          </h2>
          <div className="space-y-1.5">
            {projects.map((project) => (
              <button
                key={project.id}
                onClick={() => setSelectedProjectId(project.id)}
                className={`w-full text-left px-3 py-2.5 rounded-lg transition-all text-sm
                  ${selectedProjectId === project.id
                    ? 'bg-blue-500/10 border border-blue-500/30 text-blue-600 dark:text-blue-400'
                    : 'hover:bg-[var(--surface-2)] text-[var(--text-secondary)] border border-transparent'
                  }
                `}
              >
                <div className="flex items-center justify-between">
                  <span className="font-medium truncate">{project.name}</span>
                  {selectedProjectId === project.id && (
                    <span className="text-[10px] text-blue-500">●</span>
                  )}
                </div>
                <div className="text-[10px] text-[var(--text-muted)] mt-0.5 flex gap-2">
                  <span>{project.nodeCount} 서비스</span>
                  <span>·</span>
                  <span>{project.edgeCount} 의존성</span>
                </div>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Graph Info */}
      {graph.graphData && (
        <div className="bg-[var(--surface-1)] border border-[var(--border)] rounded-xl p-4 shadow-sm">
          <h2 className="text-sm font-semibold text-[var(--text-secondary)] uppercase tracking-wider mb-3">
            그래프 정보
          </h2>
          <div className="space-y-2">
            <div className="flex justify-between text-xs">
              <span className="text-[var(--text-secondary)]">프로젝트</span>
              <span className="text-[var(--text-primary)] font-medium truncate max-w-[140px]">
                {graph.graphData.metadata.projectName}
              </span>
            </div>
            <div className="flex justify-between text-xs">
              <span className="text-[var(--text-secondary)]">서비스 수</span>
              <span className="text-[var(--text-primary)] font-medium">
                {graph.graphData.metadata.totalNodes}
              </span>
            </div>
            <div className="flex justify-between text-xs">
              <span className="text-[var(--text-secondary)]">의존성 수</span>
              <span className="text-[var(--text-primary)] font-medium">
                {graph.graphData.metadata.totalEdges}
              </span>
            </div>
            <div className="pt-2 border-t border-[var(--border)]">
              <p className="text-[10px] text-[var(--text-muted)] mb-1.5">언어</p>
              <div className="flex flex-wrap gap-1">
                {graph.graphData.metadata.languages.map((lang) => (
                  <span
                    key={lang}
                    className="text-[10px] px-1.5 py-0.5 rounded bg-[var(--surface-2)] text-[var(--text-secondary)]"
                  >
                    {lang}
                  </span>
                ))}
              </div>
            </div>
            <div className="flex justify-between text-xs pt-1">
              <span className="text-[var(--text-muted)]">분석 시각</span>
              <span className="text-[var(--text-muted)] text-[10px]">
                {new Date(graph.graphData.metadata.analyzedAt).toLocaleString('ko-KR', {
                  month: 'short',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Usage Hints */}
      <div className="bg-[var(--surface-1)] border border-[var(--border)] rounded-xl p-4 shadow-sm">
        <h2 className="text-sm font-semibold text-[var(--text-secondary)] uppercase tracking-wider mb-3">
          사용법
        </h2>
        <ul className="space-y-1.5 text-[11px] text-[var(--text-muted)]">
          <li className="flex gap-2">
            <span className="flex-shrink-0">🖱️</span>
            <span>노드 호버 — 서비스 정보</span>
          </li>
          <li className="flex gap-2">
            <span className="flex-shrink-0">👆</span>
            <span>노드 클릭 — 연결 엣지 하이라이트</span>
          </li>
          <li className="flex gap-2">
            <span className="flex-shrink-0">👆👆</span>
            <span>더블클릭 — 1-depth 필터</span>
          </li>
          <li className="flex gap-2">
            <span className="flex-shrink-0">✋</span>
            <span>드래그 — 노드 이동 (핀 고정)</span>
          </li>
          <li className="flex gap-2">
            <span className="flex-shrink-0">🖱️→</span>
            <span>우클릭 — 고정 해제</span>
          </li>
          <li className="flex gap-2">
            <span className="flex-shrink-0">→</span>
            <span>엣지 클릭 — 코드 미리보기</span>
          </li>
          <li className="flex gap-2">
            <span className="flex-shrink-0">⚙️</span>
            <span>스크롤 — 줌 인/아웃</span>
          </li>
        </ul>
      </div>
    </>
  );

  return (
    <Layout sidebar={sidebar}>
      {graph.isLoading ? (
        <div className="h-full flex items-center justify-center bg-[var(--graph-bg)]">
          <div className="flex flex-col items-center gap-4 text-[var(--text-secondary)]">
            <svg className="animate-spin w-10 h-10 text-blue-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10" strokeOpacity="0.25" />
              <path d="M12 2a10 10 0 0 1 10 10" />
            </svg>
            <span className="text-sm font-medium">그래프 로딩 중...</span>
          </div>
        </div>
      ) : (
        <GraphViewer
          nodes={graph.nodes}
          links={graph.links}
          isMockData={graph.isMockData}
          onEdgeClick={(edge: D3Link) => setSelectedEdge(edge)}
        />
      )}

      <CodePreviewModal
        edge={selectedEdge}
        projectId={selectedProjectId ?? 'demo-project'}
        isDark={isDark}
        onClose={() => setSelectedEdge(null)}
      />
    </Layout>
  );
};

export default App;
