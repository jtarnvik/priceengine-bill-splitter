import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { RxHamburgerMenu } from 'react-icons/rx';

export function NavMenu() {
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
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
          {/* Leads straight to the upload page until phase 3 puts the Google login in front of it; that is
              where a login lands. */}
          <button
            className="w-full text-left px-4 py-2 text-gray-800 hover:bg-gray-100 text-sm cursor-pointer"
            onClick={() => { navigate('/upload'); setMenuOpen(false); }}
          >
            Logga in
          </button>
        </div>
      )}
    </div>
  );
}
