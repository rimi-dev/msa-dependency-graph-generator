import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import type { JobStatus, GraphData } from '@/types';

const WS_URL = import.meta.env.VITE_WS_URL ?? 'ws://localhost:8080/ws';

let stompClient: Client | null = null;

type UnsubscribeFn = () => void;

const getClient = (): Promise<Client> => {
  return new Promise((resolve, reject) => {
    if (stompClient?.connected) {
      resolve(stompClient);
      return;
    }

    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      onConnect: () => {
        stompClient = client;
        resolve(client);
      },
      onStompError: (frame) => {
        console.error('STOMP error', frame);
        reject(new Error(frame.headers['message'] ?? 'STOMP connection error'));
      },
      onDisconnect: () => {
        stompClient = null;
      },
    });

    client.activate();
  });
};

export const subscribeJobProgress = async (
  jobId: string,
  onUpdate: (status: JobStatus) => void
): Promise<UnsubscribeFn> => {
  try {
    const client = await getClient();
    const subscription: StompSubscription = client.subscribe(
      `/topic/jobs/${jobId}/progress`,
      (message: IMessage) => {
        try {
          const data = JSON.parse(message.body) as JobStatus;
          onUpdate(data);
        } catch (e) {
          console.error('Failed to parse job progress message', e);
        }
      }
    );
    return () => subscription.unsubscribe();
  } catch (e) {
    console.warn('WebSocket not available, falling back to polling', e);
    return () => {};
  }
};

export const subscribeGraphUpdates = async (
  projectId: string,
  onUpdate: (graph: Partial<GraphData>) => void
): Promise<UnsubscribeFn> => {
  try {
    const client = await getClient();
    const subscription: StompSubscription = client.subscribe(
      `/topic/projects/${projectId}/graph-updates`,
      (message: IMessage) => {
        try {
          const data = JSON.parse(message.body) as Partial<GraphData>;
          onUpdate(data);
        } catch (e) {
          console.error('Failed to parse graph update message', e);
        }
      }
    );
    return () => subscription.unsubscribe();
  } catch (e) {
    console.warn('WebSocket not available for graph updates', e);
    return () => {};
  }
};

export const disconnectWebSocket = (): void => {
  if (stompClient?.connected) {
    stompClient.deactivate();
    stompClient = null;
  }
};
