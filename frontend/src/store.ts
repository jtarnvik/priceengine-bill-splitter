import { configureStore } from '@reduxjs/toolkit';

import { pbsApi } from './communication/api/base-api';

// A store that holds nothing but the RTK Query cache: all state in pbs is server state.
export const store = configureStore({
  reducer: {
    [pbsApi.reducerPath]: pbsApi.reducer,
  },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(pbsApi.middleware),
});
