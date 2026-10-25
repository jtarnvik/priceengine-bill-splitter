import { createHashRouter, RouterProvider } from 'react-router-dom';

import { Layout } from './views/layout.tsx';
import { PublicPage } from './views/public-page.tsx';
import { UploadPage } from './views/upload-page.tsx';

// HashRouter: the server never sees the client-side path, so it needs no fallback to index.html (see Build).
const router = createHashRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <PublicPage /> },
      { path: '/upload', element: <UploadPage /> },
    ],
  },
]);

function App() {
  return (
    <RouterProvider router={router} />
  );
}

export default App;
