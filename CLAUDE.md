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

**Phase 1 in progress.** The project is generated (Spring Initializr, Boot 4.1.2, Java 25) and builds with
checkstyle; nothing beyond that is built yet. The design below was worked out in one discussion (October 2026)
before the repository existed. Treat it as decided unless a step proves it wrong, and "Phases, in order" as the
plan. Remove planning text from this file as it is carried out, keeping the "why" notes.

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

## Authentication

Google OAuth2 via Spring Security, admitting exactly one Google account: the owner's.

- **A separate OAuth client** in the same Google Cloud project as the commuter app, not extra redirect URIs on
  that client — each app then has its own secret and can be revoked alone. The consent screen is per project and
  is in test mode with the owner as a test user, which is all this app needs.
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

Same `just` flow as publicbackend. Copy and rename its `change-version.sh`, `build.just.template` (with the
`maven-antrun-plugin` execution that generates `build.just`), the root `justfile` and
`deployment/start-publicbackend.sh`. Read publicbackend's `CLAUDE.md` "Deployment" for the reasoning, especially
why `just doit` runs `just` again for each step.

- Tag-driven: `./change-version.sh <version>` on the dev machine, push the commit **and** the tag, then
  `just pull`, `just build`, `just start` (or `just doit`) on the Mini.
- **`just build` shrinks to `mvn verify`** plus copying into `deployment/bin` — the frontend is built inside
  Maven, so there is no separate frontend recipe.
- Run directory `deployment/bin`, so `mvn clean` cannot delete the jar from under a running instance.
- Profile `production`, set by the start script. Deployment-only behaviour is gated positively on it, as in
  publicbackend.
- Secrets in `deployment/pbs.env` on the Mini (gitignored, `chmod 600`), copied from a committed
  `deployment/pbs.env.example`.
- Heap: `JAVA_OPTS=-Xmx512m`. The Mini also runs publicbackend (`-Xmx2g`), overlord and MySQL.
- Runs in a foreground terminal, like publicbackend. UptimeRobot on `/ping` is the only thing that will report it
  down.

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

**Phase 1 - Infrastructure and communication.** Everything between the internet and a running jar, with the app
itself reduced to `/ping`.
- Spring Boot 4.1 / Java 25 project answering `/ping` on :8082, profile `production`, with
  `forward-headers-strategy=native`.
- The `just` files, `change-version.sh`, the start script and `deployment/pbs.env.example` — the full tag-driven
  deploy flow, run once for real.
- The infrastructure steps in the manual's "pbs Setup Checklist": Cloudflare CNAME, certificate check, Caddy
  block, UptimeRobot. Create the Google OAuth client now as well, so phase 3 only consumes it.
- Done when `https://pbs.tarnvik.com/ping` answers from off the machine, and a second tagged version has been
  deployed the same way — proving the flow repeats, not only that it ran once.

#### Phase 1 — step by step

Steps 1.4, 1.5 and 1.9 do not depend on the code and can be done while 1.1 is being written. The order below
is the one in which each check builds on the previous: DNS, then Caddy, then the app, so that when something
fails, the last step taken is the suspect.

**1.1 The app and its deploy files** — Claude ✅
- `HealthController` (`port.incoming.rest`): `GET /ping` → `ok`, as in publicbackend, with a `@WebMvcTest`.
- `server.port=8082` in `application.properties`, so every profile binds it.
- `application-production.properties`: banner off, file logging to `${FOLDER_BASE}/logs/pbs.log`,
  `server.forward-headers-strategy=native`, and **`server.address=127.0.0.1`** — a deliberate difference from
  publicbackend. Caddy is the only legitimate client. Bound to all interfaces, anything on the LAN could reach
  :8082 directly and skip Cloudflare; and since Tomcat trusts `X-Forwarded-*` headers from private-network
  addresses by default, it could also claim to be HTTPS. Binding to localhost removes both.
- `logback-spring.xml`: console always, file as well under `production` (publicbackend's, minus the dashboard).
  Boot's own appenders and default pattern, untouched — the log-retention stack parses that exact line (see
  Infrastructure).
- `justfile`, `build.just.template` with the `maven-antrun-plugin` execution, `change-version.sh`,
  `deployment/start-pbs.sh`, `deployment/pbs.env.example` with all five variables (the ones not read yet are
  marked with the phase that starts using them, so the env file on the Mini is written once). The antrun copy
  gets `failonerror="true"` — publicbackend's `false` is a Render leftover its own `CLAUDE.md` suggests fixing.
- `.gitignore`: `release/`, `deployment/bin/`, `deployment/logs/`, `deployment/*.env`,
  `pom.xml.versionsBackup`, `application-local.properties`.

Verify, on the dev machine:
1. `mvn verify` → `BUILD SUCCESS`.
2. `cp deployment/pbs.env.example deployment/pbs.env` — empty values are fine, nothing reads them in phase 1.
3. `just build`, then `just start` → the log shows `"production"` as the active profile and Tomcat on port 8082.
4. In a second terminal: `curl -s localhost:8082/ping` → `ok`, and `just logs` shows the same lines as the
   console.
5. `curl -s http://<dev machine's LAN IP>:8082/ping` → connection refused (the localhost binding).
6. `git status` lists neither `deployment/pbs.env` nor `deployment/bin/`;
   `git check-ignore -v deployment/pbs.env` names the `.gitignore` rule. Stop pbs with Ctrl-C.

**1.2 Publish the repository** — owner, dev machine ✅
- Create `jtarnvik/priceengine-bill-splitter` on GitHub as **public**, empty (no README or licence, so the
  first push needs no merge), if it does not exist yet. Then `git push -u origin main`.

Verify:
1. `git ls-files` contains nothing secret — no `.env`, no `application-local.properties`.
2. `git ls-remote origin` lists `refs/heads/main`.
3. On the Mini, `git ls-remote git@github.com:jtarnvik/priceengine-bill-splitter.git` works — proves the Mini's
   SSH key reaches the repo before the clone in 1.6 depends on it.

**1.3 The first version** — owner, dev machine ✅
- `./change-version.sh 0.1.0`, then run the push command it puts on the clipboard (commit **and** tag).

Verify:
1. `git ls-remote --tags origin` lists `v0.1.0`.
2. `git show v0.1.0:build.just | grep jar_version` → `0.1.0` — the tag carries its own jar name.

**1.4 DNS** — owner, Cloudflare dashboard ✅
- tarnvik.com → DNS → Records → Add record: type `CNAME`, name `pbs`, target `api2.tarnvik.com`, proxy status
  **Proxied** (orange cloud), TTL Auto. Never an A record — see Infrastructure.

Verify, from the dev machine:
1. `dig +short pbs.tarnvik.com` → Cloudflare addresses, like `dig +short api2.tarnvik.com` gives. If it answers
   `api2.tarnvik.com.` the record is grey-clouded (DNS only); if it answers nothing, ask Cloudflare's own
   nameserver directly: `dig +short pbs.tarnvik.com @aron.ns.cloudflare.com`. *Why:* a lookup made before the
   record existed is cached as "no such name" for up to 30 minutes (the 1800 s negative TTL in the zone's SOA),
   so the local resolver can lag. `curl --resolve pbs.tarnvik.com:443:<address>` gets past it.
2. `curl -s -w ' status=%{http_code} bytes=%{size_download}\n' https://pbs.tarnvik.com/ping` →
   `status=200 bytes=0`. **An empty 200, not an error**, which is what Caddy answers for a host name it has no
   site for: Cloudflare reached Caddy on the Mini, and the TLS handshake worked. (The plan originally predicted
   a 525 here; the run on 2026-10-25 showed the empty 200.) Look at the body, not the status — an empty 200
   would fool a status-only check. Had it said `ok`, the request would have reached publicbackend, meaning the
   host name was lost somewhere on the way.

**1.5 Certificate check and Caddy** — owner, on the Mini, following the manual's "Adding Another Project" ✅
- Check the origin certificate covers the new name: the `openssl ... -ext subjectAltName` command in the manual
  must list `*.tarnvik.com`. If it does not, stop here — that needs a new certificate, and a discussion.
- Copy the Caddyfile to a `.bak` next to it, then add the pbs block (already written out in the manual under
  "Caddy Reverse Proxy").
- `caddy validate --config <the Caddyfile>` **before** restarting. Caddy also serves api2, so a typo would take
  the commuter app down with it. A failure about reading the key file is permissions, not the edit.
- `brew services restart caddy`.

Verify:
1. `brew services info caddy` → running.
2. `curl -s https://api2.tarnvik.com/ping` → `ok` — the commuter app survived the restart.
3. `curl -sI https://pbs.tarnvik.com/ping` → **502**. The step from the empty 200 to 502 is the proof: Caddy now
   has a site for pbs.tarnvik.com and forwards to :8082, and the 502 is only that nothing listens there yet.

Rollback if anything misbehaves: restore the `.bak` and restart Caddy.

**1.6 First deploy** — owner, on the Mini ✅
- Next to publicbackend's production clone: `git clone git@github.com:jtarnvik/priceengine-bill-splitter.git`,
  `cd` into it. Keep that location and the repository's name for the folder: the manual's log-retention services
  table already expects the log there, and Docker can only see folders it shares.
- `java -version` and `mvn -v` → both 25. `.java-version` should make jenv pick it, as for publicbackend.
- `cp deployment/pbs.env.example deployment/pbs.env`, `chmod 600 deployment/pbs.env`. Empty values are fine
  for now; 1.9 fills in the Google ones.
- `just doit` — leaves the clone on `v0.1.0` in detached HEAD, which is expected, and pbs running in the
  foreground. Give it its own terminal tab next to publicbackend's.

Verify, on the Mini:
1. The startup log shows `Starting PbsApplication v0.1.0 using Java 25`, profile `"production"`, port 8082.
2. `curl -s localhost:8082/ping` → `ok`.
3. `ls -l deployment/pbs.env` → `-rw-------`.

**1.6a Join log retention** — owner, on the Mini, right after 1.6 ✅ (with the stack's lag deferred)
Needs 1.6 because pbs must have written its log folder first; if Docker is pointed at a folder that does not
exist, it creates an empty one in its place.
- Confirm the real log path: `ls <clone>/deployment/logs/pbs.log`. If it differs from the path in the manual's
  log-retention services table, correct the table first, then use the real path below.
- Follow the manual's "Log retention → Adding a service": the volume line in `docker-compose.yml`, the block in
  `alloy/config.alloy`, then `docker compose up -d`. Service label `pbs`, service id `pbs`, and the existing
  parser `sl_dashboard` (same format as publicbackend) — no new parser block.
- Do it soon after 1.6. Alloy imports the current file on first read, but lines more than about three hours old
  only show in Grafana once Loki flushes them, which can take another half hour.

Verify, on the Mini (Grafana and the Alloy UI addresses are in the manual):
1. `docker compose logs alloy | grep "start tailing"` lists `pbs.log`.
2. The Alloy UI shows `loki.source.file.pbs` as healthy.
3. Grafana → Explore → Loki, code editor: `{service="pbs"}` shows `Starting PbsApplication v0.1.0` with
   `level="INFO"`.
4. Live: with that query on *Live*, Ctrl-C pbs and `just start` again. The shutdown and startup lines appear
   within seconds. Then `curl -s localhost:8082/ping` once, and the three `Initializing … DispatcherServlet`
   lines appear. *Only the first request after a start logs anything;* a second curl adds nothing, and neither
   would a curl without the restart, since 1.6 already made the first request.

**1.6a outcome, 2026-10-25.** Verify 1–3 passed, including thread names with dots. Verify 4 passed only
eventually: every line reaches Grafana, but the startup lines took about three hours. The cause is in the
log-retention stack, not in pbs, and affects every service on it — see "After the project" at the end of this
file. pbs's side (the file, its format, the Alloy block) is done; the stack's lag is deferred.

**1.7 End to end** — owner ✅
Verify:
1. From the dev machine: `curl -si https://pbs.tarnvik.com/ping` → `HTTP/2 200`, `server: cloudflare`, body `ok`.
2. From the phone with **Wi-Fi off**: `https://pbs.tarnvik.com/ping` shows `ok`. This is the off-network test;
   the dev machine shares the Mini's network.
3. From the dev machine: `curl -s http://<the Mini's LAN IP>:8082/ping` → connection refused.

**1.8 Monitoring** — owner, UptimeRobot ✅
- New HTTP(s) monitor on `https://pbs.tarnvik.com/ping`, with the same interval and alert contacts as the api2
  monitor.
- *Known blind spot:* it checks the status code only. A Caddy that has lost its pbs block answers an empty 200
  (see 1.4), which this monitor counts as Up. A keyword monitor on `ok` would catch it, but needs a paid plan.

Verify:
1. The monitor turns Up.
2. Once, deliberately: stop pbs (Ctrl-C), wait for the Down alert to actually arrive, then `just start` and wait
   for Up. UptimeRobot is the only thing that will report pbs down, so the alert path is worth seeing work.

**1.9 Google OAuth client** — owner, Google Cloud Console (used from phase 3) ✅
- In the commuter app's project: Google Auth Platform → Clients → Create client (in the older UI: APIs &
  Services → Credentials → Create credentials → OAuth client ID). Type *Web application*, name `pbs`, no
  JavaScript origins, and exactly the two redirect URIs under Authentication.
- Copy the client secret immediately; newer consoles show it only once. Put the ID and secret in
  `deployment/pbs.env` on the Mini, along with `PBS_ALLOWED_EMAIL`. Keep them where the dev machine can get them
  too — phase 3 needs them in the gitignored `application-local.properties`.
- Under Audience, check the app is in test mode with the owner as a test user.

Verify:
1. The client list shows two clients, commuter and pbs, and the commuter one's redirect URIs are unchanged.
2. pbs's client lists exactly the two URIs, character for character. A typo here only surfaces in phase 3, as
   Google's `redirect_uri_mismatch`.

**1.10 The second version** — owner, with Claude preparing whatever is on `main`
- On the dev machine: `./change-version.sh 0.1.1` and push. Any committed change is enough; the point is the flow.
- On the Mini: Ctrl-C the running pbs, then `just doit`.

Verify:
1. The startup log shows `v0.1.1`, and `deployment/bin/` holds `Version-0.1.1.txt`.
2. `curl -s https://pbs.tarnvik.com/ping` from off the Mini → `ok`.
3. In Grafana, `{service="pbs"} |= "Starting PbsApplication"` over the last day shows both the `v0.1.0` and the
   `v0.1.1` startup lines — the logs outlive a redeploy. Until the stack's lag is fixed (see "After the
   project"), the newest lines can take hours to arrive: check the next day if `v0.1.1` is missing.

**1.11 Wrap-up** — Claude
- Reduce this plan to what exists and why, tick the manual's "pbs Setup Checklist" (its SSE item stays open for
  phase 2, the Anthropic key for phase 4), including "Log retention: add `pbs` as a service", remove
  "(planned)" from pbs's row in the manual's log-retention services table, and set Status to phase 2.
- In the manual's Caddyfile section, replace the planned pbs block with the real one:
  `reverse_proxy 127.0.0.1:8082`, not `localhost:8082`. pbs binds IPv4 loopback only (1.1), and `localhost` can
  also resolve to `::1`.

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

**Found in 1.6a, 2026-10-25.** Lines reach Grafana, but sometimes tens of minutes or hours late — pbs's
startup lines took about three hours, and publicbackend's lines were more than 20 minutes behind on the same
day. Nothing is lost. The lines hit hardest are the last ones before a service goes quiet, which are the ones
wanted after a crash.

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
