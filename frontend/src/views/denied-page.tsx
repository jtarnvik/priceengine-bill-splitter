import { Link } from 'react-router-dom';

import { Card } from '../components/common/card';
import { View } from '../components/common/view';

// Where a refused login lands (the security config's failureUrl): any Google account other than the owner's.
export function DeniedPage() {
  return (
    <View>
      <Card>
        <h2 className="pt-2 font-medium">Inloggningen nekades</h2>
        <p className="pb-2">
          Bara den som delar notan kan logga in. Allas summor syns på <Link className="text-brand underline" to="/">startsidan</Link>.
        </p>
      </Card>
    </View>
  );
}
