import apiClient from './client';
import type {
  AddRepoRequest,
  ApiResponse,
  CreateProjectRequest,
  GraphData,
  Project,
  ProjectRepo,
  SourceDetail,
  AnalyzeResponse,
} from '@/types';

export const listProjects = async (): Promise<ApiResponse<Project[]>> => {
  const response = await apiClient.get<ApiResponse<Project[]>>('/projects');
  return response.data;
};

export const getProjectDetail = async (id: string): Promise<ApiResponse<Project>> => {
  const response = await apiClient.get<ApiResponse<Project>>(`/projects/${id}`);
  return response.data;
};

export const getProjectGraph = async (projectId: string): Promise<ApiResponse<GraphData>> => {
  const response = await apiClient.get<ApiResponse<GraphData>>(`/projects/${projectId}/graph`);
  return response.data;
};

export const getDependencySource = async (
  projectId: string,
  depId: string
): Promise<ApiResponse<SourceDetail>> => {
  const response = await apiClient.get<ApiResponse<SourceDetail>>(
    `/projects/${projectId}/dependencies/${depId}/source`
  );
  return response.data;
};

export const createProject = async (
  data: CreateProjectRequest
): Promise<ApiResponse<Project>> => {
  const slug = data.slug ?? data.name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  const response = await apiClient.post<ApiResponse<Project>>('/projects', { ...data, slug });
  return response.data;
};

export const addRepo = async (
  projectId: string,
  data: AddRepoRequest
): Promise<ApiResponse<ProjectRepo>> => {
  const response = await apiClient.post<ApiResponse<ProjectRepo>>(
    `/projects/${projectId}/repos`,
    data
  );
  return response.data;
};

export const removeRepo = async (
  projectId: string,
  repoId: string
): Promise<ApiResponse<void>> => {
  const response = await apiClient.delete<ApiResponse<void>>(
    `/projects/${projectId}/repos/${repoId}`
  );
  return response.data;
};

export const analyzeProject = async (
  projectId: string
): Promise<ApiResponse<AnalyzeResponse>> => {
  const response = await apiClient.post<ApiResponse<AnalyzeResponse>>(
    `/projects/${projectId}/analyze`
  );
  return response.data;
};

export const analyzeSingleRepo = async (
  projectId: string,
  repoId: string
): Promise<ApiResponse<AnalyzeResponse>> => {
  const response = await apiClient.post<ApiResponse<AnalyzeResponse>>(
    `/projects/${projectId}/repos/${repoId}/analyze`
  );
  return response.data;
};
