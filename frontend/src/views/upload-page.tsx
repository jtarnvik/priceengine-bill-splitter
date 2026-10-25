import { Card } from '../components/common/card';
import { View } from '../components/common/view';
import { LOGIN_URL, useLoginState } from '../hook/use-login-state';

// The owner's page, behind login. The upload itself arrives in phase 4.
export function UploadPage() {
  const loginState = useLoginState();

  return (
    <View>
      <Card>
        <h2 className="pt-2 font-medium">Ladda upp kvitto</h2>
        {loginState === 'loading' && <p className="pb-2">Kontrollerar inloggning …</p>}
        {loginState === 'offline' && <p className="pb-2">Servern svarar inte just nu.</p>}
        {loginState === 'loggedOut' && (
          <p className="pb-2">
            Sidan är bara för den som delar notan. <a className="text-brand underline" href={LOGIN_URL}>Logga in</a>
          </p>
        )}
        {loginState === 'loggedIn' && <p className="pb-2">Kommer i fas 4.</p>}
      </Card>
    </View>
  );
}
