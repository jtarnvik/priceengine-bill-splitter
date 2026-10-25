import { pbsApi as api } from "./base-api";
export const addTagTypes = ["protected", "auth"] as const;
const injectedRtkApi = api
  .enhanceEndpoints({
    addTagTypes,
  })
  .injectEndpoints({
    endpoints: (build) => ({
      protectedPing: build.query<ProtectedPingApiResponse, ProtectedPingApiArg>(
        {
          query: () => ({ url: `/api/protected/ping` }),
          providesTags: ["protected"],
        },
      ),
      getMe: build.query<GetMeApiResponse, GetMeApiArg>({
        query: () => ({ url: `/api/auth/me` }),
        providesTags: ["auth"],
      }),
    }),
    overrideExisting: false,
  });
export { injectedRtkApi as generatedApi };
export type ProtectedPingApiResponse = /** status 200 OK */ string;
export type ProtectedPingApiArg = void;
export type GetMeApiResponse = /** status 200 OK */ MeResponse;
export type GetMeApiArg = void;
export type MeResponse = {
  email: string;
};
export const { useProtectedPingQuery, useGetMeQuery } = injectedRtkApi;
