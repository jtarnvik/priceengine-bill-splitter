/**
 * Generates RTK Query endpoints from the backend's OpenAPI spec. Copied from overlord.
 *
 * Run with `npm run generate-api` **while the backend is running in the `local` profile** — springdoc is switched
 * off everywhere else (see application.properties). schemaFile is the live /v3/api-docs rather than a committed
 * copy, so the generated client can never be stale relative to a spec file somebody forgot to refresh. The
 * generated file IS committed; regeneration is a deliberate act, never part of the build: the frontend is built
 * inside `mvn verify`, before any backend could run.
 *
 * .cjs rather than .ts, as in overlord: the codegen CLI can only read a TypeScript config if esbuild-runner or
 * ts-node is installed, and neither is worth adding. The JSDoc type below gives the same editor checking.
 *
 * @type {import("@rtk-query/codegen-openapi").ConfigFile}
 */

/**
 * Endpoints that must never reach the generated client, excluded by tag. `events` is the SSE stream: an
 * SseEmitter has no typed body, and the stream is the hand-written streaming endpoint in live-api.ts.
 */
const EXCLUDED_TAGS = new Set(["events"]);

module.exports = {
  schemaFile: "http://localhost:8082/v3/api-docs",
  apiFile: "./src/communication/api/base-api.ts",
  apiImport: "pbsApi",
  outputFile: "./src/communication/api/generated-api.ts",
  exportName: "generatedApi",
  hooks: true,
  // Derives providesTags/invalidatesTags from the OpenAPI tags, which are the controllers' @Tag names.
  tag: true,
  filterEndpoints: (name, definition) => {
    const tag = definition.operation.tags && definition.operation.tags[0];
    return !(tag && EXCLUDED_TAGS.has(tag));
  }
};
