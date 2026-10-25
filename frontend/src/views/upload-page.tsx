import { Card } from '../components/common/card';
import { View } from '../components/common/view';

// The owner's page. Login arrives in phase 3, the upload itself in phase 4.
export function UploadPage() {
  return (
    <View>
      <Card>
        <h2 className="pt-2 font-medium">Ladda upp kvitto</h2>
        <p className="pb-2">Kommer i fas 4.</p>
      </Card>
    </View>
  );
}
