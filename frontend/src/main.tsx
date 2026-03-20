import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './index.css';

// 첫 렌더링 전에 테마를 초기화하여 깜빡임 방지
const stored = localStorage.getItem('msa-graph-theme');
const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
const theme = stored ?? (prefersDark ? 'dark' : 'light');
document.documentElement.setAttribute('data-theme', theme);

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
