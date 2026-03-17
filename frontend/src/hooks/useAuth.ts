import { useState, useEffect, useCallback } from 'react';
import apiClient from '../api/client';

interface User {
  id: string;
  login: string;
  name: string | null;
  email: string | null;
  avatarUrl: string | null;
}

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

export function useAuth() {
  const [authState, setAuthState] = useState<AuthState>({
    user: null,
    isAuthenticated: false,
    isLoading: true,
  });

  const checkAuth = useCallback(async () => {
    const token = localStorage.getItem('auth_token');
    if (!token) {
      setAuthState({ user: null, isAuthenticated: false, isLoading: false });
      return;
    }
    try {
      const response = await apiClient.get('/auth/me');
      if (response.data.success && response.data.data) {
        setAuthState({ user: response.data.data, isAuthenticated: true, isLoading: false });
      } else {
        localStorage.removeItem('auth_token');
        setAuthState({ user: null, isAuthenticated: false, isLoading: false });
      }
    } catch {
      localStorage.removeItem('auth_token');
      setAuthState({ user: null, isAuthenticated: false, isLoading: false });
    }
  }, []);

  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

  const login = useCallback(() => {
    window.location.href = `${import.meta.env.VITE_API_BASE_URL?.replace('/api/v1', '') || 'http://localhost:8080'}/oauth2/authorization/github`;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('auth_token');
    setAuthState({ user: null, isAuthenticated: false, isLoading: false });
  }, []);

  return { ...authState, login, logout, checkAuth };
}
