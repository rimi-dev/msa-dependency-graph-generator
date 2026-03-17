import { useState, useCallback, useEffect } from 'react';
import { getProjectGraph } from '@/api/projects';
import { subscribeGraphUpdates } from '@/api/websocket';
import type { GraphData, D3Node, D3Link } from '@/types';
import { convertGraphData } from '@/utils/graph';
import { MOCK_GRAPH_DATA } from '@/utils/mockData';

interface GraphState {
  graphData: GraphData | null;
  nodes: D3Node[];
  links: D3Link[];
  isLoading: boolean;
  error: string | null;
  isMockData: boolean;
}

export const useGraph = (projectId: string | null) => {
  const [state, setState] = useState<GraphState>({
    graphData: null,
    nodes: [],
    links: [],
    isLoading: false,
    error: null,
    isMockData: false,
  });

  const loadGraph = useCallback(async (id: string) => {
    setState((prev) => ({ ...prev, isLoading: true, error: null }));
    try {
      const res = await getProjectGraph(id);
      if (res.success) {
        const { nodes, links } = convertGraphData(res.data);
        setState({
          graphData: res.data,
          nodes,
          links,
          isLoading: false,
          error: null,
          isMockData: false,
        });
      } else {
        throw new Error(res.error?.message ?? 'Failed to load graph');
      }
    } catch {
      // Fall back to mock data when backend is unavailable
      const { nodes, links } = convertGraphData(MOCK_GRAPH_DATA);
      setState({
        graphData: MOCK_GRAPH_DATA,
        nodes,
        links,
        isLoading: false,
        error: null,
        isMockData: true,
      });
    }
  }, []);

  const loadMockGraph = useCallback(() => {
    const { nodes, links } = convertGraphData(MOCK_GRAPH_DATA);
    setState({
      graphData: MOCK_GRAPH_DATA,
      nodes,
      links,
      isLoading: false,
      error: null,
      isMockData: true,
    });
  }, []);

  useEffect(() => {
    if (projectId) {
      loadGraph(projectId);
    } else {
      // Load demo data immediately when no project selected
      loadMockGraph();
    }
  }, [projectId, loadGraph, loadMockGraph]);

  useEffect(() => {
    if (!projectId || state.isMockData) return;

    let unsubscribe: (() => void) | null = null;

    subscribeGraphUpdates(projectId, (update) => {
      setState((prev) => {
        if (!prev.graphData) return prev;
        const merged: GraphData = {
          ...prev.graphData,
          nodes: update.nodes ?? prev.graphData.nodes,
          edges: update.edges ?? prev.graphData.edges,
        };
        const { nodes, links } = convertGraphData(merged);
        return { ...prev, graphData: merged, nodes, links };
      });
    })
      .then((unsub) => {
        unsubscribe = unsub;
      })
      .catch(() => {});

    return () => {
      unsubscribe?.();
    };
  }, [projectId, state.isMockData]);

  return {
    ...state,
    loadGraph,
    loadMockGraph,
  };
};
