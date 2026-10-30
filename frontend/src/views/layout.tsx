import { Outlet } from 'react-router-dom';

import { Navbar } from '../components/navbar';
import { useReloadOnNewVersion } from '../hook/use-reload-on-new-version';

export function Layout() {
  // Here, so that it holds on every page.
  useReloadOnNewVersion();

  return (
    <>
      <Navbar />
      <div className="h-14" />
      <Outlet />
    </>
  );
}
