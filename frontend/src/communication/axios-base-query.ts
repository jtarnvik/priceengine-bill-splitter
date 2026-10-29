import axios, { AxiosRequestConfig, Method } from 'axios';
import type { BaseQueryFn } from '@reduxjs/toolkit/query';

import { errorMessage } from './error-message';

/**
 * What RTK Query calls whenever one of its endpoints has to hit the network. An endpoint's `query` returns a plain
 * object describing the call; RTK Query hands that object to this function and stores whatever comes back —
 * `{data}` on success, `{error}` on failure — in the cache entry that the generated hooks then read.
 *
 * Copied from overlord, where RTK Query's own `fetchBaseQuery` was replaced by axios on purpose; pbs keeps the same
 * stack so the two projects read alike (see "Frontend data layer" in CLAUDE.md).
 */
export interface AxiosQueryArgs {
  url: string,
  method?: Method,
  /**
   * The request body. Named `body`, not axios's own `data`, because that is what `@rtk-query/codegen-openapi`
   * emits — and a generated endpoint whose body key this function does not recognise sends **no body at all, with
   * no error**. Hand-written endpoints use the same name so there is one dialect. A body holding a `Blob` is sent
   * as `multipart/form-data` (see toRequestData).
   */
  body?: unknown,
  /** Query-string parameters. Also the generator's name for them; likewise silently dropped if unread. */
  params?: Record<string, unknown>
}

/**
 * The error shape every endpoint sees: a plain status and string rather than the axios error object, because RTK
 * Query puts it straight into the Redux store, which has to stay serialisable.
 */
export interface AxiosQueryError {
  status: number | undefined,
  message: string
}

/**
 * The generator types a multipart request as a plain object, `{ image: Blob }` for the receipt upload, and axios
 * would send that as JSON. So an object with a `Blob` among its values becomes FormData, one part per key, and
 * axios sets the multipart Content-Type with its boundary itself. Anything else goes as it is.
 */
function toRequestData(body: unknown): unknown {
  if (body === null || typeof body !== 'object' || !Object.values(body).some((value) => value instanceof Blob)) {
    return body;
  }
  const form = new FormData();
  for (const [key, value] of Object.entries(body)) {
    form.append(key, value instanceof Blob ? value : String(value));
  }
  return form;
}

export const axiosBaseQuery = (): BaseQueryFn<AxiosQueryArgs, unknown, AxiosQueryError> =>
  async ({ url, method = 'get', body, params }) => {
    const args: AxiosRequestConfig = { method: method, url: url };
    if (body !== undefined) {
      args.data = toRequestData(body);
    }
    if (params !== undefined) {
      args.params = params;
    }
    try {
      const result = await axios(args);
      return { data: result.data };
    } catch (error) {
      return {
        error: {
          status: axios.isAxiosError(error) ? error.response?.status : undefined,
          message: errorMessage(error)
        }
      };
    }
  };
