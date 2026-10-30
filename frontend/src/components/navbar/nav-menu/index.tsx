import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { RxHamburgerMenu } from 'react-icons/rx';

import { SettingsDialog } from '../../settings-dialog';
import { useLogoutMutation } from '../../../communication/api/auth-api';
import { LOGIN_ROUTE, useLoginState } from '../../../hook/use-login-state';

const ITEM_CLASS = 'block w-full text-left px-4 py-2 text-gray-800 hover:bg-gray-100 text-sm cursor-pointer';

export function NavMenu() {
  const navigate = useNavigate();
  const loginState = useLoginState();
  const [logout] = useLogoutMutation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  async function handleLogout() {
    setMenuOpen(false);
    // Whatever the answer, the refetch of /api/auth/me that the mutation triggers decides what the menu shows.
    await logout();
    navigate('/');
  }

  return (
    <div className="relative" ref={menuRef}>
      <button
        className="relative text-white text-2xl px-2 py-1 focus:outline-hidden hover:bg-brand-hover rounded-sm cursor-pointer"
        onClick={() => setMenuOpen(prev => !prev)}
        aria-label="Meny"
      >
        <RxHamburgerMenu />
      </button>
      {menuOpen && (
        <div className="absolute right-0 mt-1 w-52 bg-white rounded-sm shadow-lg z-30">
          {loginState === 'loggedIn' ? (
            <>
              <button className={ITEM_CLASS} onClick={() => { navigate('/upload'); setMenuOpen(false); }}>
                Ladda upp kvitto
              </button>
              <button className={ITEM_CLASS} onClick={() => { setSettingsOpen(true); setMenuOpen(false); }}>
                Inställningar
              </button>
              <button className={ITEM_CLASS} onClick={handleLogout}>
                Logga ut
              </button>
            </>
          ) : (
            // To the app's own login page, which shows that something is happening while the browser goes to Google.
            <button className={ITEM_CLASS} onClick={() => { navigate(LOGIN_ROUTE); setMenuOpen(false); }}>
              Logga in
            </button>
          )}
        </div>
      )}
      {/* Over whatever page is shown, so that the owner lands back where they were. */}
      {settingsOpen && <SettingsDialog onClose={() => setSettingsOpen(false)} />}
    </div>
  );
}
