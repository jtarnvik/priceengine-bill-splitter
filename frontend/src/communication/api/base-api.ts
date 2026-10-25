import { createApi } from '@reduxjs/toolkit/query/react';

import { axiosBaseQuery } from '../axios-base-query';

/**
 * The single RTK Query api slice for the whole app: the cache reducer, the middleware that drives it, and one hook
 * per endpoint all come out of this one call. It is created empty; endpoints are injected by the files beside it:
 * `generated-api.ts` (generated from the backend's OpenAPI spec, `npm run generate-api`), `live-api.ts` (the
 * stream) and `auth-api.ts` (logout, which is a Spring Security filter and so not in the spec).
 *
 * Never inject the same endpoint name from two files: two injections do not merge, whichever module loads first
 * wins and the other is silently dropped (overlord learnt this the hard way). The stream is excluded from code
 * generation, so it and the generated endpoints cannot collide.
 */
export const pbsApi = createApi({
  reducerPath: 'pbsApi',
  baseQuery: axiosBaseQuery(),
  tagTypes: [],
  endpoints: () => ({})
});
