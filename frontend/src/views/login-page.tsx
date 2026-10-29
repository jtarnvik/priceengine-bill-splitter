import { useEffect } from 'react';

import { Card } from '../components/common/card';
import { Spinner } from '../components/common/spinner';
import { View } from '../components/common/view';
import { LOGIN_URL } from '../hook/use-login-state';

/**
 * Where "Logga in" leads: it shows at once that the login has started, then leaves for Google, which can take a
 * moment over a poor connection. Without it the menu just stayed open and nothing seemed to happen.
 */
export function LoginPage() {
  useEffect(() => {
    // replace, not assign: Back from Google's page then skips this one, which would only send the browser there
    // again.
    window.location.replace(LOGIN_URL);
  }, []);

  return (
    <View>
      <Card>
        <div className="flex items-center gap-3 py-3">
          <Spinner />
          <span>Loggar in …</span>
        </div>
      </Card>
    </View>
  );
}
