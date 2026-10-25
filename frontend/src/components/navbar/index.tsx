import { useNavigate } from 'react-router-dom';
import { FaRegCreditCard } from 'react-icons/fa';

import { NavMenu } from './nav-menu';

export function Navbar() {
  const navigate = useNavigate();

  return (
    <nav className="bg-brand fixed w-full z-20 top-0 inset-s-0 border-b border-gray-200">
      <div className="min-h-[53px] max-w-(--breakpoint-xl) flex items-center justify-between gap-2 mx-auto p-2">
        {/* min-w-0 and truncate keep the menu button on the bar if the name is too long for a narrow phone:
            the name is cut with an ellipsis rather than pushing the button onto a second line. */}
        <div className="flex min-w-0 space-x-2 text-white text-2xl items-center">
          <button onClick={() => navigate('/')} className="shrink-0 pl-[4px] focus:outline-hidden cursor-pointer" aria-label="Gå till startsidan">
            <FaRegCreditCard className="w-auto h-[32px]" />
          </button>
          <span className="truncate">
            PriceEngine Bill Splitter
          </span>
        </div>
        <NavMenu />
      </div>
    </nav>
  );
}
