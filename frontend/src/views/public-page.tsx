import { Card } from '../components/common/card';
import { View } from '../components/common/view';
import { ReceiptList } from '../components/receipt-list';
import { Receipt } from '../communication/api/generated-api';
import { useGetLiveStateQuery } from '../communication/api/live-api';
import { useDelayedFlag } from '../hook/use-delayed-flag';
import { useGreeting } from '../hook/use-greeting';

/**
 * Long enough that the quick reconnect after a phone is unlocked never shows the notice, short enough that a page
 * that is really cut off says so before anyone wonders why nothing changes.
 */
const RECONNECT_NOTICE_DELAY_MS = 2_000;

// What the friends watch: the receipt as the owner sees it, live, read-only.
export function PublicPage() {
  const { data } = useGetLiveStateQuery();
  const receipt = data?.receipt;
  const showReconnecting = useDelayedFlag(data?.status === 'reconnecting', RECONNECT_NOTICE_DELAY_MS);

  return (
    <View>
      {showReconnecting && <p className="text-xs text-gray-500">Återansluter …</p>}
      {/* Nothing until the first event, which comes at once: no flash of "Inget kvitto ännu" before a receipt. */}
      {receipt === null && (
        <Card>
          <p className="py-2">Inget kvitto ännu.</p>
        </Card>
      )}
      {/* Keyed, so that a new receipt mounts anew: its rows are new elements, and the greeting is decided afresh. */}
      {receipt && <PublicReceipt key={receipt.receiptId} receipt={receipt} />}
    </View>
  );
}

function PublicReceipt({ receipt }: { receipt: Receipt }) {
  const greet = useGreeting(receipt.receiptId);

  return (
    <Card>
      <h2 className="pt-2 pb-1 text-lg font-medium break-words">{receipt.restaurantName}</h2>
      <ReceiptList receipt={receipt} totalLabel="Kvar att dela på" greet={greet} />
    </Card>
  );
}
