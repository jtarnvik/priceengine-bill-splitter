import { Card } from '../components/common/card';
import { View } from '../components/common/view';
import { useGetLiveStateQuery } from '../communication/api/live-api';
import { ConnectionStatus } from '../types/live';
import { formatKronor } from '../util/money';

// Temporary, for watching the reconnect in phase 2; goes in phase 5.
const STATUS_TEXT: Record<ConnectionStatus, string> = {
  connecting: 'Ansluter …',
  open: 'Ansluten',
  reconnecting: 'Återansluter …',
};

// What the friends watch. Until phase 5 only which receipt it is, beside phase 2's dummy viewer count.
export function PublicPage() {
  const { data } = useGetLiveStateQuery();
  const receipt = data?.receipt;

  return (
    <View>
      <Card>
        {receipt === null && <p className="pt-2">Inget kvitto ännu.</p>}
        {receipt && (
          <div className="pt-2 grid grid-cols-[1fr_auto] gap-x-4">
            <h2 className="min-w-0 break-words text-lg font-medium">{receipt.restaurantName}</h2>
            <div className="text-lg tabular-nums whitespace-nowrap">{formatKronor(receipt.total)}</div>
          </div>
        )}
        <p className="pt-2">Tittare just nu: {data?.viewers ?? '–'}</p>
        <p className="pb-2 text-xs text-gray-500">{data ? STATUS_TEXT[data.status] : STATUS_TEXT.connecting}</p>
      </Card>
    </View>
  );
}
