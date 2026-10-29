import { useState } from 'react';
import { Navigate } from 'react-router-dom';

import { Card } from '../components/common/card';
import { ConfirmDialog } from '../components/common/confirm-dialog';
import { View } from '../components/common/view';
import { ReceiptList } from '../components/receipt-list';
import { UploadButton } from '../components/upload-button';
import { useGetLiveStateQuery } from '../communication/api/live-api';
import {
  useResetReceiptMutation,
  useSetItemAssigneesMutation,
  useUploadReceiptMutation
} from '../communication/api/receipt-api';
import { AxiosQueryError } from '../communication/axios-base-query';
import { useLoginState } from '../hook/use-login-state';
import { downscaleToJpeg } from '../util/downscale-image';

// The owner's page, behind login.
export function UploadPage() {
  const loginState = useLoginState();

  if (loginState === 'loggedIn') {
    return <OwnerReceipt />;
  }
  if (loginState === 'loggedOut') {
    // Not logged in, or no longer: a restart of the server ends every session. The public page has the receipt,
    // and "Logga in" in the menu.
    return <Navigate to="/" replace />;
  }
  return (
    <View>
      <Card>
        <h2 className="pt-2 font-medium">Ladda upp kvitto</h2>
        {loginState === 'loading' && <p className="pb-2">Kontrollerar inloggning …</p>}
        {loginState === 'offline' && <p className="pb-2">Servern svarar inte just nu.</p>}
      </Card>
    </View>
  );
}

/**
 * The upload button without a receipt, the receipt with one. Drawn from the stream like the public page, so a reload
 * mid-dinner keeps the receipt.
 */
function OwnerReceipt() {
  const { data } = useGetLiveStateQuery();
  const [uploadReceipt, { isLoading: uploading }] = useUploadReceiptMutation();
  const [resetReceipt] = useResetReceiptMutation();
  const [setItemAssignees] = useSetItemAssigneesMutation();
  const [preparing, setPreparing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmingReset, setConfirmingReset] = useState(false);
  const receipt = data?.receipt;
  const split = data?.split;

  async function handleFileChosen(file: File) {
    setError(null);
    setPreparing(true);
    try {
      const image = await downscaleToJpeg(file);
      await uploadReceipt({ body: { image } }).unwrap();
    } catch (failure) {
      setError(failureMessage(failure, 'Kvittot kunde inte skickas. Försök igen.'));
    } finally {
      setPreparing(false);
    }
  }

  // The box has already changed (an optimistic update); a failure has undone it, and says so here.
  async function handleAssign(itemId: number, people: string[]) {
    if (!receipt) {
      return;
    }
    setError(null);
    try {
      await setItemAssignees({ id: itemId, assigneesRequest: { receiptId: receipt.receiptId, people } }).unwrap();
    } catch (failure) {
      setError(failureMessage(failure, 'Bocken kunde inte sparas. Försök igen.'));
    }
  }

  async function handleReset() {
    setConfirmingReset(false);
    setError(null);
    try {
      await resetReceipt().unwrap();
    } catch (failure) {
      setError(failureMessage(failure, 'Kvittot kunde inte återställas. Försök igen.'));
    }
  }

  return (
    <View>
      <Card>
        {receipt === undefined && <p className="py-2 text-gray-500">Hämtar kvittot …</p>}
        {receipt === null && <UploadButton busy={preparing || uploading} onFileChosen={handleFileChosen} />}
        {receipt && (
          <>
            <h2 className="pt-2 pb-1 text-lg font-medium break-words">{receipt.restaurantName}</h2>
            <ReceiptList
              receipt={receipt}
              footer={[
                { label: 'Totalt', amount: receipt.total },
                // Before the split's event, which follows the receipt at once, nothing is assigned.
                { label: 'Kvar att dela på', amount: split?.unassigned.total ?? receipt.linesTotal },
              ]}
              assignment={split ? { split, onChange: handleAssign } : undefined}
            />
          </>
        )}
        {error && <p role="alert" className="pb-2 text-red-700">{error}</p>}
      </Card>
      {receipt && (
        <button
          className="self-start rounded-md border border-gray-300 bg-white px-4 py-2 text-gray-800 hover:bg-gray-100 cursor-pointer"
          onClick={() => setConfirmingReset(true)}
        >
          Återställ
        </button>
      )}
      <ConfirmDialog
        open={confirmingReset}
        message="Ta bort kvittot för alla? Det går inte att ångra."
        confirmLabel="Återställ"
        onConfirm={handleReset}
        onCancel={() => setConfirmingReset(false)}
      />
    </View>
  );
}

/**
 * In Swedish whatever failed. pbs's own error answers (a failed parse, a stale receipt) bring a Swedish message to
 * show as it is; the downscaling throws an Error with one. Everything else gets the caller's general one: axios's
 * own messages are English and technical.
 */
function failureMessage(failure: unknown, fallback: string): string {
  if (failure instanceof Error) {
    return failure.message;
  }
  if (isQueryError(failure) && failure.status === 401) {
    return 'Du är inte inloggad längre. Logga in igen från menyn.';
  }
  if (isQueryError(failure) && failure.serverMessage) {
    return failure.serverMessage;
  }
  return fallback;
}

/** What unwrap() rejects with when the request itself failed: axiosBaseQuery's error. */
function isQueryError(failure: unknown): failure is AxiosQueryError {
  return typeof failure === 'object' && failure !== null && 'status' in failure && 'message' in failure;
}
