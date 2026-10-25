import { useGetMeQuery } from '../communication/api/generated-api';

/** Where Google login starts. A full page navigation, not a fetch: Spring redirects the browser on to Google. */
export const LOGIN_URL = '/oauth2/authorization/google';

/**
 * - `loading`: the first answer from `/api/auth/me` has not come yet.
 * - `loggedOut`: it answered 401.
 * - `loggedIn`: it answered with the owner.
 * - `offline`: anything else — the backend is down or unreachable, so nobody knows.
 */
export type LoginState = 'loading' | 'loggedOut' | 'loggedIn' | 'offline';

/**
 * The owner's login state, as sl-dashboard's useUserLoginState gives it, but read from the RTK Query cache instead
 * of a context: every component calling this shares one request to `/api/auth/me`.
 */
export function useLoginState(): LoginState {
  const { data, error, isLoading } = useGetMeQuery();
  if (isLoading) {
    return 'loading';
  }
  // The error before the data: a failed refetch keeps the previous data, so after logout `data` still holds the
  // owner while `error` already says 401.
  if (error) {
    return 'status' in error && error.status === 401 ? 'loggedOut' : 'offline';
  }
  return data ? 'loggedIn' : 'loading';
}
