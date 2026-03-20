import * as d3 from 'd3';
import type { D3Node, D3Link, LayoutType, GraphData } from '@/types';

export const convertGraphData = (data: GraphData): { nodes: D3Node[]; links: D3Link[] } => {
  const nodes: D3Node[] = data.nodes.map((n) => ({
    ...n,
    pinned: false,
  }));

  const links: D3Link[] = data.edges.map((e) => ({
    ...e,
    source: e.source,
    target: e.target,
  }));

  return { nodes, links };
};

export const createForceSimulation = (
  nodes: D3Node[],
  links: D3Link[],
  width: number,
  height: number
): d3.Simulation<D3Node, D3Link> => {
  return d3
    .forceSimulation<D3Node, D3Link>(nodes)
    .force(
      'link',
      d3
        .forceLink<D3Node, D3Link>(links)
        .id((d) => d.id)
        .distance(120)
        .strength(0.5)
    )
    .force('charge', d3.forceManyBody().strength(-400).distanceMax(400))
    .force('center', d3.forceCenter(width / 2, height / 2))
    .force('collision', d3.forceCollide().radius(50))
    .force('x', d3.forceX(width / 2).strength(0.05))
    .force('y', d3.forceY(height / 2).strength(0.05))
    .alphaDecay(0.02)
    .velocityDecay(0.4);
};

export const applyRadialLayout = (
  nodes: D3Node[],
  links: D3Link[],
  width: number,
  height: number
): void => {
  const cx = width / 2;
  const cy = height / 2;
  const radius = Math.min(width, height) * 0.38;
  const n = nodes.length;

  nodes.forEach((node, i) => {
    const angle = (i / n) * 2 * Math.PI - Math.PI / 2;
    node.x = cx + radius * Math.cos(angle);
    node.y = cy + radius * Math.sin(angle);
    node.fx = node.x;
    node.fy = node.y;
  });

  void links;
};

export const applyDagreLayout = (
  nodes: D3Node[],
  links: D3Link[],
  width: number,
  height: number
): void => {
  // dagre 의존성 없이 간단한 계층적 레이아웃 구현
  // 레벨을 찾기 위한 인접 관계 구성
  const nodeMap = new Map(nodes.map((n) => [n.id, n]));
  const inDegree = new Map(nodes.map((n) => [n.id, 0]));

  links.forEach((l) => {
    const targetId = typeof l.target === 'string' ? l.target : (l.target as D3Node).id;
    inDegree.set(targetId, (inDegree.get(targetId) ?? 0) + 1);
  });

  // 위상 정렬 기반 레벨 할당
  const levels = new Map<string, number>();
  const queue: string[] = [];

  inDegree.forEach((deg, id) => {
    if (deg === 0) queue.push(id);
  });

  queue.forEach((id) => levels.set(id, 0));

  const visited = new Set<string>();
  while (queue.length > 0) {
    const id = queue.shift()!;
    if (visited.has(id)) continue;
    visited.add(id);

    const currentLevel = levels.get(id) ?? 0;
    links.forEach((l) => {
      const sourceId = typeof l.source === 'string' ? l.source : (l.source as D3Node).id;
      const targetId = typeof l.target === 'string' ? l.target : (l.target as D3Node).id;
      if (sourceId === id) {
        const existing = levels.get(targetId) ?? 0;
        levels.set(targetId, Math.max(existing, currentLevel + 1));
        queue.push(targetId);
      }
    });
  }

  // 나머지 노드를 레벨 0으로 할당
  nodes.forEach((n) => {
    if (!levels.has(n.id)) levels.set(n.id, 0);
  });

  // 레벨별 그룹핑
  const levelGroups = new Map<number, string[]>();
  levels.forEach((level, id) => {
    if (!levelGroups.has(level)) levelGroups.set(level, []);
    levelGroups.get(level)!.push(id);
  });

  const maxLevel = Math.max(...Array.from(levelGroups.keys()));
  const levelHeight = height / (maxLevel + 2);

  levelGroups.forEach((ids, level) => {
    const levelWidth = width / (ids.length + 1);
    ids.forEach((id, i) => {
      const node = nodeMap.get(id);
      if (node) {
        node.x = levelWidth * (i + 1);
        node.y = levelHeight * (level + 1);
        node.fx = node.x;
        node.fy = node.y;
      }
    });
  });
};

export const applyLayout = (
  nodes: D3Node[],
  links: D3Link[],
  layout: LayoutType,
  width: number,
  height: number,
  simulation: d3.Simulation<D3Node, D3Link>
): void => {
  // 먼저 모든 노드 고정 해제
  nodes.forEach((n) => {
    if (!n.pinned) {
      n.fx = undefined;
      n.fy = undefined;
    }
  });

  if (layout === 'radial') {
    applyRadialLayout(nodes, links, width, height);
    simulation.alpha(0).stop();
  } else if (layout === 'dagre') {
    applyDagreLayout(nodes, links, width, height);
    simulation.alpha(0).stop();
  } else {
    // Force 레이아웃 - 고정 해제 후 재시작
    nodes.forEach((n) => {
      if (!n.pinned) {
        n.fx = undefined;
        n.fy = undefined;
      }
    });
    simulation.alpha(0.8).restart();
  }
};

export const getConnectedEdgeIds = (nodeId: string, links: D3Link[]): Set<string> => {
  const ids = new Set<string>();
  links.forEach((l) => {
    const sourceId = typeof l.source === 'string' ? l.source : (l.source as D3Node).id;
    const targetId = typeof l.target === 'string' ? l.target : (l.target as D3Node).id;
    if (sourceId === nodeId || targetId === nodeId) {
      ids.add(l.id);
    }
  });
  return ids;
};

export const getOneDepthNodes = (nodeId: string, links: D3Link[]): Set<string> => {
  const ids = new Set<string>([nodeId]);
  links.forEach((l) => {
    const sourceId = typeof l.source === 'string' ? l.source : (l.source as D3Node).id;
    const targetId = typeof l.target === 'string' ? l.target : (l.target as D3Node).id;
    if (sourceId === nodeId) ids.add(targetId);
    if (targetId === nodeId) ids.add(sourceId);
  });
  return ids;
};
