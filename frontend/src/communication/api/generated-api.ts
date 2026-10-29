import { pbsApi as api } from "./base-api";
export const addTagTypes = ["receipt", "split", "settings", "auth"] as const;
const injectedRtkApi = api
  .enhanceEndpoints({
    addTagTypes,
  })
  .injectEndpoints({
    endpoints: (build) => ({
      setReceiptDone: build.mutation<
        SetReceiptDoneApiResponse,
        SetReceiptDoneApiArg
      >({
        query: (queryArg) => ({
          url: `/api/protected/receipt/done`,
          method: "PUT",
          body: queryArg.doneRequest,
        }),
        invalidatesTags: ["receipt"],
      }),
      setItemAssignees: build.mutation<
        SetItemAssigneesApiResponse,
        SetItemAssigneesApiArg
      >({
        query: (queryArg) => ({
          url: `/api/protected/items/${queryArg.id}/assignees`,
          method: "PUT",
          body: queryArg.assigneesRequest,
        }),
        invalidatesTags: ["split"],
      }),
      uploadReceipt: build.mutation<
        UploadReceiptApiResponse,
        UploadReceiptApiArg
      >({
        query: (queryArg) => ({
          url: `/api/protected/receipt`,
          method: "POST",
          body: queryArg.body,
        }),
        invalidatesTags: ["receipt"],
      }),
      resetReceipt: build.mutation<ResetReceiptApiResponse, ResetReceiptApiArg>(
        {
          query: () => ({ url: `/api/protected/receipt`, method: "DELETE" }),
          invalidatesTags: ["receipt"],
        },
      ),
      getSettings: build.query<GetSettingsApiResponse, GetSettingsApiArg>({
        query: () => ({ url: `/api/protected/settings` }),
        providesTags: ["settings"],
      }),
      getMe: build.query<GetMeApiResponse, GetMeApiArg>({
        query: () => ({ url: `/api/auth/me` }),
        providesTags: ["auth"],
      }),
    }),
    overrideExisting: false,
  });
export { injectedRtkApi as generatedApi };
export type SetReceiptDoneApiResponse = unknown;
export type SetReceiptDoneApiArg = {
  doneRequest: DoneRequest;
};
export type SetItemAssigneesApiResponse = unknown;
export type SetItemAssigneesApiArg = {
  id: number;
  assigneesRequest: AssigneesRequest;
};
export type UploadReceiptApiResponse = /** status 200 OK */ Receipt;
export type UploadReceiptApiArg = {
  body: {
    image: Blob;
  };
};
export type ResetReceiptApiResponse = unknown;
export type ResetReceiptApiArg = void;
export type GetSettingsApiResponse = /** status 200 OK */ SettingsResponse;
export type GetSettingsApiArg = void;
export type GetMeApiResponse = /** status 200 OK */ MeResponse;
export type GetMeApiArg = void;
export type ErrorMessageResponse = {
  message: string;
};
export type DoneRequest = {
  receiptId: string;
  done: boolean;
};
export type AssigneesRequest = {
  receiptId: string;
  people: string[];
};
export type ReceiptItem = {
  id: number;
  name: string;
  quantity: number;
  unitPrice: number | null;
  price: number;
  note: string | null;
};
export type ReceiptAdjustment = {
  name: string;
  amount: number;
};
export type Receipt = {
  receiptId: string;
  restaurantName: string;
  total: number;
  items: ReceiptItem[];
  adjustments: ReceiptAdjustment[];
  linesTotal: number;
  totalMatches: boolean;
};
export type SettingsResponse = {
  receiptParser: string;
};
export type MeResponse = {
  email: string;
};
export const {
  useSetReceiptDoneMutation,
  useSetItemAssigneesMutation,
  useUploadReceiptMutation,
  useResetReceiptMutation,
  useGetSettingsQuery,
  useGetMeQuery,
} = injectedRtkApi;
