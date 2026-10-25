import axios from 'axios';

/**
 * Extracts something readable from a failed call. A `message` in the response body is preferred (Spring's error
 * body has one); a transport failure (no response at all) only has the axios message; anything else falls back to
 * the caller's wording. Adapted from overlord, which has its own `ResponseInfo` error body; pbs has none yet.
 */
export function errorMessage(error: unknown, fallback = 'Okänt fel'): string {
  if (axios.isAxiosError<{ message?: string }>(error) && error.response?.data?.message) {
    return error.response.data.message;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}
