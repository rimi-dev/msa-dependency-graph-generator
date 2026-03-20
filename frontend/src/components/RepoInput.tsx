import React, { useState, useRef, useCallback } from 'react';
import { ProgressBar } from './ProgressBar';
import type { AnalysisStep, ProjectRepo, ServiceInfo } from '@/types';

interface RepoInputProps {
  onAnalyzeRepo: (url: string, projectId?: string) => void;
  onAnalyzeZip: (file: File) => void;
  onUploadZipToProject?: (projectId: string, file: File) => void;
  onAnalyzeAllRepos?: (projectId: string) => void;
  onAddRepo?: (gitUrl: string, serviceId?: string) => void;
  services?: ServiceInfo[];
  onRemoveRepo?: (repoId: string) => void;
  isRunning: boolean;
  step: AnalysisStep | null;
  progress: number;
  message: string;
  error: string | null;
  onReset: () => void;
  selectedProjectId: string | null;
  repos?: ProjectRepo[];
}

const GITHUB_URL_REGEX = /^https?:\/\/(www\.)?github\.com\/[\w.-]+\/[\w.-]+(\.git)?$/;

const STATUS_BADGES: Record<string, { label: string; color: string }> = {
  PENDING: { label: '대기', color: 'text-gray-400 bg-gray-500/10' },
  INGESTING: { label: '수집중', color: 'text-yellow-500 bg-yellow-500/10' },
  ANALYZING: { label: '분석중', color: 'text-blue-500 bg-blue-500/10' },
  READY: { label: '완료', color: 'text-green-500 bg-green-500/10' },
  ERROR: { label: '오류', color: 'text-red-500 bg-red-500/10' },
};

export const RepoInput: React.FC<RepoInputProps> = ({
  onAnalyzeRepo,
  onAnalyzeZip,
  onUploadZipToProject,
  onAnalyzeAllRepos,
  onAddRepo,
  onRemoveRepo,
  services,
  isRunning,
  step,
  progress,
  message,
  error,
  onReset,
  selectedProjectId,
  repos,
}) => {
  const [repoUrl, setRepoUrl] = useState('');
  const [urlError, setUrlError] = useState('');
  const [selectedServiceId, setSelectedServiceId] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [droppedFile, setDroppedFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const isProjectMode = !!selectedProjectId && !!repos;

  const validateUrl = (url: string): boolean => {
    if (!url) {
      setUrlError('GitHub URL을 입력하세요.');
      return false;
    }
    if (!GITHUB_URL_REGEX.test(url)) {
      setUrlError('올바른 GitHub 리포지토리 URL을 입력하세요.');
      return false;
    }
    setUrlError('');
    return true;
  };

  const handleAnalyze = () => {
    if (droppedFile) {
      if (isProjectMode && onUploadZipToProject && selectedProjectId) {
        onUploadZipToProject(selectedProjectId, droppedFile);
      } else {
        onAnalyzeZip(droppedFile);
      }
    } else if (isProjectMode) {
      if (onAnalyzeAllRepos && selectedProjectId) {
        onAnalyzeAllRepos(selectedProjectId);
      }
    } else if (validateUrl(repoUrl)) {
      onAnalyzeRepo(repoUrl);
    }
  };

  const handleAddRepo = () => {
    if (validateUrl(repoUrl) && onAddRepo) {
      onAddRepo(repoUrl, selectedServiceId ?? undefined);
      setRepoUrl('');
      setSelectedServiceId(null);
    }
  };

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  }, []);

  const handleDragLeave = useCallback(() => {
    setIsDragging(false);
  }, []);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files[0];
    if (file && (file.name.endsWith('.zip') || file.type === 'application/zip')) {
      setDroppedFile(file);
      setRepoUrl('');
      setUrlError('');
    }
  }, []);

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setDroppedFile(file);
      setRepoUrl('');
      setUrlError('');
    }
  };

  const removeFile = (e: React.MouseEvent) => {
    e.stopPropagation();
    setDroppedFile(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const isCompleted = step === 'COMPLETED';
  const canStart = !isRunning && (!!droppedFile || (isProjectMode ? (repos && repos.length > 0) : !!repoUrl));

  return (
    <div className="bg-[var(--surface-1)] border border-[var(--border)] rounded-xl p-4 shadow-sm">
      <h2 className="text-sm font-semibold text-[var(--text-secondary)] uppercase tracking-wider mb-3">
        {isProjectMode ? '레포지토리 관리' : '리포지토리 분석'}
      </h2>

      {/* Project Mode: Repo list with status */}
      {isProjectMode && repos && (
        <div className="mb-3">
          {repos.length > 0 ? (
            <div className="space-y-1.5 mb-3 max-h-48 overflow-y-auto">
              {repos.map((repo) => {
                const badge = STATUS_BADGES[repo.status] ?? STATUS_BADGES.PENDING;
                return (
                  <div
                    key={repo.id}
                    className="flex items-center gap-2 px-2.5 py-2 rounded-lg bg-[var(--surface-2)] text-xs"
                  >
                    <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium ${badge.color}`}>
                      {badge.label}
                    </span>
                    <span className="flex-1 truncate text-[var(--text-secondary)]" title={repo.gitUrl}>
                      {repo.gitUrl.replace(/^https?:\/\/(www\.)?github\.com\//, '')}
                    </span>
                    {onRemoveRepo && !isRunning && (
                      <button
                        onClick={() => onRemoveRepo(repo.id)}
                        className="text-[var(--text-muted)] hover:text-red-500 transition-colors flex-shrink-0"
                        title="레포 제거"
                      >
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <line x1="18" y1="6" x2="6" y2="18" />
                          <line x1="6" y1="6" x2="18" y2="18" />
                        </svg>
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          ) : (
            <p className="text-xs text-[var(--text-muted)] mb-3">등록된 레포가 없습니다.</p>
          )}
        </div>
      )}

      {/* URL Input */}
      <div className="mb-3">
        <label className="text-xs text-[var(--text-secondary)] mb-1 block">GitHub URL</label>
        <div className="relative flex gap-2">
          <div className="relative flex-1">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--text-muted)]">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z" />
              </svg>
            </span>
            <input
              type="url"
              value={repoUrl}
              onChange={(e) => {
                setRepoUrl(e.target.value);
                if (urlError) validateUrl(e.target.value);
                if (e.target.value) setDroppedFile(null);
              }}
              onBlur={() => repoUrl && validateUrl(repoUrl)}
              placeholder="https://github.com/owner/repository"
              disabled={isRunning || (!isProjectMode && !!droppedFile)}
              className={`w-full pl-9 pr-3 py-2 text-sm rounded-lg border bg-[var(--input-bg)] text-[var(--text-primary)] placeholder:text-[var(--text-muted)] transition-colors
                ${urlError ? 'border-red-500 focus:ring-red-500/30' : 'border-[var(--border)] focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20'}
                disabled:opacity-50 disabled:cursor-not-allowed outline-none
              `}
            />
          </div>
          {isProjectMode && onAddRepo && (
            <button
              onClick={handleAddRepo}
              disabled={isRunning || !repoUrl}
              className="px-3 py-2 text-xs font-medium rounded-lg border border-[var(--border)] text-[var(--text-secondary)] hover:bg-[var(--surface-2)] transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex-shrink-0"
            >
              추가
            </button>
          )}
        </div>
        {urlError && <p className="mt-1 text-xs text-red-500">{urlError}</p>}
      </div>

      {/* Service selector (project mode only) */}
      {isProjectMode && services && services.length > 0 && (
        <div className="mb-3">
          <label className="text-xs text-[var(--text-secondary)] mb-1 block">서비스 연결 (선택)</label>
          <select
            value={selectedServiceId ?? ''}
            onChange={(e) => setSelectedServiceId(e.target.value || null)}
            disabled={isRunning}
            className="w-full px-3 py-2 text-sm rounded-lg border border-[var(--border)] bg-[var(--input-bg)] text-[var(--text-primary)] outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <option value="">없음 — 자동 감지</option>
            {services.map((svc) => (
              <option key={svc.id} value={svc.id}>
                {svc.name}
              </option>
            ))}
          </select>
        </div>
      )}

      {/* ZIP Drop Zone */}
      <div className="flex items-center gap-2 mb-3">
        <div className="flex-1 h-px bg-[var(--border)]" />
        <span className="text-xs text-[var(--text-muted)]">또는 ZIP 업로드</span>
        <div className="flex-1 h-px bg-[var(--border)]" />
      </div>

      <div
        className={`relative border-2 border-dashed rounded-lg p-4 text-center cursor-pointer transition-all duration-200
          ${isDragging ? 'border-blue-500 bg-blue-500/5 scale-[1.02]' : 'border-[var(--border)] hover:border-[var(--border-hover)]'}
          ${droppedFile ? 'border-green-500 bg-green-500/5' : ''}
          ${isRunning ? 'pointer-events-none opacity-50' : ''}
        `}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={() => !droppedFile && fileInputRef.current?.click()}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".zip"
          className="hidden"
          onChange={handleFileSelect}
        />

        {droppedFile ? (
          <div className="flex items-center justify-center gap-2">
            <span className="text-green-500">📦</span>
            <span className="text-sm font-medium text-green-600 dark:text-green-400 truncate max-w-[160px]">
              {droppedFile.name}
            </span>
            <button
              onClick={removeFile}
              className="text-[var(--text-muted)] hover:text-red-500 transition-colors ml-1"
            >
              ✕
            </button>
          </div>
        ) : (
          <div>
            <div className="text-2xl mb-1">{isDragging ? '📂' : '📁'}</div>
            <p className="text-xs text-[var(--text-secondary)]">
              ZIP 파일을 드래그하거나{' '}
              <span className="text-blue-500 underline">클릭하여 선택</span>
            </p>
          </div>
        )}
      </div>

      {/* Action Buttons */}
      <div className="mt-3 flex gap-2">
        {isCompleted || error ? (
          <button
            onClick={onReset}
            className="flex-1 py-2 text-sm font-medium rounded-lg border border-[var(--border)] text-[var(--text-secondary)] hover:bg-[var(--surface-2)] transition-colors"
          >
            초기화
          </button>
        ) : null}
        <button
          onClick={handleAnalyze}
          disabled={!canStart}
          className={`flex-1 py-2 text-sm font-semibold rounded-lg transition-all duration-200
            ${canStart ? 'bg-blue-600 hover:bg-blue-500 text-white shadow-lg shadow-blue-600/20 active:scale-[0.98]' : 'bg-[var(--surface-2)] text-[var(--text-muted)] cursor-not-allowed'}
          `}
        >
          {isRunning ? (
            <span className="flex items-center justify-center gap-2">
              <svg
                className="animate-spin w-4 h-4"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
              >
                <circle cx="12" cy="12" r="10" strokeOpacity="0.25" />
                <path d="M12 2a10 10 0 0 1 10 10" />
              </svg>
              분석 중...
            </span>
          ) : droppedFile ? (
            'ZIP 분석 시작'
          ) : isProjectMode ? (
            `전체 분석 (${repos?.length ?? 0}개 레포)`
          ) : (
            '분석 시작'
          )}
        </button>
      </div>

      {/* Progress */}
      <ProgressBar step={step} progress={progress} message={message} error={error} />
    </div>
  );
};
