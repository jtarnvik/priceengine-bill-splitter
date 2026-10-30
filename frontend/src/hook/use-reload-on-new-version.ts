import { useEffect } from 'react';
import { useStore } from 'react-redux';

import { liveApi } from '../communication/api/live-api';
import type { RootState } from '../store';

const CHECK_INTERVAL_MS = 1_000;

/**
 * Reloads the page once the stream has announced another server version than the one it started with — after a
 * deploy, a phone left on the page since the last dinner would otherwise run the old app against the new server.
 * Not in the middle of something: while a dialog is open ("Vem är du?", the settings, "Återställ") or a request is
 * under way (an upload, a tick), it waits, checking every second.
 *
 * Reads the stream's cache entry without subscribing to it, so it opens no stream of its own on pages that have none.
 */
export function useReloadOnNewVersion() {
  const { data } = liveApi.endpoints.getLiveState.useQueryState();
  const store = useStore<RootState>();
  const newVersion = data?.newVersion ?? null;

  useEffect(() => {
    if (!newVersion) {
      return;
    }
    const timer = window.setInterval(() => {
      const dialogOpen = document.querySelector('dialog[open]') !== null;
      const requestPending = Object.values(store.getState().pbsApi.mutations)
        .some((mutation) => mutation?.status === 'pending');
      if (!dialogOpen && !requestPending) {
        window.location.reload();
      }
    }, CHECK_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [newVersion, store]);
}
