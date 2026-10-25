import { pbsApi } from './base-api';
import { ConnectionStatus, LiveState, ViewersEvent } from '../../types/live';

const EVENTS_URL = '/api/public/events';
const FIRST_RETRY_DELAY_MS = 2_000;
const MAX_RETRY_DELAY_MS = 30_000;

/**
 * The live stream as an RTK Query streaming endpoint: the query itself fetches nothing, and `onCacheEntryAdded`
 * keeps an EventSource open for as long as any component uses the hook, writing each event into the cache. Every
 * event carries the whole of its part of the state, and the server sends the current state first on every
 * connection, so (re)connecting is the entire recovery — nothing is ever fetched or merged.
 *
 * Two ways the connection ends, and who reopens it:
 * - **The stream drops** (network blip, Cloudflare closing it): EventSource reconnects by itself.
 * - **EventSource gives up** (`CLOSED`): it does that when a reconnect gets a non-200 answer — Cloudflare's 502
 *   while pbs restarts during a deploy, or the Vite proxy's while the backend is down — and iOS can leave it
 *   closed after the screen has been locked. Reopened here, after a delay that grows to a cap, and at once when the
 *   page becomes visible again.
 */
export const liveApi = pbsApi.injectEndpoints({
  endpoints: (build) => ({
    getLiveState: build.query<LiveState, void>({
      queryFn: () => ({ data: { viewers: null, status: 'connecting' } }),
      async onCacheEntryAdded(_arg, { updateCachedData, cacheDataLoaded, cacheEntryRemoved }) {
        await cacheDataLoaded;

        let source: EventSource | null = null;
        let retryTimer: number | undefined = undefined;
        let retryDelay = FIRST_RETRY_DELAY_MS;

        function setStatus(status: ConnectionStatus) {
          updateCachedData((draft) => {
            draft.status = status;
          });
        }

        function close() {
          source?.close();
          source = null;
        }

        function open() {
          close();
          source = new EventSource(EVENTS_URL);
          source.onopen = () => {
            retryDelay = FIRST_RETRY_DELAY_MS;
            setStatus('open');
          };
          source.addEventListener('viewers', (event) => {
            const payload = JSON.parse(event.data) as ViewersEvent;
            updateCachedData((draft) => {
              draft.viewers = payload.viewers;
            });
          });
          source.onerror = () => {
            setStatus('reconnecting');
            if (source?.readyState === EventSource.CLOSED) {
              scheduleRetry();
            }
          };
        }

        function scheduleRetry() {
          if (retryTimer !== undefined) {
            return;
          }
          retryTimer = window.setTimeout(() => {
            retryTimer = undefined;
            open();
          }, retryDelay);
          retryDelay = Math.min(retryDelay * 2, MAX_RETRY_DELAY_MS);
        }

        function cancelRetry() {
          if (retryTimer !== undefined) {
            window.clearTimeout(retryTimer);
            retryTimer = undefined;
          }
        }

        function handleVisibilityChange() {
          if (document.visibilityState === 'visible' && source?.readyState === EventSource.CLOSED) {
            cancelRetry();
            retryDelay = FIRST_RETRY_DELAY_MS;
            open();
          }
        }

        open();
        document.addEventListener('visibilitychange', handleVisibilityChange);

        await cacheEntryRemoved;

        document.removeEventListener('visibilitychange', handleVisibilityChange);
        cancelRetry();
        close();
      },
    }),
  }),
});

export const { useGetLiveStateQuery } = liveApi;
