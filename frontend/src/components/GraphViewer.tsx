import React, {
  useRef,
  useEffect,
  useState,
  useCallback,
  useMemo,
} from 'react';
import * as d3 from 'd3';
import type {
  D3Node,
  D3Link,
  LayoutType,
  Protocol,
  TooltipState,
  ContextMenuState,
} from '@/types';
import { getLanguageColor, getProtocolColor, getProtocolStrokeDash, getEdgeWidth } from '@/utils/colors';
import { createForceSimulation, applyLayout, getConnectedEdgeIds, getOneDepthNodes } from '@/utils/graph';
import { NodeTooltip } from './NodeTooltip';
import { EdgeTooltip } from './EdgeTooltip';
import { Toolbar } from './Toolbar';

interface GraphViewerProps {
  nodes: D3Node[];
  links: D3Link[];
  isMockData: boolean;
  onEdgeClick: (edge: D3Link) => void;
}

const ZOOM_MIN = 0.1;
const ZOOM_MAX = 5.0;
const NODE_RADIUS = 30;

export const GraphViewer: React.FC<GraphViewerProps> = ({
  nodes: initialNodes,
  links: initialLinks,
  isMockData,
  onEdgeClick,
}) => {
  const svgRef = useRef<SVGSVGElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const simulationRef = useRef<d3.Simulation<D3Node, D3Link> | null>(null);
  const zoomRef = useRef<d3.ZoomBehavior<SVGSVGElement, unknown> | null>(null);
  const gRef = useRef<d3.Selection<SVGGElement, unknown, null, undefined> | null>(null);

  const [currentZoom, setCurrentZoom] = useState(1);
  const [layout, setLayout] = useState<LayoutType>('force');
  const [lockNodes, setLockNodes] = useState(false);
  const [protocolFilter, setProtocolFilter] = useState<Set<Protocol>>(new Set(['HTTP', 'gRPC', 'MQ']));
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [depthFilterNodeId, setDepthFilterNodeId] = useState<string | null>(null);
  const [tooltip, setTooltip] = useState<TooltipState>({ visible: false, x: 0, y: 0, content: null });
  const [contextMenu, setContextMenu] = useState<ContextMenuState>({ visible: false, x: 0, y: 0, nodeId: null });

  // Mutable copies tracked inside D3
  const nodesRef = useRef<D3Node[]>([]);
  const linksRef = useRef<D3Link[]>([]);

  // Filtered nodes/links based on depth filter and protocol filter
  const { visibleNodes, visibleLinks } = useMemo(() => {
    let filteredNodes = initialNodes;
    let filteredLinks = initialLinks.filter((l) => protocolFilter.has(l.protocol));

    if (depthFilterNodeId) {
      const connected = getOneDepthNodes(depthFilterNodeId, filteredLinks);
      filteredNodes = filteredNodes.filter((n) => connected.has(n.id));
      filteredLinks = filteredLinks.filter((l) => {
        const src = typeof l.source === 'string' ? l.source : (l.source as D3Node).id;
        const tgt = typeof l.target === 'string' ? l.target : (l.target as D3Node).id;
        return connected.has(src) && connected.has(tgt);
      });
    }

    return { visibleNodes: filteredNodes, visibleLinks: filteredLinks };
  }, [initialNodes, initialLinks, protocolFilter, depthFilterNodeId]);

  const edgeCounts = useMemo(() => {
    return {
      HTTP: initialLinks.filter((l) => l.protocol === 'HTTP').length,
      gRPC: initialLinks.filter((l) => l.protocol === 'gRPC').length,
      MQ: initialLinks.filter((l) => l.protocol === 'MQ').length,
    };
  }, [initialLinks]);

  const hideContextMenu = useCallback(() => {
    setContextMenu((prev) => ({ ...prev, visible: false }));
  }, []);

  const hideTooltip = useCallback(() => {
    setTooltip((prev) => ({ ...prev, visible: false }));
  }, []);

  // Forward ref so buildGraph can call fitAll without circular dependency
  const fitAllRef = useRef<(w?: number, h?: number) => void>(() => {});

  // ─── D3 Setup ──────────────────────────────────────────────────────────────

  const buildGraph = useCallback(() => {
    const svg = svgRef.current;
    const container = containerRef.current;
    if (!svg || !container) return;

    const width = container.clientWidth;
    const height = container.clientHeight;

    // Deep clone to avoid mutating props
    const nodes: D3Node[] = visibleNodes.map((n) => {
      const existing = nodesRef.current.find((e) => e.id === n.id);
      return existing
        ? { ...n, x: existing.x, y: existing.y, vx: existing.vx, vy: existing.vy, fx: existing.fx, fy: existing.fy, pinned: existing.pinned }
        : { ...n, x: width / 2 + (Math.random() - 0.5) * 200, y: height / 2 + (Math.random() - 0.5) * 200 };
    });
    const links: D3Link[] = visibleLinks.map((l) => ({ ...l }));

    nodesRef.current = nodes;
    linksRef.current = links;

    // Stop previous simulation
    simulationRef.current?.stop();

    d3.select(svg).selectAll('*').remove();

    const defs = d3.select(svg).append('defs');

    // Arrow markers for each protocol
    const protocols: Protocol[] = ['HTTP', 'gRPC', 'MQ'];
    protocols.forEach((p) => {
      const color = getProtocolColor(p);
      defs
        .append('marker')
        .attr('id', `arrow-${p}`)
        .attr('viewBox', '0 -4 10 8')
        .attr('refX', NODE_RADIUS + 10)
        .attr('refY', 0)
        .attr('markerWidth', 8)
        .attr('markerHeight', 8)
        .attr('orient', 'auto')
        .append('path')
        .attr('d', 'M0,-4L10,0L0,4')
        .attr('fill', color)
        .attr('opacity', 0.85);

      // Dimmed version
      defs
        .append('marker')
        .attr('id', `arrow-${p}-dim`)
        .attr('viewBox', '0 -4 10 8')
        .attr('refX', NODE_RADIUS + 10)
        .attr('refY', 0)
        .attr('markerWidth', 8)
        .attr('markerHeight', 8)
        .attr('orient', 'auto')
        .append('path')
        .attr('d', 'M0,-4L10,0L0,4')
        .attr('fill', color)
        .attr('opacity', 0.15);
    });

    const g = d3.select(svg).append('g').attr('class', 'graph-root');
    gRef.current = g;

    // Zoom behavior
    const zoom = d3
      .zoom<SVGSVGElement, unknown>()
      .scaleExtent([ZOOM_MIN, ZOOM_MAX])
      .on('zoom', (event: d3.D3ZoomEvent<SVGSVGElement, unknown>) => {
        g.attr('transform', event.transform.toString());
        setCurrentZoom(event.transform.k);
      });
    zoomRef.current = zoom;
    d3.select(svg).call(zoom);

    // Links
    const linkG = g.append('g').attr('class', 'links');
    const linkSelection = linkG
      .selectAll<SVGLineElement, D3Link>('line')
      .data(links, (d) => d.id)
      .join('line')
      .attr('class', 'edge')
      .attr('data-id', (d) => d.id)
      .attr('stroke', (d) => getProtocolColor(d.protocol))
      .attr('stroke-width', (d) => getEdgeWidth(d.sourceLocationCount))
      .attr('stroke-dasharray', (d) => getProtocolStrokeDash(d.protocol))
      .attr('stroke-opacity', 0.8)
      .attr('marker-end', (d) => `url(#arrow-${d.protocol})`)
      .attr('cursor', 'pointer')
      .on('mouseenter', (event: MouseEvent, d: D3Link) => {
        d3.select(event.currentTarget as SVGLineElement).attr('stroke-opacity', 1).attr('stroke-width', getEdgeWidth(d.sourceLocationCount) + 1.5);
        setTooltip({ visible: true, x: event.clientX, y: event.clientY, content: { type: 'edge', edge: d } });
      })
      .on('mousemove', (event: MouseEvent) => {
        setTooltip((prev) => ({ ...prev, x: event.clientX, y: event.clientY }));
      })
      .on('mouseleave', (event: MouseEvent, d: D3Link) => {
        d3.select(event.currentTarget as SVGLineElement).attr('stroke-opacity', 0.8).attr('stroke-width', getEdgeWidth(d.sourceLocationCount));
        hideTooltip();
      })
      .on('click', (_event: MouseEvent, d: D3Link) => {
        onEdgeClick(d);
      });

    // Nodes
    const nodeG = g.append('g').attr('class', 'nodes');
    const nodeSelection = nodeG
      .selectAll<SVGGElement, D3Node>('g.node')
      .data(nodes, (d) => d.id)
      .join('g')
      .attr('class', 'node')
      .attr('data-id', (d) => d.id)
      .attr('cursor', 'grab');

    // Node circles
    nodeSelection
      .append('circle')
      .attr('r', NODE_RADIUS)
      .attr('fill', (d) => getLanguageColor(d.language).bg)
      .attr('stroke', (d) => getLanguageColor(d.language).border)
      .attr('stroke-width', 2)
      .attr('filter', 'url(#node-shadow)');

    // Node shadow filter
    const shadowFilter = defs.append('filter').attr('id', 'node-shadow').attr('x', '-50%').attr('y', '-50%').attr('width', '200%').attr('height', '200%');
    shadowFilter.append('feDropShadow').attr('dx', 0).attr('dy', 2).attr('stdDeviation', 4).attr('flood-opacity', 0.2);

    // Node labels (display name - multiline)
    nodeSelection.each(function (d) {
      const el = d3.select(this);
      const words = d.displayName.split(/[\s-_]/);
      const lineHeight = 12;
      const startY = words.length > 1 ? -lineHeight * 0.5 : 0;
      const color = getLanguageColor(d.language);

      words.slice(0, 2).forEach((word, i) => {
        el.append('text')
          .attr('text-anchor', 'middle')
          .attr('dominant-baseline', 'central')
          .attr('y', startY + i * lineHeight)
          .attr('fill', color.text)
          .attr('font-size', '10px')
          .attr('font-weight', '600')
          .attr('font-family', 'Inter, sans-serif')
          .attr('pointer-events', 'none')
          .text(word.length > 8 ? word.slice(0, 7) + '…' : word);
      });
    });

    // Node language badge
    nodeSelection
      .append('text')
      .attr('text-anchor', 'middle')
      .attr('y', NODE_RADIUS + 14)
      .attr('fill', 'var(--text-secondary)')
      .attr('font-size', '9px')
      .attr('font-family', 'Inter, sans-serif')
      .attr('pointer-events', 'none')
      .text((d) => d.language);

    // Pin indicator
    nodeSelection
      .append('text')
      .attr('class', 'pin-icon')
      .attr('text-anchor', 'middle')
      .attr('y', -NODE_RADIUS - 6)
      .attr('font-size', '12px')
      .attr('pointer-events', 'none')
      .text((d) => (d.pinned ? '📌' : ''));

    // Event handlers
    nodeSelection
      .on('mouseenter', (event: MouseEvent, d: D3Node) => {
        const connectedEdges = getConnectedEdgeIds(d.id, links);
        // Highlight connected
        linkSelection
          .attr('stroke-opacity', (l) => connectedEdges.has(l.id) ? 1 : 0.15)
          .attr('marker-end', (l) => connectedEdges.has(l.id) ? `url(#arrow-${l.protocol})` : `url(#arrow-${l.protocol}-dim)`);

        d3.select(event.currentTarget as SVGGElement)
          .select('circle')
          .attr('stroke-width', 3)
          .attr('filter', 'url(#node-shadow) brightness(1.1)');

        setTooltip({ visible: true, x: event.clientX, y: event.clientY, content: { type: 'node', node: d } });
      })
      .on('mousemove', (event: MouseEvent) => {
        setTooltip((prev) => ({ ...prev, x: event.clientX, y: event.clientY }));
      })
      .on('mouseleave', (event: MouseEvent) => {
        // Restore
        if (!selectedNodeId) {
          linkSelection.attr('stroke-opacity', 0.8).attr('marker-end', (l) => `url(#arrow-${l.protocol})`);
        }
        d3.select(event.currentTarget as SVGGElement)
          .select('circle')
          .attr('stroke-width', 2)
          .attr('filter', 'url(#node-shadow)');
        hideTooltip();
      })
      .on('click', (_event: MouseEvent, d: D3Node) => {
        setSelectedNodeId((prev) => {
          const newId = prev === d.id ? null : d.id;
          if (newId) {
            const connectedEdges = getConnectedEdgeIds(newId, links);
            linkSelection
              .attr('stroke-opacity', (l) => connectedEdges.has(l.id) ? 0.9 : 0.1)
              .attr('marker-end', (l) => connectedEdges.has(l.id) ? `url(#arrow-${l.protocol})` : `url(#arrow-${l.protocol}-dim)`);
            nodeSelection.select('circle').attr('opacity', (n: D3Node) => {
              const ce = getConnectedEdgeIds(newId, links);
              const nodeIds = new Set([...Array.from(links).flatMap((l) => {
                if (ce.has(l.id)) {
                  const src = typeof l.source === 'string' ? l.source : (l.source as D3Node).id;
                  const tgt = typeof l.target === 'string' ? l.target : (l.target as D3Node).id;
                  return [src, tgt];
                }
                return [];
              }), newId]);
              return nodeIds.has(n.id) ? 1 : 0.25;
            });
          } else {
            linkSelection.attr('stroke-opacity', 0.8).attr('marker-end', (l) => `url(#arrow-${l.protocol})`);
            nodeSelection.select('circle').attr('opacity', 1);
          }
          return newId;
        });
      })
      .on('dblclick', (_event: MouseEvent, d: D3Node) => {
        setDepthFilterNodeId((prev) => (prev === d.id ? null : d.id));
      })
      .on('contextmenu', (event: MouseEvent, d: D3Node) => {
        event.preventDefault();
        if (d.pinned) {
          setContextMenu({ visible: true, x: event.clientX, y: event.clientY, nodeId: d.id });
        }
      });

    // Drag behavior
    const drag = d3
      .drag<SVGGElement, D3Node>()
      .on('start', function (event, d) {
        if (!lockNodes && !event.active) {
          simulationRef.current?.alphaTarget(0.3).restart();
        }
        d.fx = d.x;
        d.fy = d.y;
      })
      .on('drag', function (event, d) {
        if (!lockNodes) {
          d.fx = event.x;
          d.fy = event.y;
        }
      })
      .on('end', function (event, d) {
        if (!lockNodes && !event.active) {
          simulationRef.current?.alphaTarget(0);
        }
        d.pinned = true;
        // Update pin icon
        d3.select(svgRef.current)
          .selectAll<SVGGElement, D3Node>('g.node')
          .filter((n) => n.id === d.id)
          .select('.pin-icon')
          .text('📌');
      });

    nodeSelection.call(drag);

    // Simulation
    const simulation = createForceSimulation(nodes, links, width, height);
    simulationRef.current = simulation;

    simulation.on('tick', () => {
      linkSelection
        .attr('x1', (d) => (d.source as D3Node).x ?? 0)
        .attr('y1', (d) => (d.source as D3Node).y ?? 0)
        .attr('x2', (d) => (d.target as D3Node).x ?? 0)
        .attr('y2', (d) => (d.target as D3Node).y ?? 0);

      nodeSelection.attr('transform', (d) => `translate(${d.x ?? 0},${d.y ?? 0})`);
    });

    // Apply initial layout if not force
    if (layout !== 'force') {
      applyLayout(nodes, links, layout, width, height, simulation);
    }

    // Initial zoom to fit
    setTimeout(() => fitAllRef.current(width, height), 100);

    return () => simulation.stop();
  }, [visibleNodes, visibleLinks, layout, lockNodes, selectedNodeId, onEdgeClick, hideTooltip]);

  const fitAll = useCallback((w?: number, h?: number) => {
    const svg = svgRef.current;
    const container = containerRef.current;
    if (!svg || !container || !gRef.current || !zoomRef.current) return;

    const width = w ?? container.clientWidth;
    const height = h ?? container.clientHeight;
    const bbox = (gRef.current.node() as SVGGElement)?.getBBox();
    if (!bbox || bbox.width === 0) return;

    const padding = 60;
    const scaleX = (width - padding * 2) / bbox.width;
    const scaleY = (height - padding * 2) / bbox.height;
    const scale = Math.min(scaleX, scaleY, ZOOM_MAX);
    const tx = width / 2 - scale * (bbox.x + bbox.width / 2);
    const ty = height / 2 - scale * (bbox.y + bbox.height / 2);

    d3.select(svg)
      .transition()
      .duration(600)
      .call(zoomRef.current.transform, d3.zoomIdentity.translate(tx, ty).scale(scale));
  }, []);

  // Keep forward ref in sync
  useEffect(() => {
    fitAllRef.current = fitAll;
  }, [fitAll]);

  const zoomIn = useCallback(() => {
    const svg = svgRef.current;
    if (!svg || !zoomRef.current) return;
    d3.select(svg).transition().duration(300).call(zoomRef.current.scaleBy, 1.4);
  }, []);

  const zoomOut = useCallback(() => {
    const svg = svgRef.current;
    if (!svg || !zoomRef.current) return;
    d3.select(svg).transition().duration(300).call(zoomRef.current.scaleBy, 1 / 1.4);
  }, []);

  const handleLayoutChange = useCallback(
    (newLayout: LayoutType) => {
      setLayout(newLayout);
      if (simulationRef.current && nodesRef.current.length > 0) {
        const container = containerRef.current;
        if (!container) return;
        applyLayout(
          nodesRef.current,
          linksRef.current,
          newLayout,
          container.clientWidth,
          container.clientHeight,
          simulationRef.current
        );
      }
    },
    []
  );

  const handleUnpinNode = useCallback((nodeId: string) => {
    hideContextMenu();
    const node = nodesRef.current.find((n) => n.id === nodeId);
    if (node) {
      node.pinned = false;
      node.fx = undefined;
      node.fy = undefined;
      simulationRef.current?.alpha(0.3).restart();
      d3.select(svgRef.current)
        .selectAll<SVGGElement, D3Node>('g.node')
        .filter((n) => n.id === nodeId)
        .select('.pin-icon')
        .text('');
    }
  }, [hideContextMenu]);

  // Build graph when data changes
  useEffect(() => {
    buildGraph();
  }, [buildGraph]);

  // Handle window resize
  useEffect(() => {
    const handleResize = () => {
      if (simulationRef.current) {
        const container = containerRef.current;
        if (container) {
          simulationRef.current
            .force('center', d3.forceCenter(container.clientWidth / 2, container.clientHeight / 2))
            .alpha(0.3)
            .restart();
        }
      }
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // Close context menu on outside click
  useEffect(() => {
    const handler = () => hideContextMenu();
    document.addEventListener('click', handler);
    return () => document.removeEventListener('click', handler);
  }, [hideContextMenu]);

  return (
    <div className="relative flex flex-col h-full bg-[var(--graph-bg)] overflow-hidden">
      {/* Mock data banner */}
      {isMockData && (
        <div className="absolute top-3 left-1/2 -translate-x-1/2 z-20 bg-amber-500/10 border border-amber-500/30 text-amber-600 dark:text-amber-400 text-xs px-4 py-1.5 rounded-full font-medium backdrop-blur-sm shadow">
          데모 데이터 표시 중 — 분석을 실행하면 실제 데이터로 교체됩니다
        </div>
      )}

      {/* Depth filter indicator */}
      {depthFilterNodeId && (
        <div className="absolute top-3 left-3 z-20 flex items-center gap-2 bg-blue-500/10 border border-blue-500/30 text-blue-600 dark:text-blue-400 text-xs px-3 py-1.5 rounded-full font-medium backdrop-blur-sm shadow">
          <span>1-depth 필터: {visibleNodes.find((n) => n.id === depthFilterNodeId)?.displayName}</span>
          <button
            onClick={() => setDepthFilterNodeId(null)}
            className="hover:text-red-500 transition-colors"
          >
            ✕
          </button>
        </div>
      )}

      {/* SVG Graph */}
      <div ref={containerRef} className="flex-1 overflow-hidden">
        <svg
          ref={svgRef}
          width="100%"
          height="100%"
          className="block cursor-grab active:cursor-grabbing"
          onClick={() => {
            hideContextMenu();
          }}
        />
      </div>

      {/* Toolbar */}
      <div className="flex-shrink-0 border-t border-[var(--border)] bg-[var(--surface-1)] px-4 py-2.5">
        <Toolbar
          zoom={currentZoom}
          onZoomIn={zoomIn}
          onZoomOut={zoomOut}
          onFitAll={() => fitAll()}
          layout={layout}
          onLayoutChange={handleLayoutChange}
          protocolFilter={protocolFilter}
          onProtocolFilterChange={setProtocolFilter}
          lockNodes={lockNodes}
          onLockToggle={() => setLockNodes((v) => !v)}
          edgeCounts={edgeCounts}
        />
      </div>

      {/* Tooltips */}
      {tooltip.visible && tooltip.content && (
        <>
          {tooltip.content.type === 'node' && (
            <NodeTooltip node={tooltip.content.node} x={tooltip.x} y={tooltip.y} />
          )}
          {tooltip.content.type === 'edge' && (
            <EdgeTooltip edge={tooltip.content.edge} x={tooltip.x} y={tooltip.y} />
          )}
        </>
      )}

      {/* Context Menu */}
      {contextMenu.visible && contextMenu.nodeId && (
        <div
          style={{ position: 'fixed', left: contextMenu.x, top: contextMenu.y, zIndex: 9999 }}
          className="bg-[var(--surface-1)] border border-[var(--border)] rounded-xl shadow-2xl py-1 min-w-[140px]"
          onClick={(e) => e.stopPropagation()}
        >
          <button
            onClick={() => handleUnpinNode(contextMenu.nodeId!)}
            className="w-full text-left px-3 py-2 text-xs text-[var(--text-secondary)] hover:bg-[var(--surface-2)] transition-colors flex items-center gap-2"
          >
            <span>📌</span>
            <span>고정 해제</span>
          </button>
        </div>
      )}
    </div>
  );
};
