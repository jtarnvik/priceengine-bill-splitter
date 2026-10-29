import { useState } from 'react';
import { Navigate } from 'react-router-dom';

import { Card } from '../components/common/card';
import { ConfirmDialog } from '../components/common/confirm-dialog';
import { View } from '../components/common/view';
import { ReceiptList } from '../components/receipt-list';
import { UploadButton } from '../components/upload-button';
import { useGetLiveStateQuery } from '../communication/api/live-api';
import { useResetReceiptMutation, useUploadReceiptMutation } from '../communication/api/receipt-api';
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
  const [preparing, setPreparing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmingReset, setConfirmingReset] = useState(false);
  const receipt = data?.receipt;

  async function handleFileChosen(file: File) {
    setError(null);
    setPreparing(true);
    try {
      const image = await downscaleToJpeg(file);
      await uploadReceipt({ body: { image } }).unwrap();
    } catch (failure) {
      setError(failureMessage(failure));
    } finally {
      setPreparing(false);
    }
  }

  async function handleReset() {
    setConfirmingReset(false);
    setError(null);
    try {
      await resetReceipt().unwrap();
    } catch (failure) {
      setError(failureMessage(failure));
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
            <ReceiptList receipt={receipt} totalLabel="Totalt" />
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
 * In Swedish whatever failed. A parse failure (502) brings the server's own Swedish message; the downscaling throws
 * an Error with one. Everything else gets a general one: axios's own messages are English and technical.
 */
function failureMessage(failure: unknown): string {
  if (failure instanceof Error) {
    return failure.message;
  }
  if (isQueryError(failure) && failure.status === 502) {
    return failure.message;
  }
  if (isQueryError(failure) && failure.status === 401) {
    return 'Du är inte inloggad längre. Logga in igen från menyn.';
  }
  return 'Kvittot kunde inte skickas. Försök igen.';
}

/** What unwrap() rejects with when the request itself failed: axiosBaseQuery's error. */
function isQueryError(failure: unknown): failure is AxiosQueryError {
  return typeof failure === 'object' && failure !== null && 'status' in failure && 'message' in failure;
}
