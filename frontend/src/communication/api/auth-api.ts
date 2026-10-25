import { generatedApi } from './generated-api';

/**
 * Logout, hand-written: `/logout` is Spring Security's filter, not a controller, so springdoc never sees it. Injected
 * into the generated api rather than the base one, so that the `auth` tag it invalidates is a known tag type.
 *
 * Invalidating `auth` refetches `/api/auth/me`, which then answers 401 — and every component using the login state
 * sees the owner as logged out, with no state of its own to reset.
 */
export const authApi = generatedApi.injectEndpoints({
  endpoints: (build) => ({
    logout: build.mutation<void, void>({
      query: () => ({ url: '/logout', method: 'post' }),
      invalidatesTags: ['auth'],
    }),
  }),
});

export const { useLogoutMutation } = authApi;
