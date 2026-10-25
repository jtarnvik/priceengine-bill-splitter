import { Card } from '../components/common/card';
import { View } from '../components/common/view';
import { useGetLiveStateQuery } from '../communication/api/live-api';
import { ConnectionStatus } from '../types/live';

// Temporary, for watching the reconnect in phase 2; goes when phase 5 replaces the payload.
const STATUS_TEXT: Record<ConnectionStatus, string> = {
  connecting: 'Ansluter …',
  open: 'Ansluten',
  reconnecting: 'Återansluter …',
};

// What the friends watch. Until phase 5 it shows phase 2's dummy state: how many pages are connected.
export function PublicPage() {
  const { data } = useGetLiveStateQuery();

  return (
    <View>
      <Card>
        <p className="pt-2">Inget kvitto ännu.</p>
        <p className="pt-2">Tittare just nu: {data?.viewers ?? '–'}</p>
        <p className="pb-2 text-xs text-gray-500">{data ? STATUS_TEXT[data.status] : STATUS_TEXT.connecting}</p>
      </Card>
    </View>
  );
}
