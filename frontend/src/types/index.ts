// ─── API Response Wrappers ────────────────────────────────────────────────────

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  timestamp: string;
  error?: ApiError;
}

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
  traceId: string;
}

// ─── Graph Data ───────────────────────────────────────────────────────────────

export interface GraphData {
  nodes: ServiceNode[];
  edges: DependencyEdge[];
  metadata: GraphMetadata;
}

export interface ServiceNode {
  id: string;
  displayName: string;
  language: string;
  framework?: string;
  dependencyCount: { outgoing: number; incoming: number };
  repoId?: string;
  repoUrl?: string;
}

export type Protocol = 'HTTP' | 'gRPC' | 'MQ';

export interface DependencyEdge {
  id: string;
  source: string;
  target: string;
  protocol: Protocol;
  method?: string;
  endpoint?: string;
  confidence: number;
  detectedBy: string;
  sourceLocationCount: number;
}

export interface GraphMetadata {
  projectId: string;
  projectName: string;
  analyzedAt: string;
  totalNodes: number;
  totalEdges: number;
  languages: string[];
}

// ─── Source Detail ────────────────────────────────────────────────────────────

export interface SourceDetail {
  dependency: {
    id: string;
    source: string;
    target: string;
    protocol: string;
  };
  locations: SourceLocationDetail[];
  relatedConfig: ConfigEntry[];
}

export interface SourceLocationDetail {
  id: string;
  filePath: string;
  startLine: number;
  endLine: number;
  content: string;
  language: string;
  githubUrl?: string;
  highlightLines: number[];
}

export interface ConfigEntry {
  filePath: string;
  key: string;
  value: string;
  githubUrl?: string;
}

// ─── Job / Analysis ───────────────────────────────────────────────────────────

export type AnalysisStep =
  | 'CLONING'
  | 'SCANNING'
  | 'ANALYZING'
  | 'PERSISTING'
  | 'COMPLETED'
  | 'FAILED';

export interface JobStatus {
  jobId: string;
  projectId?: string;
  step: AnalysisStep;
  progress: number;
  message: string;
  createdAt: string;
  updatedAt: string;
  error?: string;
}

export interface AnalyzeRequest {
  repoUrl?: string;
  projectId?: string;
}

export interface AnalyzeResponse {
  jobId: string;
  message: string;
}

// ─── Project ──────────────────────────────────────────────────────────────────

export type RepoStatus = 'PENDING' | 'INGESTING' | 'ANALYZING' | 'READY' | 'ERROR';

export interface ProjectRepo {
  id: string;
  gitUrl: string;
  branch?: string;
  status: RepoStatus;
  lastAnalyzedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Project {
  id: string;
  name: string;
  repoUrl?: string;
  repoCount?: number;
  repos?: ProjectRepo[];
  language?: string;
  createdAt: string;
  updatedAt: string;
  nodeCount: number;
  edgeCount: number;
}

export interface CreateProjectRequest {
  name: string;
  slug?: string;
  description?: string;
}

export interface AddRepoRequest {
  gitUrl: string;
  branch?: string;
}

// ─── D3 Graph Internal Types ──────────────────────────────────────────────────

import type * as d3 from 'd3';

export interface D3Node extends d3.SimulationNodeDatum {
  id: string;
  displayName: string;
  language: string;
  framework?: string;
  dependencyCount: { outgoing: number; incoming: number };
  repoId?: string;
  repoUrl?: string;
  pinned?: boolean;
}

export interface D3Link extends d3.SimulationLinkDatum<D3Node> {
  id: string;
  source: string | D3Node;
  target: string | D3Node;
  protocol: Protocol;
  method?: string;
  endpoint?: string;
  confidence: number;
  detectedBy: string;
  sourceLocationCount: number;
}

// ─── UI State ─────────────────────────────────────────────────────────────────

export type LayoutType = 'force' | 'dagre' | 'radial';

export interface GraphFilter {
  protocols: Set<Protocol>;
  selectedNodeId: string | null;
  depthFilter: string | null;
}

export interface TooltipState {
  visible: boolean;
  x: number;
  y: number;
  content: TooltipContent | null;
}

export type TooltipContent =
  | { type: 'node'; node: D3Node }
  | { type: 'edge'; edge: D3Link };

export interface ContextMenuState {
  visible: boolean;
  x: number;
  y: number;
  nodeId: string | null;
}
