import { generatedApi } from './generated-api';
import { liveApi, setReceipt } from './live-api';
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
 * The generated receipt and split mutations, each also writing into the stream's cache entry:
 * - **Upload and reset** write their result when it comes. The stream would bring the same a moment later, since
 *   the server broadcasts it to every page; writing it at once means the upload page never shows the button again
 *   for an instant between the answer and the event.
 * - **A tick** writes before the request is even sent (an optimistic update): over a poor connection a box that
 *   waits for the server feels broken. The `split` event that follows confirms it, or corrects it; a failed request
 *   undoes it.
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
            setReceipt(draft, data);
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
            setReceipt(draft, null);
          }));
        } catch (failure) {
          onUnauthorised(failure, dispatch);
        }
      },
    },
    setItemAssignees: {
      async onQueryStarted({ id, assigneesRequest }, { dispatch, queryFulfilled }) {
        const patch = dispatch(liveApi.util.updateQueryData('getLiveState', undefined, (draft) => {
          const item = draft.split?.items.find((candidate) => candidate.id === id);
          if (item) {
            item.people = assigneesRequest.people;
          }
        }));
        try {
          await queryFulfilled;
        } catch (failure) {
          patch.undo();
          onUnauthorised(failure, dispatch);
        }
      },
    },
  },
});

export const { useUploadReceiptMutation, useResetReceiptMutation, useSetItemAssigneesMutation } = receiptApi;
