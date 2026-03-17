import React from 'react';
import { useTheme } from '@/hooks/useTheme';

interface User {
  id: string;
  login: string;
  name: string | null;
  email: string | null;
  avatarUrl: string | null;
}

interface LayoutProps {
  children: React.ReactNode;
  sidebar: React.ReactNode;
  user?: User | null;
  onLogin?: () => void;
  onLogout?: () => void;
  isAuthenticated?: boolean;
}

export const Layout: React.FC<LayoutProps> = ({ children, sidebar, user, onLogin, onLogout, isAuthenticated }) => {
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

          {/* User info */}
          {user ? (
            <div className="flex items-center gap-2 ml-2 pl-2 border-l border-[var(--border)]">
              {user.avatarUrl && (
                <img
                  src={user.avatarUrl}
                  alt={user.login}
                  className="w-7 h-7 rounded-full"
                />
              )}
              <span className="text-xs text-[var(--text-secondary)] font-medium">
                {user.login}
              </span>
              <button
                onClick={onLogout}
                className="text-xs px-2 py-1 rounded-md hover:bg-[var(--surface-2)] transition-colors text-[var(--text-muted)]"
                title="로그아웃"
              >
                Logout
              </button>
            </div>
          ) : (
            <button
              onClick={onLogin}
              className="flex items-center gap-1.5 ml-2 pl-2 border-l border-[var(--border)] text-xs px-2.5 py-1.5 rounded-md hover:bg-[var(--surface-2)] transition-colors text-[var(--text-secondary)] font-medium"
              title="GitHub으로 로그인하면 private 저장소도 분석할 수 있습니다"
            >
              <svg height="14" width="14" viewBox="0 0 16 16" fill="currentColor">
                <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/>
              </svg>
              Sign in
            </button>
          )}
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
