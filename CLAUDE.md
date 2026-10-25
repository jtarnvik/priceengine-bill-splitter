# CLAUDE.md — pbs

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`{{BASE_PACKAGE}}` = `com.tarnvik.pbs`

Prefer 125 character wide lines in this file where the format allows it.

## Session start

Before generating Java code, read `src/main/resources/checkstyle.xml` (copied from publicbackend, enforced by
`mvn verify`); before TypeScript or React code, read the frontend's `eslint.config.js`. The ESLint config does
not exist yet — phase 2 copies it from sl-dashboard, so all three projects share one style.

The build needs **JDK 25**. `.java-version` pins it through jenv, as in publicbackend; without it jenv's global
default (21 on the dev machine) wins and the `--release 25` compile fails.

## Reference implementations

Much of this project is a copy of something already working in a sibling repository. The repos sit side by side
under one parent directory, so the paths below are relative to this repo's root. Read the original before
writing the copy, and adapt rather than reinvent.

| What | Where | Used in phase |
|---|---|---|
| `just` deploy flow | `../publicbackend/justfile`, `build.just.template`, `change-version.sh`, the `maven-antrun-plugin` execution in its `pom.xml` | 1 |
| Start script and env template | `../publicbackend/deployment/start-publicbackend.sh`, `publicbackend.env.example` | 1 |
| `production` profile, file logging | `../publicbackend/src/main/resources/application-production.properties`, `logback-spring.xml` | 1 |
| Checkstyle rules | `../publicbackend/src/main/resources/checkstyle.xml` | 1 |
| ESLint config | `../sl-dashboard/eslint.config.js` | 2 |
| SSE backend | `../cl-media/overlord/src/main/java/com/tarnvik/overlord/port/outgoing/SseNotificationProvider.java` and `port/incoming/rest/SseController.java` | 2 |
| SSE frontend | `../cl-media/overlord/frontend/src/communication/live-updates.tsx` | 2 |
| SSE notes | `../cl-media/sse-enterprise-gotchas.md` | 2 |
| RTK Query on axios | `../cl-media/overlord/frontend/src/communication/axios-base-query.ts` and `api/base-api.ts` | 2 |
| OpenAPI codegen | `../cl-media/overlord/frontend/openapi-config.cjs`, springdoc in overlord's `pom.xml`, the springdoc/codegen notes in `../cl-media/CLAUDE.md` | 3 |
| OAuth2 security config | `../publicbackend/src/main/java/com/tarnvik/publicbackend/config/SecurityConfig.java` | 3 |
| Login state in React | `../sl-dashboard/src/hook/use-user.ts` and the `user` handling in `App.tsx` | 3 |
| Anthropic Java SDK usage | `../publicbackend/src/main/java/com/tarnvik/publicbackend/commuter/port/outgoing/rest/claude/ClaudeProvider.java` | 4 |

**Do not `@`-import publicbackend's `CLAUDE.md`** the way sl-dashboard does. Most of it is GTFS and commuter
history that has nothing to do with this project and would only crowd the context. Copy what applies into this
file instead.

What *not* to copy from publicbackend: Spring Session JDBC, `SameSite=None`, the CORS configuration and its
two frontend-origin properties, the pending-user flow in the success handler, Liquibase, the terminal
dashboard. Each exists there because of a separate frontend origin or a database, and pbs has neither.

## Status

**Phase 1 done (2026-10-25); phase 2 next, and it needs its step plan first.** pbs `v0.1.1` runs on the Mini
and answers `/ping` at `https://pbs.tarnvik.com`, behind Cloudflare and Caddy, monitored by UptimeRobot, its
log collected by the log-retention stack; the Google OAuth client exists for phase 3. The app itself is still
only `/ping`. The design below was worked out in one discussion (October 2026) before the repository existed.
Treat it as decided unless a step proves it wrong, and "Phases, in order" as the plan. Remove planning text
from this file as it is carried out, keeping the "why" notes.

**Base structure before functionality.** The first three phases build and prove the frame — infrastructure,
a two-page shell, login — and contain no receipt, split or Claude code at all. Nothing functional starts until
all three work end to end in production.

**How a phase is worked.** Before a phase starts, it gets a numbered step-by-step plan in this file, discussed
first (phases 4 onwards are deliberately thin and need that discussion most). Claude implements; the owner
verifies. Every step ends with a **Verify** list of concrete checks — commands with their expected output where
possible — and a step is done when those pass, not when the code is written. Infrastructure steps are the
owner's hands with Claude walking through them; host-specific details stay in the manual (see Infrastructure)
and the plan points there rather than copying them. Mark a step ✅ in its heading when it is verified.

## Purpose

A bill splitter for four friends who eat out together. One of them (the owner) photographs the receipt, the
backend has Claude read it into items, the owner ticks which of the four had each item, and the other three watch
their running totals update live on a public page. They can change nothing; if they spot a mistake they say so
at the table and the owner fixes it.

A learning project first — Spring Boot 4, a React app served from the same jar, Server-Sent Events, the Anthropic
Java SDK — and deliberately small: four hardcoded people, one receipt at a time, nothing stored.

- **No database.** All state is in memory and lost on restart, which is accepted: a split lives for one dinner.
- **One receipt at a time.** Uploading a new one replaces the old.
- **The four people are hardcoded**, first names only, in `application.properties`.
- **The repository is public.** Nothing in it may identify the owner beyond what a first name does: the allowed
  Google email, the client secret and the Anthropic key all come from the environment.

## Architecture

```
Browser (owner's phone, friends' phones)
        ↓ HTTPS, HTTP/2
Cloudflare (proxied CNAME pbs → api2.tarnvik.com)
        ↓ HTTPS (Cloudflare origin certificate)
Caddy on the Mac Mini, :443
        ↓ plain HTTP, localhost only
Spring Boot, :8082, profile `production` — serves the React app AND the API
        ↓
Anthropic API (receipt parsing only)
```

**One origin for the app and the API**, unlike sl-dashboard (GitHub Pages) + publicbackend (api2). Consequences,
all of them simplifications:

- **No CORS configuration at all.** None of the `FRONTEND_URL` / `ALLOWED_ORIGINS` failure modes publicbackend
  documents can happen here.
- **The session cookie is first-party**, so `SameSite=Lax` works where publicbackend needs `None`. Lax also stops
  a cross-site POST from carrying the cookie, which is what makes leaving CSRF disabled defensible.
- **Sessions are in memory** (no Spring Session JDBC). A restart logs the owner out, which matches everything else
  being lost on restart.

Spring Boot 4.1.x on Java 25, matching publicbackend. Java 24 (overlord) is end-of-life — do not copy that.

## Infrastructure

Host-level details — Caddyfile, certificates, Cloudflare account, firewall, GoDNS — are in
`~/develop/repos/personal/home-infra-manual.md`, **outside every repository**. This repository is public and the
manual describes the home network. Do not copy host details into this file. The manual's "pbs Setup Checklist"
lists the infrastructure steps in order.

What pbs itself must get right:

- **Port 8082, in every profile.** Overlord holds 8080 and publicbackend 8081 on the Mini. Using 8082 in dev too
  means the three can run side by side on the dev machine, and no profile needs a port override.
- **`pbs` is a proxied CNAME to `api2.tarnvik.com`, never an A record.** GoDNS keeps only `api2` up to date, so an
  A record would be left with the old IP after the next WAN IP change and fail silently.
- **`server.forward-headers-strategy=native`** in `production`. Caddy forwards plain HTTP; without this Spring
  builds an `http://` OAuth2 redirect URI and Google rejects it.
- **`server.address=127.0.0.1`** in `production` — a deliberate difference from publicbackend. Caddy is the only
  legitimate client. Bound to all interfaces, anything on the LAN could reach :8082 directly and skip
  Cloudflare; and since Tomcat trusts `X-Forwarded-*` headers from private-network addresses by default, it
  could also claim to be HTTPS. For the same reason Caddy's block says `127.0.0.1:8082`, not `localhost`, which
  can resolve to `::1`.
- **A host name Caddy has no block for gets an empty HTTP 200**, not an error: the wildcard origin certificate
  still completes the handshake. So a status code alone does not prove a request reached pbs — look for the
  `ok` body. The UptimeRobot monitor checks only the status (keyword monitors need a paid plan), so a Caddyfile
  that loses pbs's block shows as Up.
- **Cloudflare abandons a response after 100 seconds (524).** A receipt parse takes perhaps 10–30 s, so a
  synchronous request is fine. The SSE stream relies on its heartbeat (see Live updates).
- **Caddy flushes `text/event-stream` immediately** — no proxy configuration is needed for SSE. Cloudflare
  normally streams it unbuffered too, but that is the one link not confirmed in advance; phase 2 confirms
  it before anything depends on it.
- **The log file format is a contract with the log-retention stack.** On the Mini, Grafana Alloy follows
  `deployment/logs/pbs.log` into Loki (30 days, Grafana as the UI; manual: "Log retention — Loki + Grafana").
  It reuses publicbackend's parser, which works only while these three things stay true:
  - **The live file keeps its fixed name and rolled files get another.** Alloy reads exactly one file, never a
    glob; if it also matched the rolled files, every day would be stored twice. Boot's default rolling
    (`pbs.log.%d{yyyy-MM-dd}.%i.gz`) does this.
  - **The line is Boot's default file pattern, with `spring.application.name=pbs` set.** The name supplies the
    `[pbs]` bracket the parser expects. *Why this note exists:* a custom logback pattern, JSON logging or
    Micrometer tracing (which inserts `[traceId-spanId]` before the logger name) each change the line, and the
    failure is silent — lines lose their `level` or stop parsing. Any of them needs a parser change on the Mini
    first; check the manual's "What a service must do to be collected" before turning one on.
  - **Every entry starts with a timestamp.** Lines without one, such as stack-trace lines, are attached to the
    entry before them, which is how a stack trace stays one entry. Boot does this already.

  The newest lines can reach Grafana late — see "After the project". Nothing is lost.

## Authentication

Google OAuth2 via Spring Security, admitting exactly one Google account: the owner's.

- **A separate OAuth client** in the same Google Cloud project as the commuter app, not extra redirect URIs on
  that client — each app then has its own secret and can be revoked alone. The consent screen is per project, and
  the project is **in production** (the commuter app needs that), not in test mode as first assumed. So Google
  lets any account through to pbs, and the email check below is the only gate — which is why phase 3 tests that a
  second account is refused. The consent screen also shows the commuter app's name, not pbs's. Fine for an app
  only the owner logs in to; a project of its own would give pbs its own name and a test-mode gate.
- **Redirect URIs:** `https://pbs.tarnvik.com/login/oauth2/code/google` and
  `http://localhost:5173/login/oauth2/code/google` (dev, through the Vite proxy — see Development).
- **Reject in a custom `OidcUserService`, not in the success handler.** Compare the `email` claim (and require
  `email_verified`) against `PBS_ALLOWED_EMAIL`, and throw `OAuth2AuthenticationException` on a mismatch, so no
  session is ever created for anyone else. publicbackend checks in the success handler and invalidates the session
  afterwards because it has to record pending users; with a single fixed user there is nothing to record.
- **The allowed email comes from the environment**, never from a tracked file. The repo is public.
- **There is no login page.** The upload page shows a "Logga in" button linking to `/oauth2/authorization/google`
  when `/api/auth/me` answers 401. With a single provider, Spring redirects straight to Google. The success
  handler redirects to `/#/upload`.

### Security configuration — the two that bite

1. **Permit the static files explicitly** — `/`, `/index.html`, `/assets/**`, `/favicon.ico`. With
   `.anyRequest().authenticated()` and `oauth2Login`, a friend opening the public page would be bounced to Google
   just to load the JavaScript bundle.
2. **Answer 401 for `/api/**`, never a redirect.** Reuse publicbackend's `defaultAuthenticationEntryPointFor`
   with a `PathPatternRequestMatcher` on `/api/**`. A redirect to Google on a `fetch` fails in a way that looks
   like a CORS error and explains nothing.

Also set `server.servlet.session.cookie.same-site=lax` explicitly. A cookie with no `SameSite` attribute is
treated differently by different browsers, and the CSRF reasoning above depends on Lax.

**The paid endpoint must be behind login, and a test must say so.** The parse endpoint is the only one on the
internet that costs money per call. One integration test asserts that an anonymous `POST` to it gets 401 — cheap
insurance against a security matcher reordered later.

## Build — one jar holding the app and the API

**Use `frontend-maven-plugin` (`com.github.eirslett`)**, so that `mvn verify` alone produces the complete jar.

- It downloads a **pinned Node and npm** (`nodeVersion` / `npmVersion` in the pom) into the project, then runs
  `npm ci` and `npm run build` in `generate-resources`.
- Vite's `outDir` points at `../target/classes/static`, which needs `emptyOutDir: true` because it is outside
  the Vite root. Nothing is copied into `src/main/resources/static`.
- Skip the frontend during backend-only work with `-Dskip.npm` (and `-Dskip.installnodenpm`).
- Gitignore the plugin's downloaded `node/` directory.

*Why not cl-media's approach.* Overlord builds the frontend in the justfile (`npm ci`, `vite build`,
`cp frontend/build → src/main/resources/static`) and then runs `mvn install`. Two weaknesses, both fixed here:

- **A plain Maven build does not include the frontend.** `mvn package` from IntelliJ, or overlord's own
  `run_dashboard` recipe, packages whatever is left in `static/` from the last `just build` — stale or empty.
  Only the justfile knows the frontend exists.
- **Node and npm come from the machine.** Overlord's `CLAUDE.md` has a whole section on pinning Node/npm on both
  machines with corepack after an npm 11.6.2 lockfile bug broke `npm ci`. That fix is per machine. With the
  plugin, the version is in the pom and both machines get the same one, with no corepack involved.

The cost is slower Maven builds during backend work, which `-Dskip.npm` answers.

**Routing uses `HashRouter`**, as overlord does. The server never sees client-side paths, so no fallback that
forwards unknown paths to `index.html` is needed, and the static-file permit list stays short. Two pages:
`/#/` (public) and `/#/upload` (owner).

## Development

Backend on :8082 (`-Dspring.profiles.active=local`), Vite on :5173. **Vite proxies `/api`, `/oauth2`,
`/login/oauth2` and `/logout` to :8082**, leaving the `Host` header alone (no `changeOrigin`).

*Why proxy the auth paths too:* Spring builds the OAuth2 redirect URI from the request's host, so with the
`Host: localhost:5173` header preserved, Google sends the browser back through Vite. Dev is then single-origin
exactly like production: the success handler can redirect to a relative `/#/upload` in both, the session cookie
belongs to :5173, and no `frontend-url` property is needed anywhere. Verify on the first dev login that the
redirect URI Google receives really is `localhost:5173` — if Vite rewrites the host, set `changeOrigin: false`
explicitly.

## Deployment

Same `just` flow as publicbackend: `change-version.sh`, `build.just.template` (with the `maven-antrun-plugin`
execution that generates `build.just`), the root `justfile` and `deployment/start-pbs.sh` are renamed copies of
its files. Read publicbackend's `CLAUDE.md` "Deployment" for the reasoning, especially why `just doit` runs
`just` again for each step.

- Tag-driven: `./change-version.sh <version>` on the dev machine, push the commit **and** the tag, then Ctrl-C
  the running pbs and `just doit` on the Mini. That leaves the clone on the tag in detached HEAD, as expected.
  The deploy worked when the startup log says `Starting PbsApplication v<version>` and `deployment/bin/` holds
  `Version-<version>.txt`.
- **The antrun copy is `failonerror="true"`.** publicbackend's `false` is a Render leftover; a failed copy must
  stop `change-version.sh`, not leave a tag without its `build.just`.
- **The production clone sits next to publicbackend's, in a folder with the repository's name.** The manual's
  log-retention table names that path, and Docker only sees the folders it shares.
- **`just build` shrinks to `mvn verify`** plus copying into `deployment/bin` — the frontend is built inside
  Maven, so there is no separate frontend recipe.
- Run directory `deployment/bin`, so `mvn clean` cannot delete the jar from under a running instance.
- Profile `production`, set by the start script. Deployment-only behaviour is gated positively on it, as in
  publicbackend.
- Secrets in `deployment/pbs.env` on the Mini (gitignored, `chmod 600`), copied from a committed
  `deployment/pbs.env.example`. The example lists every variable from the start, each marked with the phase
  that begins reading it, so the file on the Mini is written once. The Google values are filled in; the
  Anthropic key waits for phase 4.
- Heap: `JAVA_OPTS=-Xmx512m`. The Mini also runs publicbackend (`-Xmx2g`), overlord and MySQL.
- Runs in a foreground terminal, like publicbackend. UptimeRobot on `/ping` is the only thing that will report it
  down; its Down and Up alerts were tested once, on purpose.

### Environment variables

| Variable | Description |
|---|---|
| `GOOGLE_CLIENT_ID` | pbs's own OAuth client, not the commuter one |
| `GOOGLE_CLIENT_SECRET` | |
| `PBS_ALLOWED_EMAIL` | The single Google account admitted |
| `ANTHROPIC_API_KEY` | pbs's own key — see below |
| `JAVA_OPTS` | `-Xmx512m` |

**Give pbs its own Anthropic key, ideally in its own Console workspace with a spending limit.** A leak or a bug
then costs at most the limit, and the key can be revoked without touching publicbackend's deviation
interpretation.

## Receipt parsing

### In the browser, before upload

**Downscale the photo on a `<canvas>` to about 2000 px on the long edge and export JPEG.** One step solves four
problems:

- **Size.** A phone photo is several MB — over Spring's 1 MB multipart default and needlessly large for the API.
  Current models use at most 2576 px on the long edge anyway.
- **HEIC.** iPhones shoot HEIC, which the API does not accept (JPEG, PNG, GIF, WebP). The canvas always emits
  JPEG.
- **EXIF.** The canvas drops metadata, GPS included — which matters the day a receipt is photographed at home.
- **Speed.** A few hundred KB uploads quickly over mobile data at the table.

Still set `spring.servlet.multipart.max-file-size` (and `max-request-size`) to a few MB as headroom.

### The Claude call

- Anthropic Java SDK (`com.anthropic:anthropic-java`), model `claude-opus-5-5`. A receipt costs a few cents.
- **Structured output, typed.** `MessageCreateParams.builder()...outputConfig(Receipt.class)` gives a
  `StructuredMessageCreateParams<Receipt>` and the response comes back as that class. Jackson's
  `@JsonPropertyDescription` on the fields becomes part of the schema, so the instructions about each field live
  next to the field.
- **Thinking cannot be disabled on Opus 5.5** and effort defaults to `medium` — set effort explicitly rather than
  relying on the default.
- **Check `stop_reason` before reading the content.** A `refusal` is unlikely for a receipt but has to produce an
  error message, not a crash.

### The schema

- **Money as integer öre (`long`)**, never `double`, in the schema as well as in Java, so the model never answers
  `24.999999`.
- **One row per unit.** "3 × Öl 237 kr" must come back as three rows of 79 kr, stated in both the prompt and the
  field description. *Why:* the split divides an item evenly between the people ticked, so a quantity line where
  one person had one beer and another had two cannot be expressed. Three rows can.
- **The receipt's own total** (the "Att betala" line) as a separate field.
- **Discounts and rounding lines as their own field**, not as negative items. They are the usual reason the items
  fail to add up, and they are spread differently (see The split).

### The sum check

**Compare the items plus discounts against the receipt's own total in Java, and show a warning when they
differ.** A misread price is the error that actually matters in a bill splitter, and this catches it without
trusting the model. The owner then corrects the row (phase 6) rather than taking a new photo.

Use the **pre-payment check** where possible: it carries the place and the items and nothing else — no card
digits, and no tip to complicate the total.

## The split

The owner's page is a table: one row per item, four checkboxes per row, one per person.

- **An item is divided evenly between the people ticked.** One tick is a personal item, several is shared. No
  tick is unassigned.
- **Exact while computing, rounded once at the end.** Each person's shares are kept exact and summed; only the
  person's total is rounded, to whole kronor. *Why not round per item:* the errors accumulate across a long
  receipt. Rounding once per person bounds the drift to ±2 kr (four people, at most 50 öre each). Exactness:
  `BigDecimal` with a few decimals per share is plenty. A fun alternative: since an item is divided by 1–4,
  every share is an exact integer in units of 1/12 öre.
- **Show the drift instead of hiding it.** The public page shows the receipt total next to the sum of the four
  rounded totals. The friends round up as they see fit when paying.
- **Discounts are spread in proportion to each person's subtotal.** Unassigned items count as a fifth bucket
  while the owner is still ticking, so the numbers always add up.
- **A tip is not on the pre-payment check.** If one is ever wanted, it is a row added by hand and split through
  the same checkboxes (phase 6).

### The assignment request

**Send the full set of people for a row, never a toggle:**
`PUT /api/protected/items/{index}/assignees` with `{"receiptId": "...", "people": ["Anna", "Erik"]}`.
*Why:* a toggle depends on the order requests arrive in. Two quick taps over a poor connection, with one retried
or reordered, leaves the box the opposite of what the screen shows. Setting the whole set gives the same result
however often it arrives.

`receiptId` is regenerated on each upload, and a request carrying an old one gets 409. Otherwise a second open
tab could apply ticks meant for the previous receipt to the new one by row index.

### State

The whole split is one immutable record — receipt id, items with their assignees, totals — in an
`AtomicReference`. Every change builds a new record, swaps it in and broadcasts it. Only the owner writes, so
there is no contention to design for, and an immutable snapshot can be serialised for every listener with no
locking.

## Live updates — Server-Sent Events

Friends' pages are read-only and data only flows from the server to the phones, so **SSE, not WebSockets**.
Overlord's `SseNotificationProvider` (emitter registry, 20 s comment heartbeat, dead-emitter cleanup) is the model
for the backend — copy the mechanics.

**Send the whole state on every change, not a "something changed" signal.** Overlord broadcasts invalidation
signals and the browser refetches, which is why its `live-updates.tsx` needs throttles and a `refreshAll()` on
every reconnect. Here the whole state is a few hundred bytes, so each event carries all of it.

- **Send the current state as the first event on connect.** A reconnect is then indistinguishable from a first
  connect, nothing on the phone ever fetches, and the response starts at once rather than idling towards
  Cloudflare's 100 s limit.
- **Keep the heartbeat** (`: ping` every 20 s), for Cloudflare's idle timeout.
- **Phones lock their screens mid-dinner.** iOS suspends the page and the connection dies; overlord's
  `live-updates.tsx` already notes that iOS can give up for good (`readyState` stuck at `CLOSED`). On
  `visibilitychange` back to visible, reopen the `EventSource` if it is closed. The state-on-connect rule makes
  that the entire recovery.
- The browser's 6-connections-per-site limit does not apply: Cloudflare serves the browser over HTTP/2.
- The stream endpoint is public and read-only: `GET /api/public/events`.

## Frontend data layer — RTK Query on axios

**RTK Query for all server data, axios underneath, a Redux store that holds nothing else.** Overkill for a handful
of endpoints, chosen on purpose: the owner is learning RTK Query and continues here. Overlord already has the same
stack, so its files are the reference (see Reference implementations).

- **The SSE stream is an RTK Query streaming endpoint.** One query whose `onCacheEntryAdded` opens the
  `EventSource` and writes each event into the cache with `updateCachedData`; both pages just call its hook.
  This is RTK Query's documented streaming pattern, and a different technique from overlord's (signal, then
  refetch) — it fits because every event carries the whole state. The `visibilitychange` reopen lives in the
  same function. The `EventSource` is plain browser API; it does not go through axios.
- **Mutations need no tag invalidation** for the split state: the server broadcasts the new state after every
  write, and the stream updates the cache. Optimistic updates with `updateQueryData` are an option for the
  checkboxes, so a tick shows at once over a poor connection.
- **axios via a custom `axiosBaseQuery`**, RTK Query's documented pattern for replacing `fetchBaseQuery`, copied
  from overlord.
- **DTOs are TypeScript `interface`s/`type`s, never classes.** The store and the RTK Query cache expect plain,
  serialisable objects: class instances trip the serialisability middleware, Immer (behind `updateCachedData`)
  does not draft them, and their methods are lost on the first spread.
- **Generated client: `@rtk-query/codegen-openapi`** from the backend's springdoc spec, as overlord does. Read
  overlord's `openapi-config.cjs` and the springdoc/codegen notes in `../cl-media/CLAUDE.md` first: in short,
  run generation by hand against the running dev backend and commit the output, never as part of the build (the
  frontend is built inside `mvn verify`, before any backend could run); exclude the stream endpoint; operationIds
  are the Java method names, so a rename is a hook rename; response fields need `@Schema(requiredMode = REQUIRED)`
  and request fields Bean Validation (boxed, with `@Valid`), or every generated field is optional.
- **springdoc only in the `local` profile.** The app is on the public internet, and `/v3/api-docs` plus Swagger
  UI would publish the whole API surface. Off by default, on in `local`, which is the only place codegen runs.
- **The stream's payload type is invisible to springdoc** (an `SseEmitter` has no typed body). Hand-write that
  interface, or register its schema with the spec — decided when phase 2 is planned.

## The two pages

**`/#/` — public, read-only.** What the friends watch.
- Each person's items, not only their total. The point is for friends to catch the owner's mistakes, and a total
  alone says something is wrong without saying what.
- Each person's rounded total, the receipt total, the sum of the rounded totals, and the **unassigned** amount —
  so the arithmetic is visible and shows how far the owner has got.
- An empty state ("Inget kvitto ännu") for before the first upload and after a restart.

**`/#/upload` — the owner, behind login.** Upload, the parsed table with its checkboxes, the sum-check warning,
and later corrections.
- It is filled in at the table on a phone, so four checkbox columns, a price and a name must fit in 375 px:
  **initials as column headers**, **tap targets larger than the checkbox itself**, and item names that wrap or
  truncate — price and checkboxes are what matter.

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/ping` | Public | Health check for UptimeRobot |
| GET | `/api/auth/me` | Optional | The owner, or 401 |
| GET | `/api/public/events` | Public | SSE stream; current state on connect, then on every change |
| POST | `/api/protected/receipt` | Owner | Multipart image → parse → replaces the state, broadcasts it |
| PUT | `/api/protected/items/{index}/assignees` | Owner | The full set of people for one row; 409 on a stale `receiptId` |
| POST | `/logout` | Owner | Ends the session |

Corrections (edit a row, add a row, remove a row) arrive with phase 6.

## Open questions

- UI language — Swedish like sl-dashboard is assumed ("Logga in", "Inget kvitto ännu").
- The four first names, for `application.properties`.

---

## Phases, in order

Each phase is small and ends in something that can be seen working **in production**, not only locally.

### Base structure — no functionality

The frame is built and proven first, because infrastructure and auth are the parts most likely to surprise, and
a surprise there is cheapest when no feature depends on the frame yet.

**Phase 1 - Infrastructure and communication** ✅ (2026-10-25). Everything between the internet and a running
jar, with the app itself reduced to `/ping`: the project and its deploy files, the Cloudflare CNAME, the
certificate check, Caddy's block, the first deploy, log retention, UptimeRobot and the Google OAuth client, then
a second tagged version (`v0.1.1`) deployed the same way to prove the flow repeats. What it established is now
in Infrastructure, Deployment and Authentication; host-side details are in the manual. The worked step plan is
in git history (`git log -- CLAUDE.md`), as a model for the next phase's.

**Phase 2 - A two-page shell.** The one-jar build and the frontend frame, with placeholder content.
- `frontend-maven-plugin` with pinned Node/npm, Vite output into `target/classes/static`, `mvn verify` producing
  the single jar. The Vite dev loop with its proxy.
- React with `HashRouter` and two placeholder pages, `/#/` and `/#/upload`, and a way to move between them.
- The data layer's frame: Redux store, the RTK Query base api with `axiosBaseQuery`, and the stream as an
  `onCacheEntryAdded` streaming query (see Frontend data layer).
- **The public page's SSE wiring, carrying a dummy counter** instead of a split: the emitter registry,
  state-on-connect, the heartbeat and the `visibilitychange` reconnect. It is part of the frame, not a feature —
  the real state later replaces only the payload. It also settles the one link in the chain not confirmed in
  advance: open the page on a phone over mobile data and watch events arrive one at a time rather than in bursts,
  then lock and unlock the phone to exercise the reconnect.
- Done when both pages load from `https://pbs.tarnvik.com` and the counter ticks on a phone.

**Phase 3 - Login on the upload page.** The security frame, still with nothing behind it worth protecting.
- Security config with the two gotchas above (static files permitted, 401 for `/api/**`), `SameSite=Lax`, the
  email check in `OidcUserService`, `/api/auth/me`, logout.
- The upload page shows a "Logga in" button when logged out and the owner's name plus "Logga ut" when logged in.
  The public page stays open to all.
- One placeholder protected endpoint, so the 401 rule has something real to guard before the paid endpoint
  exists.
- springdoc (in `local` only) and `@rtk-query/codegen-openapi`, with `/api/auth/me` as the first generated
  endpoint — the first real JSON endpoint, since phase 2's only one is the stream, which codegen excludes.
- Tests: anonymous access to `/api/protected/**` gets 401; a different email is refused.
- Done when the owner can log in and out on `https://pbs.tarnvik.com` from a phone, and a second Google account
  is refused — and the dev login through the Vite proxy works too.

### Functionality

**Phase 4 - Parse a receipt.** Canvas resize in the browser, the multipart endpoint, the Claude call with the typed
schema, the sum check. Needs pbs's own Anthropic key. The upload page shows the parsed rows and any warning. No
splitting yet.

**Phase 5 - Split and broadcast.** Checkboxes, the assignment endpoint, the split calculation, the state record replacing
the dummy counter on the SSE stream, the public page showing per-person items and totals. The real dinner test.

**Phase 6 - Corrections.** Edit a row's name or price, add a row by hand (a tip, a misread item), remove a row.

**Phase 7 - Phone polish.** Layout at 375 px, tap targets, empty states.

---

## After the project

Things found along the way that belong to something other than pbs, to fix once the phases are done.

### Log-retention lag — Docker Desktop's file sharing

**Found while joining the log-retention stack in phase 1, 2026-10-25.** Lines reach Grafana, but sometimes
tens of minutes or hours late — pbs's startup lines took about three hours, and publicbackend's lines were more
than 20 minutes behind on the same day. Nothing is lost. The lines hit hardest are the last ones before a
service goes quiet, which are the ones wanted after a crash.

**Cause, established by experiment.** Alloy runs in a container and reads the logs through Docker Desktop's
file sharing. For a log file that a host process keeps open and appends to, while Alloy holds it open inside the
container, the container's view of the file can stay stale: its size and modification time freeze, and Alloy
cannot read what the container does not see. Alloy itself delivers everything the container sees — Grafana
stopped exactly where the container's view of the file stopped. A fresh file, appended to with `echo` and read
with a new `ls` each time, showed the change within a second, so the sharing is not slow in general.

*A misleading signal:* Alloy's `positions.yml` lags behind what has been delivered, so it is no measure of how
far Alloy has got. Compare the file's size on the host with its size inside the container instead.

**Fix, to be decided.** It belongs to the log-retention stack and the manual, not to pbs:
- **Run Alloy natively on the Mini (Homebrew)** instead of in Docker — the preferred option, since it removes
  file sharing from the log path entirely.
- Or switch Docker Desktop's file-sharing implementation (Settings → General) and test again. Cheaper, but it
  replaces one layer with another.

Either way, the check afterwards is the one that found it: append a line to a service's log on the host, and
see it in the container (`ls -l` on both sides) and in Grafana within seconds — repeatedly, over an afternoon.
