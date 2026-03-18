import { useState, useCallback, useRef } from 'react';
import { startAnalysis, startAnalysisWithZip, getJobStatus } from '@/api/analysis';
import { analyzeProject as analyzeProjectApi } from '@/api/projects';
import { subscribeJobProgress } from '@/api/websocket';
import type { AnalysisStep, JobStatus } from '@/types';

interface AnalysisState {
  jobId: string | null;
  step: AnalysisStep | null;
  progress: number;
  message: string;
  isRunning: boolean;
  error: string | null;
  completedProjectId: string | null;
}

const STEP_ORDER: AnalysisStep[] = ['CLONING', 'SCANNING', 'ANALYZING', 'PERSISTING', 'COMPLETED'];

const stepToProgress = (step: AnalysisStep): number => {
  const idx = STEP_ORDER.indexOf(step);
  if (idx < 0) return 0;
  return Math.round(((idx + 1) / STEP_ORDER.length) * 100);
};

export const useAnalysis = () => {
  const [state, setState] = useState<AnalysisState>({
    jobId: null,
    step: null,
    progress: 0,
    message: '',
    isRunning: false,
    error: null,
    completedProjectId: null,
  });

  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const unsubscribeRef = useRef<(() => void) | null>(null);

  const stopPolling = useCallback(() => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
    if (unsubscribeRef.current) {
      unsubscribeRef.current();
      unsubscribeRef.current = null;
    }
  }, []);

  const handleJobUpdate = useCallback(
    (status: JobStatus) => {
      setState((prev) => ({
        ...prev,
        step: status.step,
        progress: status.progress ?? stepToProgress(status.step),
        message: status.message,
        isRunning: status.step !== 'COMPLETED' && status.step !== 'FAILED',
        error: status.step === 'FAILED' ? (status.error ?? 'Analysis failed') : null,
        completedProjectId:
          status.step === 'COMPLETED' ? (status.projectId ?? prev.completedProjectId) : prev.completedProjectId,
      }));

      if (status.step === 'COMPLETED' || status.step === 'FAILED') {
        stopPolling();
      }
    },
    [stopPolling]
  );

  const pollJobStatus = useCallback(
    (jobId: string) => {
      pollingRef.current = setInterval(async () => {
        try {
          const res = await getJobStatus(jobId);
          if (res.success) {
            handleJobUpdate(res.data);
          }
        } catch {
          // Ignore transient errors
        }
      }, 2000);
    },
    [handleJobUpdate]
  );

  const startJob = useCallback(
    async (jobId: string) => {
      setState((prev) => ({
        ...prev,
        jobId,
        step: 'CLONING',
        progress: 0,
        message: '리포지토리를 클로닝하는 중...',
        isRunning: true,
        error: null,
        completedProjectId: null,
      }));

      // Try WebSocket first, fall back to polling
      try {
        const unsub = await subscribeJobProgress(jobId, handleJobUpdate);
        unsubscribeRef.current = unsub;
      } catch {
        // WebSocket failed, will rely on polling
      }

      // Always start polling as a safety net
      pollJobStatus(jobId);
    },
    [handleJobUpdate, pollJobStatus]
  );

  const analyzeRepo = useCallback(
    async (repoUrl: string, projectId?: string) => {
      try {
        const res = await startAnalysis({ repoUrl, projectId });
        if (res.success) {
          await startJob(res.data.jobId);
        } else {
          setState((prev) => ({
            ...prev,
            error: res.error?.message ?? 'Analysis failed to start',
          }));
        }
      } catch (err) {
        setState((prev) => ({
          ...prev,
          error: err instanceof Error ? err.message : 'Network error',
          isRunning: false,
        }));
      }
    },
    [startJob]
  );

  const analyzeAllRepos = useCallback(
    async (projectId: string) => {
      try {
        const res = await analyzeProjectApi(projectId);
        if (res.success) {
          await startJob(res.data.jobId);
        } else {
          setState((prev) => ({
            ...prev,
            error: res.error?.message ?? 'Analysis failed to start',
          }));
        }
      } catch (err) {
        setState((prev) => ({
          ...prev,
          error: err instanceof Error ? err.message : 'Network error',
          isRunning: false,
        }));
      }
    },
    [startJob]
  );

  const analyzeZip = useCallback(
    async (file: File) => {
      try {
        const res = await startAnalysisWithZip(file);
        if (res.success) {
          await startJob(res.data.jobId);
        } else {
          setState((prev) => ({
            ...prev,
            error: res.error?.message ?? 'Analysis failed to start',
          }));
        }
      } catch (err) {
        setState((prev) => ({
          ...prev,
          error: err instanceof Error ? err.message : 'Network error',
          isRunning: false,
        }));
      }
    },
    [startJob]
  );

  const reset = useCallback(() => {
    stopPolling();
    setState({
      jobId: null,
      step: null,
      progress: 0,
      message: '',
      isRunning: false,
      error: null,
      completedProjectId: null,
    });
  }, [stopPolling]);

  return {
    ...state,
    analyzeRepo,
    analyzeAllRepos,
    analyzeZip,
    reset,
  };
};
