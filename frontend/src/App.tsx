import React, { useState, useEffect, useCallback } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from '@/components/Layout';
import { RepoInput } from '@/components/RepoInput';
import { GraphViewer } from '@/components/GraphViewer';
import { CodePreviewModal } from '@/components/CodePreviewModal';
import { ServiceDetailPopup } from '@/components/ServiceDetailPopup';
import { LoginPage } from '@/components/LoginPage';
import { OAuthCallback } from '@/components/OAuthCallback';
import { useAnalysis } from '@/hooks/useAnalysis';
import { useGraph } from '@/hooks/useGraph';
import { useTheme } from '@/hooks/useTheme';
import { useAuth } from '@/hooks/useAuth';
import type { D3Link, D3Node, ProjectRepo, ServiceInfo } from '@/types';
import { listProjects, getProjectDetail, addRepo, removeRepo as removeRepoApi, renameService, listServices, createProject, deleteProject } from '@/api/projects';
import type { Project } from '@/types';

const App: React.FC = () => {
  const { user, isAuthenticated, isLoading, login, logout, checkAuth } = useAuth();

  return (
    <Routes>
      <Route path="/login" element={
        isAuthenticated ? <Navigate to="/" replace /> : <LoginPage onLogin={login} />
      } />
      <Route path="/oauth/callback" element={
        <OAuthCallback onAuthComplete={checkAuth} />
      } />
      <Route path="/*" element={
        <MainApp user={user} onLogin={login} onLogout={logout} isAuthenticated={isAuthenticated} />
      } />
    </Routes>
  );
};

// ─── 메인 앱 (인증 완료 상태) ────────────────────────────────────────────────

interface MainAppProps {
  user: { id: string; login: string; name: string | null; email: string | null; avatarUrl: string | null } | null;
  onLogin: () => void;
  onLogout: () => void;
  isAuthenticated: boolean;
}

const MainApp: React.FC<MainAppProps> = ({ user, onLogin, onLogout, isAuthenticated }) => {
  const { isDark } = useTheme();
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [projectRepos, setProjectRepos] = useState<ProjectRepo[]>([]);
  const [services, setServices] = useState<ServiceInfo[]>([]);
  const [selectedEdge, setSelectedEdge] = useState<D3Link | null>(null);
  const [selectedNode, setSelectedNode] = useState<D3Node | null>(null);

  const analysis = useAnalysis();
  const graph = useGraph(selectedProjectId);

  // 마운트 시 프로젝트 목록 로드
  useEffect(() => {
    listProjects()
      .then((res) => {
        if (res.success && res.data.length > 0) {
          setProjects(res.data);
        }
      })
      .catch(() => {
        // 백엔드 사용 불가 — 데모 모드
      });
  }, []);

  // 프로젝트 선택 시 레포 및 서비스 로드
  useEffect(() => {
    if (selectedProjectId) {
      getProjectDetail(selectedProjectId)
        .then((res) => {
          if (res.success && res.data.repos) {
            setProjectRepos(res.data.repos);
          } else {
            setProjectRepos([]);
          }
        })
        .catch(() => setProjectRepos([]));
      listServices(selectedProjectId)
        .then((res) => {
          if (res.success) setServices(res.data);
          else setServices([]);
        })
        .catch(() => setServices([]));
    } else {
      setProjectRepos([]);
      setServices([]);
    }
  }, [selectedProjectId]);

  // 분석 완료 시 새 프로젝트로 전환하고 그래프 다시 로드
  useEffect(() => {
    if (analysis.completedProjectId) {
      setSelectedProjectId(analysis.completedProjectId);
      // 그래프 강제 리로드 (같은 프로젝트가 이미 선택된 경우에도)
      graph.loadGraph(analysis.completedProjectId);
      // 프로젝트 목록 새로고침
      listProjects()
        .then((res) => {
          if (res.success) setProjects(res.data);
        })
        .catch(() => {});
      // 레포 및 서비스 새로고침
      getProjectDetail(analysis.completedProjectId)
        .then((res) => {
          if (res.success && res.data.repos) {
            setProjectRepos(res.data.repos);
          }
        })
        .catch(() => {});
      listServices(analysis.completedProjectId)
        .then((res) => {
          if (res.success) setServices(res.data);
        })
        .catch(() => {});
    }
  }, [analysis.completedProjectId]);

  const handleAddRepo = useCallback(
    async (gitUrl: string, serviceId?: string) => {
      if (!selectedProjectId) return;
      try {
        const res = await addRepo(selectedProjectId, { gitUrl, serviceId });
        if (res.success) {
          setProjectRepos((prev) => [...prev, res.data]);
        }
      } catch {
        // 무시 — 중복일 수 있음
      }
    },
    [selectedProjectId]
  );

  const handleRemoveRepo = useCallback(
    async (repoId: string) => {
      if (!selectedProjectId) return;
      try {
        await removeRepoApi(selectedProjectId, repoId);
        setProjectRepos((prev) => prev.filter((r) => r.id !== repoId));
      } catch {
        // 무시
      }
    },
    [selectedProjectId]
  );

  const [showCreateProject, setShowCreateProject] = useState(false);
  const [newProjectName, setNewProjectName] = useState('');

  const handleCreateProject = useCallback(async () => {
    const name = newProjectName.trim();
    if (!name) return;
    try {
      const res = await createProject({ name });
      if (res.success) {
        setProjects((prev) => [...prev, res.data]);
        setSelectedProjectId(res.data.id);
        setNewProjectName('');
        setShowCreateProject(false);
      }
    } catch {
      // 무시
    }
  }, [newProjectName]);

  const handleDeleteProject = useCallback(
    async (projectId: string) => {
      if (!confirm('프로젝트를 삭제하시겠습니까?')) return;
      try {
        await deleteProject(projectId);
        setProjects((prev) => prev.filter((p) => p.id !== projectId));
        if (selectedProjectId === projectId) {
          setSelectedProjectId(null);
        }
      } catch (err) {
        console.error('프로젝트 삭제 실패:', err);
      }
    },
    [selectedProjectId]
  );

  const handleRenameService = useCallback(
    async (serviceId: string, newName: string) => {
      if (!selectedProjectId) return;
      try {
        await renameService(selectedProjectId, serviceId, newName);
        graph.loadGraph(selectedProjectId);
      } catch {
        // 무시
      }
    },
    [selectedProjectId, graph]
  );

  const handleUploadZipToProject = useCallback(
    async (projectId: string, file: File) => {
      try {
        const { uploadZipToProject } = await import('@/api/analysis');
        const res = await uploadZipToProject(projectId, file);
        if (res.success) {
          // 분석 완료 — 그래프 리로드
          graph.loadGraph(projectId);
          listServices(projectId)
            .then((r) => { if (r.success) setServices(r.data); })
            .catch(() => {});
          listProjects()
            .then((r) => { if (r.success) setProjects(r.data); })
            .catch(() => {});
        }
      } catch {
        // 오류 무시
      }
    },
    [graph]
  );

  const sidebar = (
    <>
      <RepoInput
        onAnalyzeRepo={analysis.analyzeRepo}
        onAnalyzeZip={analysis.analyzeZip}
        onUploadZipToProject={selectedProjectId ? handleUploadZipToProject : undefined}
        onAnalyzeAllRepos={analysis.analyzeAllRepos}
        onAddRepo={selectedProjectId ? handleAddRepo : undefined}
        onRemoveRepo={selectedProjectId ? handleRemoveRepo : undefined}
        isRunning={analysis.isRunning}
        step={analysis.step}
        progress={analysis.progress}
        message={analysis.message}
        error={analysis.error}
        onReset={analysis.reset}
        selectedProjectId={selectedProjectId}
        repos={projectRepos}
        services={services}
      />

      {/* 프로젝트 목록 */}
      <div className="bg-[var(--surface-1)] border border-[var(--border)] rounded-xl p-4 shadow-sm">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-semibold text-[var(--text-secondary)] uppercase tracking-wider">
            프로젝트 목록
          </h2>
          <button
            onClick={() => setShowCreateProject((v) => !v)}
            className="text-xs text-blue-500 hover:text-blue-400 transition-colors font-medium"
          >
            {showCreateProject ? '취소' : '+ 새 프로젝트'}
          </button>
        </div>

        {/* 프로젝트 생성 폼 */}
        {showCreateProject && (
          <div className="mb-3 flex gap-2">
            <input
              type="text"
              value={newProjectName}
              onChange={(e) => setNewProjectName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleCreateProject()}
              placeholder="프로젝트 이름"
              className="flex-1 px-3 py-1.5 text-xs rounded-lg border border-[var(--border)] bg-[var(--input-bg)] text-[var(--text-primary)] placeholder:text-[var(--text-muted)] outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
              autoFocus
            />
            <button
              onClick={handleCreateProject}
              disabled={!newProjectName.trim()}
              className="px-3 py-1.5 text-xs font-medium rounded-lg bg-blue-600 text-white hover:bg-blue-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              생성
            </button>
          </div>
        )}

        {projects.length > 0 ? (
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
                  <div className="flex items-center gap-1 flex-shrink-0">
                    {selectedProjectId === project.id && (
                      <span className="text-[10px] text-blue-500">●</span>
                    )}
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        e.preventDefault();
                        handleDeleteProject(project.id);
                      }}
                      className="w-5 h-5 flex items-center justify-center rounded hover:bg-red-500/10 text-[var(--text-muted)] hover:text-red-500 transition-colors ml-1"
                      title="프로젝트 삭제"
                    >
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <line x1="18" y1="6" x2="6" y2="18" />
                        <line x1="6" y1="6" x2="18" y2="18" />
                      </svg>
                    </button>
                  </div>
                </div>
                <div className="text-[10px] text-[var(--text-muted)] mt-0.5 flex gap-2">
                  {project.repoCount != null && project.repoCount > 0 && (
                    <>
                      <span>{project.repoCount} 레포</span>
                      <span>·</span>
                    </>
                  )}
                  <span>{project.nodeCount} 서비스</span>
                  <span>·</span>
                  <span>{project.edgeCount} 의존성</span>
                </div>
              </button>
            ))}
          </div>
        ) : (
          !showCreateProject && (
            <p className="text-xs text-[var(--text-muted)]">프로젝트가 없습니다. 새 프로젝트를 생성하세요.</p>
          )
        )}
      </div>

    </>
  );

  return (
    <Layout sidebar={sidebar} user={user} onLogin={onLogin} onLogout={onLogout} isAuthenticated={isAuthenticated}>
      <div className="relative h-full">
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
            onNodeClick={(node: D3Node) => setSelectedNode(node)}
          />
        )}

        {/* 오른쪽 상단 오버레이: 그래프 정보 + 사용법 */}
        <div className="absolute top-3 right-3 flex flex-col gap-2 z-30 pointer-events-auto max-w-[220px]">
          {/* 그래프 정보 */}
          {graph.graphData && !graph.isMockData && (
            <div className="bg-[var(--surface-1)] backdrop-blur-sm border border-[var(--border)] rounded-lg px-3 py-2.5 shadow-lg opacity-95">
              <div className="flex items-center gap-2 mb-2">
                <span className="text-[10px] font-semibold text-[var(--text-secondary)] uppercase tracking-wider">그래프 정보</span>
              </div>
              <div className="space-y-1">
                <div className="flex justify-between text-[11px]">
                  <span className="text-[var(--text-muted)]">프로젝트</span>
                  <span className="text-[var(--text-primary)] font-medium truncate max-w-[110px]">
                    {graph.graphData.metadata.projectName}
                  </span>
                </div>
                <div className="flex justify-between text-[11px]">
                  <span className="text-[var(--text-muted)]">서비스</span>
                  <span className="text-[var(--text-primary)] font-medium">{graph.graphData.metadata.totalNodes}</span>
                </div>
                <div className="flex justify-between text-[11px]">
                  <span className="text-[var(--text-muted)]">의존성</span>
                  <span className="text-[var(--text-primary)] font-medium">{graph.graphData.metadata.totalEdges}</span>
                </div>
                {graph.graphData.metadata.languages.length > 0 && (
                  <div className="flex gap-1 flex-wrap pt-1">
                    {graph.graphData.metadata.languages.map((lang) => (
                      <span key={lang} className="text-[9px] px-1.5 py-0.5 rounded bg-[var(--surface-2)] text-[var(--text-secondary)]">
                        {lang}
                      </span>
                    ))}
                  </div>
                )}
                <div className="text-[9px] text-[var(--text-muted)] pt-1">
                  {new Date(graph.graphData.metadata.analyzedAt).toLocaleString('ko-KR', {
                    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
                  })}
                </div>
              </div>
            </div>
          )}

          {/* 사용법 (접을 수 있는 형태) */}
          <details className="bg-[var(--surface-1)] backdrop-blur-sm border border-[var(--border)] rounded-lg shadow-lg opacity-95">
            <summary className="px-3 py-2 text-[10px] font-semibold text-[var(--text-secondary)] uppercase tracking-wider cursor-pointer select-none hover:text-[var(--text-primary)] transition-colors">
              사용법
            </summary>
            <ul className="px-3 pb-2.5 space-y-1 text-[10px] text-[var(--text-muted)]">
              <li>🖱️ 호버 — 서비스 정보</li>
              <li>👆 클릭 — 엣지 하이라이트</li>
              <li>👆👆 더블클릭 — 1-depth 필터</li>
              <li>✋ 드래그 — 노드 이동</li>
              <li>🖱️→ 우클릭 — 고정 해제</li>
              <li>→ 엣지 클릭 — 코드 미리보기</li>
              <li>⚙️ 스크롤 — 줌</li>
            </ul>
          </details>
        </div>
      </div>

      <CodePreviewModal
        edge={selectedEdge}
        projectId={selectedProjectId ?? 'demo-project'}
        isDark={isDark}
        onClose={() => setSelectedEdge(null)}
      />

      {selectedNode && (
        <ServiceDetailPopup
          node={selectedNode}
          onClose={() => setSelectedNode(null)}
          onRename={handleRenameService}
        />
      )}
    </Layout>
  );
};

export default App;
