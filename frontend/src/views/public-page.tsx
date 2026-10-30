import { useState } from 'react';

import { ChoosePersonDialog } from '../components/choose-person-dialog';
import { Card } from '../components/common/card';
import { View } from '../components/common/view';
import { ReceiptList, SumWarning } from '../components/receipt-list';
import { ADJUSTMENT_SHARE_LABEL, SplitPeople, SplitTotals, YourPayment } from '../components/split';
import { Receipt } from '../communication/api/generated-api';
import { useGetLiveStateQuery } from '../communication/api/live-api';
import { useDelayedFlag } from '../hook/use-delayed-flag';
import { useGreeting } from '../hook/use-greeting';
import { useMe } from '../hook/use-me';
import { Split } from '../types/live';

/**
 * Long enough that the quick reconnect after a phone is unlocked never shows the notice, short enough that a page
 * that is really cut off says so before anyone wonders why nothing changes.
 */
const RECONNECT_NOTICE_DELAY_MS = 2_000;

// What the friends watch: the receipt and its split, live, read-only.
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
      {receipt && <PublicReceipt key={receipt.receiptId} receipt={receipt} split={data?.split ?? null} />}
    </View>
  );
}

/**
 * Top to bottom (to be tried at the table): the totals, which is what each friend wants first; the items left to
 * split, the list the owner is working through; and who had what, for checking.
 */
function PublicReceipt({ receipt, split }: { receipt: Receipt, split: Split | null }) {
  const greet = useGreeting(receipt.receiptId);
  const me = useMe(receipt.receiptId);
  const [choosingAgain, setChoosingAgain] = useState(false);

  function handleChoose(name: string) {
    me.choose(name);
    setChoosingAgain(false);
  }

  return (
    <>
      <Card>
        <h2 className="pt-2 pb-1 text-lg font-medium break-words">{receipt.restaurantName}</h2>
        {split && <SplitTotals receipt={receipt} split={split} me={me.name} onChangeMe={() => setChoosingAgain(true)} />}
      </Card>
      <Card>
        {/*
          The same minimum height whether it shows "Allt är fördelat" or, once done, the reader's own Swish button, so
          that pressing "Klar" moves nothing below it.
        */}
        <div className="min-h-24">
          {split?.payment && me.name ? (
            <div className="pb-2">
              <YourPayment split={split} payment={split.payment} me={me.name} />
              <SumWarning receipt={receipt} />
            </div>
          ) : (
            <LeftToSplit receipt={receipt} split={split} greet={greet} />
          )}
        </div>
      </Card>
      {split && (
        <Card>
          <SplitPeople receipt={receipt} split={split} />
        </Card>
      )}
      {/* Once the names have come with the split: asked once per receipt (see useMe), and again on a tap on the name. */}
      {split && (!me.name || choosingAgain) && (
        <ChoosePersonDialog
          names={split.people.map((person) => person.name)}
          suggested={me.name ?? me.previousName}
          onChoose={handleChoose}
        />
      )}
    </>
  );
}

/**
 * The items nobody has been ticked for yet: an item leaves the list when the owner assigns it, so the list shrinks
 * to nothing as the split is done. The adjustments show as this list's share of them, so that the rows add up to
 * "Kvar att dela på". Until the split's event, which follows the receipt at once, everything is left.
 */
function LeftToSplit({ receipt, split, greet }: { receipt: Receipt, split: Split | null, greet: boolean }) {
  const assigned = new Set(split?.items.filter((item) => item.people.length > 0).map((item) => item.id));
  const items = receipt.items.filter((item) => !assigned.has(item.id));
  let adjustments = receipt.adjustments;
  if (split) {
    adjustments = split.unassigned.adjustmentShare !== 0
      ? [{ name: ADJUSTMENT_SHARE_LABEL, amount: split.unassigned.adjustmentShare }]
      : [];
  }
  const left: Receipt = { ...receipt, items, adjustments };

  return (
    <>
      {items.length === 0 && <p className="pt-2 text-gray-500">Allt är fördelat.</p>}
      <ReceiptList
        receipt={left}
        footer={[{ label: 'Kvar att dela på', amount: split?.unassigned.total ?? receipt.linesTotal }]}
        greet={greet}
      />
    </>
  );
}
