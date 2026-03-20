export interface LanguageColor {
  bg: string;
  text: string;
  border: string;
}

export const LANGUAGE_COLORS: Record<string, LanguageColor> = {
  javascript: { bg: '#F7DF1E', text: '#1a1a1a', border: '#d4be00' },
  typescript: { bg: '#3178C6', text: '#ffffff', border: '#255fa3' },
  nodejs: { bg: '#339933', text: '#ffffff', border: '#267326' },
  nestjs: { bg: '#E0234E', text: '#ffffff', border: '#b81c3e' },
  java: { bg: '#E76F00', text: '#ffffff', border: '#c05c00' },
  kotlin: { bg: '#7F52FF', text: '#ffffff', border: '#6640d9' },
  python: { bg: '#3776AB', text: '#ffffff', border: '#2c5f8a' },
  go: { bg: '#00ADD8', text: '#ffffff', border: '#008aac' },
  rust: { bg: '#CE412B', text: '#ffffff', border: '#a83323' },
  ruby: { bg: '#CC342D', text: '#ffffff', border: '#a32924' },
  php: { bg: '#777BB4', text: '#ffffff', border: '#5e6292' },
  csharp: { bg: '#178600', text: '#ffffff', border: '#126b00' },
  unknown: { bg: '#6B7280', text: '#ffffff', border: '#4b5563' },
};

export const getLanguageColor = (language: string): LanguageColor => {
  const normalized = language.toLowerCase().replace(/[.\s-]/g, '');
  return LANGUAGE_COLORS[normalized] ?? LANGUAGE_COLORS['unknown'];
};

export const PROTOCOL_COLORS: Record<string, string> = {
  HTTP: '#3B82F6',
};

export const getProtocolColor = (protocol: string): string => {
  return PROTOCOL_COLORS[protocol] ?? '#9CA3AF';
};

export const PROTOCOL_STROKE_DASH: Record<string, string> = {
  HTTP: 'none',
};

export const getProtocolStrokeDash = (protocol: string): string => {
  return PROTOCOL_STROKE_DASH[protocol] ?? 'none';
};

export const getEdgeWidth = (count: number): number => {
  // Map sourceLocationCount (1–10+) to stroke width (1.5px–5px)
  const min = 1.5;
  const max = 5;
  const clampedCount = Math.min(Math.max(count, 1), 10);
  return min + ((clampedCount - 1) / 9) * (max - min);
};
