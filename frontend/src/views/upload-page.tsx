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
  useSetReceiptDoneMutation,
  useUploadReceiptMutation
} from '../communication/api/receipt-api';
import { AxiosQueryError } from '../communication/axios-base-query';
import { useGetSettingsQuery } from '../communication/api/generated-api';
import { useLoginState } from '../hook/use-login-state';
import { downscaleToJpeg } from '../util/downscale-image';

const SECONDARY_BUTTON_CLASS =
  'rounded-md border border-gray-300 bg-white px-4 py-2 text-gray-800 hover:bg-gray-100 cursor-pointer '
  + 'disabled:cursor-default disabled:text-gray-400 disabled:hover:bg-white';

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
  const [setReceiptDone, { isLoading: settingDone }] = useSetReceiptDoneMutation();
  const { data: settings } = useGetSettingsQuery();
  const [preparing, setPreparing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmingReset, setConfirmingReset] = useState(false);
  const receipt = data?.receipt;
  const split = data?.split;
  const allAssigned = split?.items.every((item) => item.people.length > 0) ?? false;
  // Only on canned receipts, which exist to be tested with: ticking a long one by hand gets tedious fast.
  const offerRandomTicks = settings?.receiptParser === 'canned' && !!split && !split.done;

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

  // One random person on every row nobody has yet, each as an ordinary tick; rows already ticked are left alone.
  function handleRandomTicks() {
    if (!split) {
      return;
    }
    const names = split.people.map((person) => person.name);
    for (const item of split.items) {
      if (item.people.length === 0) {
        void handleAssign(item.id, [names[Math.floor(Math.random() * names.length)]]);
      }
    }
  }

  // "Klar" or its undoing, as a whole state; the split event then shows it on every page, this one included.
  async function handleDone(done: boolean) {
    if (!receipt) {
      return;
    }
    setError(null);
    try {
      await setReceiptDone({ doneRequest: { receiptId: receipt.receiptId, done } }).unwrap();
    } catch (failure) {
      setError(failureMessage(failure, done ? 'Kunde inte markera klar. Försök igen.' : 'Kunde inte ångra. Försök igen.'));
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
        {receipt === null && (
          <>
            <UploadButton busy={preparing || uploading} onFileChosen={handleFileChosen} />
            {/* Which parser answers, since the two look alike from here and only one costs money. */}
            {settings && (
              <p className="pb-2 text-xs text-gray-500">
                {settings.receiptParser === 'canned'
                  ? 'Inspelade kvitton: Claude anropas inte. Byt i Inställningar.'
                  : 'Claude läser kvittot.'}
              </p>
            )}
          </>
        )}
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
              assignment={split ? { split, onChange: handleAssign, disabled: split.done } : undefined}
            />
          </>
        )}
        {error && <p role="alert" className="pb-2 text-red-700">{error}</p>}
      </Card>
      {split?.done && (
        <p className="text-sm text-gray-600">Klar. Vännerna ser sina Swish-knappar; ångra för att ändra något.</p>
      )}
      {receipt && (
        <div className="flex gap-2">
          {/* Only with everything assigned: the amounts in the Swish buttons must be final. */}
          {split?.done ? (
            <button className={SECONDARY_BUTTON_CLASS} disabled={settingDone} onClick={() => handleDone(false)}>
              Ångra klar
            </button>
          ) : (
            <button
              className="rounded-md bg-brand px-4 py-2 font-medium text-white hover:bg-brand-hover cursor-pointer disabled:cursor-default disabled:bg-brand/40"
              disabled={!allAssigned || settingDone}
              onClick={() => handleDone(true)}
            >
              Klar
            </button>
          )}
          <button className={SECONDARY_BUTTON_CLASS} onClick={() => setConfirmingReset(true)}>
            Återställ
          </button>
          {offerRandomTicks && (
            <button
              className={`${SECONDARY_BUTTON_CLASS} ml-auto`}
              disabled={allAssigned}
              title="Bockar i en slumpad person på varje rad som ingen har ännu. Bara med inspelade kvitton."
              onClick={handleRandomTicks}
            >
              Slumpa
            </button>
          )}
        </div>
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
