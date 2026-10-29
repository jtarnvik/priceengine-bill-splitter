import { createHashRouter, RouterProvider } from 'react-router-dom';

import { DeniedPage } from './views/denied-page.tsx';
import { Layout } from './views/layout.tsx';
import { LoginPage } from './views/login-page.tsx';
import { PublicPage } from './views/public-page.tsx';
import { UploadPage } from './views/upload-page.tsx';

// HashRouter: the server never sees the client-side path, so it needs no fallback to index.html (see Build).
const router = createHashRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <PublicPage /> },
      { path: '/upload', element: <UploadPage /> },
      { path: '/denied', element: <DeniedPage /> },
      { path: '/login', element: <LoginPage /> },
    ],
  },
]);

function App() {
  return (
    <RouterProvider router={router} />
  );
}

export default App;
