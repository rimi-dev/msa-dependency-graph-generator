import React from 'react';
import { useTheme } from '@/hooks/useTheme';

interface LayoutProps {
  children: React.ReactNode;
  sidebar: React.ReactNode;
}

export const Layout: React.FC<LayoutProps> = ({ children, sidebar }) => {
  const { isDark, toggleTheme } = useTheme();

  return (
    <div className="h-screen flex flex-col bg-[var(--bg-primary)] text-[var(--text-primary)] overflow-hidden">
      {/* Header */}
      <header className="flex-shrink-0 h-12 flex items-center justify-between px-4 border-b border-[var(--border)] bg-[var(--surface-1)] z-10 shadow-sm">
        <div className="flex items-center gap-3">
          {/* Logo */}
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center shadow">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5">
                <circle cx="5" cy="12" r="2" />
                <circle cx="19" cy="5" r="2" />
                <circle cx="19" cy="19" r="2" />
                <line x1="7" y1="12" x2="17" y2="6" />
                <line x1="7" y1="12" x2="17" y2="18" />
              </svg>
            </div>
            <div>
              <h1 className="text-sm font-bold text-[var(--text-primary)] leading-none">
                MSA Dependency Graph
              </h1>
              <p className="text-[10px] text-[var(--text-muted)] leading-none mt-0.5">
                마이크로서비스 의존성 분석기
              </p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Theme toggle */}
          <button
            onClick={toggleTheme}
            className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-[var(--surface-2)] transition-colors text-[var(--text-secondary)] text-sm"
            title={isDark ? '라이트 모드로 전환' : '다크 모드로 전환'}
          >
            {isDark ? '☀️' : '🌙'}
          </button>
        </div>
      </header>

      {/* Main */}
      <div className="flex-1 flex overflow-hidden">
        {/* Sidebar */}
        <aside className="w-72 flex-shrink-0 border-r border-[var(--border)] bg-[var(--surface-1)] overflow-y-auto">
          <div className="p-3 space-y-3">
            {sidebar}
          </div>
        </aside>

        {/* Content */}
        <main className="flex-1 overflow-hidden">
          {children}
        </main>
      </div>
    </div>
  );
};
