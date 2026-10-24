# CLAUDE.md — pbs

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`{{BASE_PACKAGE}}` = `com.tarnvik.pbs` (provisional — confirm when the project is generated)

Prefer 125 character wide lines in this file where the format allows it.

## Session start

Before generating Java code, read `src/main/resources/checkstyle.xml`; before TypeScript or React code, read the
frontend's `eslint.config.js`. Neither exists yet — goal 1 copies the checkstyle file from publicbackend and goal
2 the ESLint config from sl-dashboard, so all three projects share one style.

## Reference implementations

Much of this project is a copy of something already working in a sibling repository. The repos sit side by side
under one parent directory, so the paths below are relative to this repo's root. Read the original before
writing the copy, and adapt rather than reinvent.

| What | Where | Used in goal |
|---|---|---|
| `just` deploy flow | `../publicbackend/justfile`, `build.just.template`, `change-version.sh`, the `maven-antrun-plugin` execution in its `pom.xml` | 1 |
| Start script and env template | `../publicbackend/deployment/start-publicbackend.sh`, `publicbackend.env.example` | 1 |
| `production` profile, file logging | `../publicbackend/src/main/resources/application-production.properties`, `logback-spring.xml` | 1 |
| Checkstyle rules | `../publicbackend/src/main/resources/checkstyle.xml` | 1 |
| ESLint config | `../sl-dashboard/eslint.config.js` | 2 |
| SSE backend | `../cl-media/overlord/src/main/java/com/tarnvik/overlord/port/outgoing/SseNotificationProvider.java` and `port/incoming/rest/SseController.java` | 2 |
| SSE frontend | `../cl-media/overlord/frontend/src/communication/live-updates.tsx` | 2 |
| SSE notes | `../cl-media/sse-enterprise-gotchas.md` | 2 |
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

**Planning only — nothing is built yet.** Everything below was designed in one discussion (October 2026) before
the repository existed. Treat the design as decided unless a step proves it wrong, and the steps under "Goals, in
order" as the plan. Remove planning text from this file as it is carried out, keeping the "why" notes.

**Base structure before functionality.** The first three goals build and prove the frame — infrastructure,
a two-page shell, login — and contain no receipt, split or Claude code at all. Nothing functional starts until
all three work end to end in production.

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
  normally streams it unbuffered too, but that is the one link not confirmed in advance; goal 2 confirms
  it before anything depends on it.

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
trusting the model. The owner then corrects the row (goal 6) rather than taking a new photo.

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
  the same checkboxes (goal 6).

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

Corrections (edit a row, add a row, remove a row) arrive with goal 6.

## Open questions

- UI language — Swedish like sl-dashboard is assumed ("Logga in", "Inget kvitto ännu").
- The four first names, for `application.properties`.

---

## Goals, in order

Each goal is small and ends in something that can be seen working **in production**, not only locally.

### Base structure — no functionality

The frame is built and proven first, because infrastructure and auth are the parts most likely to surprise, and
a surprise there is cheapest when no feature depends on the frame yet.

**1 - Infrastructure and communication.** Everything between the internet and a running jar, with the app itself
reduced to `/ping`.
- Spring Boot 4.1 / Java 25 project answering `/ping` on :8082, profile `production`, with
  `forward-headers-strategy=native`.
- The `just` files, `change-version.sh`, the start script and `deployment/pbs.env.example` — the full tag-driven
  deploy flow, run once for real.
- The infrastructure steps in the manual's "pbs Setup Checklist": Cloudflare CNAME, certificate check, Caddy
  block, UptimeRobot. Create the Google OAuth client now as well, so goal 3 only consumes it.
- Done when `https://pbs.tarnvik.com/ping` answers from off the machine, and a second tagged version has been
  deployed the same way — proving the flow repeats, not only that it ran once.

**2 - A two-page shell.** The one-jar build and the frontend frame, with placeholder content.
- `frontend-maven-plugin` with pinned Node/npm, Vite output into `target/classes/static`, `mvn verify` producing
  the single jar. The Vite dev loop with its proxy.
- React with `HashRouter` and two placeholder pages, `/#/` and `/#/upload`, and a way to move between them.
- **The public page's SSE wiring, carrying a dummy counter** instead of a split: the emitter registry,
  state-on-connect, the heartbeat and the `visibilitychange` reconnect. It is part of the frame, not a feature —
  the real state later replaces only the payload. It also settles the one link in the chain not confirmed in
  advance: open the page on a phone over mobile data and watch events arrive one at a time rather than in bursts,
  then lock and unlock the phone to exercise the reconnect.
- Done when both pages load from `https://pbs.tarnvik.com` and the counter ticks on a phone.

**3 - Login on the upload page.** The security frame, still with nothing behind it worth protecting.
- Security config with the two gotchas above (static files permitted, 401 for `/api/**`), `SameSite=Lax`, the
  email check in `OidcUserService`, `/api/auth/me`, logout.
- The upload page shows a "Logga in" button when logged out and the owner's name plus "Logga ut" when logged in.
  The public page stays open to all.
- One placeholder protected endpoint, so the 401 rule has something real to guard before the paid endpoint
  exists.
- Tests: anonymous access to `/api/protected/**` gets 401; a different email is refused.
- Done when the owner can log in and out on `https://pbs.tarnvik.com` from a phone, and a second Google account
  is refused — and the dev login through the Vite proxy works too.

### Functionality

**4 - Parse a receipt.** Canvas resize in the browser, the multipart endpoint, the Claude call with the typed
schema, the sum check. Needs pbs's own Anthropic key. The upload page shows the parsed rows and any warning. No
splitting yet.

**5 - Split and broadcast.** Checkboxes, the assignment endpoint, the split calculation, the state record replacing
the dummy counter on the SSE stream, the public page showing per-person items and totals. The real dinner test.

**6 - Corrections.** Edit a row's name or price, add a row by hand (a tip, a misread item), remove a row.

**7 - Phone polish.** Layout at 375 px, tap targets, empty states.
