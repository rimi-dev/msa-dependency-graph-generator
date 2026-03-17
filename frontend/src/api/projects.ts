import apiClient from './client';
import type { ApiResponse, GraphData, Project, SourceDetail } from '@/types';

export const listProjects = async (): Promise<ApiResponse<Project[]>> => {
  const response = await apiClient.get<ApiResponse<Project[]>>('/projects');
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
