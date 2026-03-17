import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

// Initialize theme before first render to prevent flash
const stored = localStorage.getItem('msa-graph-theme');
const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
const theme = stored ?? (prefersDark ? 'dark' : 'light');
document.documentElement.setAttribute('data-theme', theme);

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
