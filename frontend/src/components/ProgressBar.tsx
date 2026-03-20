import React from 'react';
import type { AnalysisStep } from '@/types';

interface ProgressBarProps {
  step: AnalysisStep | null;
  progress: number;
  message: string;
  error: string | null;
}

const STEPS: { key: AnalysisStep; label: string; icon: string }[] = [
  { key: 'CLONING', label: '클로닝', icon: '📥' },
  { key: 'SCANNING', label: '스캔', icon: '🔍' },
  { key: 'ANALYZING', label: '분석', icon: '⚙️' },
  { key: 'PERSISTING', label: '저장', icon: '💾' },
  { key: 'COMPLETED', label: '완료', icon: '✅' },
];

export const ProgressBar: React.FC<ProgressBarProps> = ({ step, progress, message, error }) => {
  // FAILED는 STEPS에 없으므로, 에러 시 마지막 진행 step을 기준으로 표시
  const isFailed = step === 'FAILED' || !!error;
  const displayStep = step === 'FAILED' ? 'CLONING' : step;
  const currentStepIdx = displayStep ? STEPS.findIndex((s) => s.key === displayStep) : -1;

  if (step === null) return null;

  return (
    <div className="mt-4 space-y-3">
      {/* Step indicators */}
      <div className="flex items-center justify-between">
        {STEPS.map((s, i) => {
          const isDone = currentStepIdx > i;
          const isCurrent = currentStepIdx === i;
          const isStepFailed = isFailed && isCurrent;

          return (
            <React.Fragment key={s.key}>
              <div className="flex flex-col items-center gap-1">
                <div
                  className={`w-8 h-8 rounded-full flex items-center justify-center text-sm transition-all duration-300
                    ${isStepFailed ? 'bg-red-500 text-white shadow-lg shadow-red-500/30' : ''}
                    ${isDone ? 'bg-green-500 text-white shadow-lg shadow-green-500/30' : ''}
                    ${isCurrent && !isStepFailed ? 'bg-blue-500 text-white shadow-lg shadow-blue-500/30 animate-pulse' : ''}
                    ${!isDone && !isCurrent ? 'bg-[var(--surface-2)] text-[var(--text-muted)]' : ''}
                  `}
                >
                  {isDone ? '✓' : isStepFailed ? '✕' : s.icon}
                </div>
                <span
                  className={`text-[10px] font-medium
                    ${isCurrent ? 'text-blue-500' : isDone ? 'text-green-500' : 'text-[var(--text-muted)]'}
                  `}
                >
                  {s.label}
                </span>
              </div>

              {i < STEPS.length - 1 && (
                <div className="flex-1 h-0.5 mx-1 rounded-full overflow-hidden bg-[var(--surface-2)]">
                  <div
                    className={`h-full transition-all duration-500 rounded-full
                      ${isDone ? 'bg-green-500 w-full' : 'bg-[var(--surface-2)] w-0'}
                    `}
                  />
                </div>
              )}
            </React.Fragment>
          );
        })}
      </div>

      {/* Progress bar */}
      <div className="relative">
        <div className="w-full h-2 bg-[var(--surface-2)] rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full transition-all duration-500 ease-out
              ${error ? 'bg-red-500' : step === 'COMPLETED' ? 'bg-green-500' : 'bg-blue-500'}
            `}
            style={{ width: `${progress}%` }}
          />
        </div>
        <span className="absolute right-0 -top-5 text-xs text-[var(--text-secondary)]">
          {progress}%
        </span>
      </div>

      {/* Message */}
      {(message || error) && (
        <p
          className={`text-xs px-2 py-1.5 rounded ${
            error
              ? 'bg-red-500/10 text-red-500 border border-red-500/20'
              : 'text-[var(--text-secondary)]'
          }`}
        >
          {error ?? message}
        </p>
      )}
    </div>
  );
};
