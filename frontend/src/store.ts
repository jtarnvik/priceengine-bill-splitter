import { configureStore } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query';

import { pbsApi } from './communication/api/base-api';

// A store that holds nothing but the RTK Query cache: all state in pbs is server state.
export const store = configureStore({
  reducer: {
    [pbsApi.reducerPath]: pbsApi.reducer,
  },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(pbsApi.middleware),
});

// Lets a query refetch when the page comes back into view or the network returns (refetchOnFocus,
// refetchOnReconnect); useLoginState relies on it to notice a session that ended while the phone was locked.
setupListeners(store.dispatch);
