import { pbsApi as api } from "./base-api";
export const addTagTypes = ["receipt", "auth"] as const;
const injectedRtkApi = api
  .enhanceEndpoints({
    addTagTypes,
  })
  .injectEndpoints({
    endpoints: (build) => ({
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
      getMe: build.query<GetMeApiResponse, GetMeApiArg>({
        query: () => ({ url: `/api/auth/me` }),
        providesTags: ["auth"],
      }),
    }),
    overrideExisting: false,
  });
export { injectedRtkApi as generatedApi };
export type UploadReceiptApiResponse = /** status 200 OK */ Receipt;
export type UploadReceiptApiArg = {
  body: {
    image: Blob;
  };
};
export type ResetReceiptApiResponse = unknown;
export type ResetReceiptApiArg = void;
export type GetMeApiResponse = /** status 200 OK */ MeResponse;
export type GetMeApiArg = void;
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
export type ErrorMessageResponse = {
  message: string;
};
export type MeResponse = {
  email: string;
};
export const {
  useUploadReceiptMutation,
  useResetReceiptMutation,
  useGetMeQuery,
} = injectedRtkApi;
