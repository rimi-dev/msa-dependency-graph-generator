import apiClient from './client';
import type { ApiResponse, AnalyzeRequest, AnalyzeResponse, JobStatus } from '@/types';

export const startAnalysis = async (data: AnalyzeRequest): Promise<ApiResponse<AnalyzeResponse>> => {
  const response = await apiClient.post<ApiResponse<AnalyzeResponse>>('/analyze', data);
  return response.data;
};

export const startAnalysisWithZip = async (file: File): Promise<ApiResponse<AnalyzeResponse>> => {
  const formData = new FormData();
  formData.append('file', file);
  const response = await apiClient.post<ApiResponse<AnalyzeResponse>>('/analyze', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
};

export const getJobStatus = async (jobId: string): Promise<ApiResponse<JobStatus>> => {
  const response = await apiClient.get<ApiResponse<JobStatus>>(`/jobs/${jobId}`);
  return response.data;
};
