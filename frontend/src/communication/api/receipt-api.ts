import { generatedApi } from './generated-api';
import { liveApi } from './live-api';
import { AxiosQueryError } from '../axios-base-query';

/**
 * A 401 means the session is gone, most likely ended by a restart of the server. Refetching `/api/auth/me` makes
 * every component see that at once, and the upload page sends the owner to the public page.
 */
function onUnauthorised(failure: unknown, dispatch: (action: unknown) => unknown) {
  const error = (failure as { error?: AxiosQueryError }).error;
  if (error?.status === 401) {
    dispatch(generatedApi.util.invalidateTags(['auth']));
  }
}

/**
 * The generated receipt mutations, each also writing its result into the stream's cache entry. The stream would
 * bring the same receipt a moment later anyway, since the server broadcasts it to every page; writing it at once
 * means the upload page never shows the button again for an instant between the answer and the event.
 *
 * Components import the hooks from here, not from generated-api.ts, so that this module is loaded and the
 * enhancement is in place.
 */
export const receiptApi = generatedApi.enhanceEndpoints({
  endpoints: {
    uploadReceipt: {
      async onQueryStarted(_arg, { dispatch, queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          dispatch(liveApi.util.updateQueryData('getLiveState', undefined, (draft) => {
            draft.receipt = data;
          }));
        } catch (failure) {
          // The component shows the error from the mutation's own result; there is nothing to write.
          onUnauthorised(failure, dispatch);
        }
      },
    },
    resetReceipt: {
      async onQueryStarted(_arg, { dispatch, queryFulfilled }) {
        try {
          await queryFulfilled;
          dispatch(liveApi.util.updateQueryData('getLiveState', undefined, (draft) => {
            draft.receipt = null;
          }));
        } catch (failure) {
          onUnauthorised(failure, dispatch);
        }
      },
    },
  },
});

export const { useUploadReceiptMutation, useResetReceiptMutation } = receiptApi;
