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
| Look and feel: navbar, menu, card, page wrapper, Tailwind setup | `../sl-dashboard/src/components/navbar/` (`index.tsx`, `nav-menu/`), `components/common/card/`, `components/common/view/`, `views/layout.tsx`, `src/index.css`, `postcss.config.js` | 2 |
| SSE backend | `../cl-media/overlord/src/main/java/com/tarnvik/overlord/port/outgoing/SseNotificationProvider.java` and `port/incoming/rest/SseController.java` | 2 |
| SSE frontend | `../cl-media/overlord/frontend/src/communication/live-updates.tsx` | 2 |
| SSE notes | `../cl-media/sse-enterprise-gotchas.md` | 2 |
| RTK Query on axios | `../cl-media/overlord/frontend/src/communication/axios-base-query.ts` and `api/base-api.ts` | 2 |
| OpenAPI codegen | `../cl-media/overlord/frontend/openapi-config.cjs`, springdoc in overlord's `pom.xml`, the springdoc/codegen notes in `../cl-media/CLAUDE.md` | 3 |
| OAuth2 security config | `../publicbackend/src/main/java/com/tarnvik/publicbackend/config/SecurityConfig.java` | 3 |
| Login state in React | `../sl-dashboard/src/hook/use-user.ts` and the `user` handling in `App.tsx` | 3 |
| Anthropic Java SDK usage | `../publicbackend/src/main/java/com/tarnvik/publicbackend/commuter/port/outgoing/rest/claude/ClaudeProvider.java` | 4 |
| Terminal dashboard | `../publicbackend/src/main/java/com/tarnvik/publicbackend/commuter/port/outgoing/terminal/` and `port/incoming/scheduled/DashboardRefreshJob.java`; its `CLAUDE.md` sections "Terminal dashboard" and "Logging"; overlord's dashboard, which it is modelled on | 8 |

**Do not `@`-import publicbackend's `CLAUDE.md`** the way sl-dashboard does. Most of it is GTFS and commuter
history that has nothing to do with this project and would only crowd the context. Copy what applies into this
file instead.

What *not* to copy from publicbackend: Spring Session JDBC, `SameSite=None`, the CORS configuration and its
two frontend-origin properties, the pending-user flow in the success handler, Liquibase. Each exists there
because of a separate frontend origin or a database, and pbs has neither. The terminal dashboard *is* copied,
but last (phase 8).

## Status

**Phases 1 and 2 done (2026-10-25); phase 3's steps 3.1–3.3 verified on the dev machine, 3.4 (release
`v0.3.0` and the production test) under way.** pbs `v0.2.0` runs on the Mini at `https://pbs.tarnvik.com`,
behind Cloudflare and Caddy, monitored by UptimeRobot, its log collected by the log-retention stack. It serves the
React shell from the jar — header, menu, the two placeholder pages — and the live stream with its dummy viewer
count, proven over Cloudflare and on a phone. The design below was worked out in one discussion (October 2026) before the repository existed.
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
- **Caddy flushes `text/event-stream` immediately** — no proxy configuration is needed for SSE — **and so does
  Cloudflare**, confirmed in phase 2 (2026-10-25): the first event arrived at once and the pings 20 s apart, one
  by one, and a stream quiet but for pings held for over five minutes. `index.html` comes back `no-cache`
  (Cloudflare `DYNAMIC`), the assets `max-age=31536000, immutable`.
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
- **There is no login page.** The header menu's "Logga in" item links to `/oauth2/authorization/google` when
  `/api/auth/me` answers 401. With a single provider, Spring redirects straight to Google. A login redirects to
  `/#/upload` (`defaultSuccessUrl(…, true)`), a refused one to `/#/denied` (`failureUrl`), which says only the
  owner can log in — otherwise Spring would show its own generated, English `/login` page.
- **`/api/auth/me` answers the owner's email, or 401.** It is not permitted for anonymous requests, as the API
  table's "Optional" first suggested: the 401 rule then answers for it, and the controller never sees a missing
  principal.
- **Logout is `POST /logout` answering 200** (`HttpStatusReturningLogoutSuccessHandler`), not a redirect: the app
  calls it with axios, and the mutation's `auth` tag refetches `/api/auth/me`, which flips the menu.
- **Sessions keep Tomcat's 30-minute idle timeout**, by decision. The app is used for a few minutes once the bill
  has come: the owner pays, uploads the receipt and ticks the boxes in one go, so there are no long pauses for the
  timeout to catch.

### Security configuration — the two that bite

1. **Permit the static files explicitly** — `/`, `/index.html`, `/assets/**`, `/icon.svg` (the favicon, from
   `frontend/public/`), and `/favicon.ico`, which some browsers ask for regardless and should get a 404. With
   `.anyRequest().authenticated()` and `oauth2Login`, a friend opening the public page would be bounced to Google
   just to load the JavaScript bundle.
2. **Answer 401 for `/api/**`, never a redirect.** Reuse publicbackend's `defaultAuthenticationEntryPointFor`
   with a `PathPatternRequestMatcher` on `/api/**`. A redirect to Google on a `fetch` fails in a way that looks
   like a CORS error and explains nothing.

Two more found while building it:
- **Permit `/error`.** A 404 or 401 is rendered by forwarding to Boot's error page, and Spring Security authorizes
  that forward too. Unpermitted, an anonymous `/favicon.ico` got a redirect to Google instead of its 404.
- **oauth2Login redirects only browser navigations** (`Accept: text/html`); anything else outside `/api/**` gets
  401 as well. A MockMvc test of the redirect must send that header.

springdoc's paths (`/v3/api-docs/**`, `/swagger-ui/**`) are permitted too, so that codegen can read the spec
without a login; outside `local` they do not exist and answer 404.

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
- **`npm run lint` runs at `verify`**, beside checkstyle, so ESLint gates the build the same way. (To prove it, an
  `if` without braces — `curly` — is the test; an unused variable is not, since `tsc` in `npm run build` rejects
  it first.)
- **`.npmrc` has `legacy-peer-deps=true`**, as in sl-dashboard: `eslint-plugin-react` 7.37 declares ESLint up to
  9 only, but works with 10. `fsevents` has its build script denied under `allowScripts` in `package.json`; npm
  11.17 warns about unreviewed install scripts and will later block them.
- **`ajv` 8 is a direct devDependency for that reason.** `@rtk-query/codegen-openapi` needs `ajv-draft-04`, which
  takes ajv 8 as a *peer*. Peers are not installed under `legacy-peer-deps`, so `ajv-draft-04` was hoisted next to
  ESLint's ajv 6 and `npm run generate-api` failed with `Cannot find module 'ajv/dist/core'`. Declared, ajv 8 is
  hoisted and ESLint's 6 nested under it.
- **Cache headers for the static files** (`StaticResourceConfig`): `/assets/**` one year, `immutable`, since the
  file names carry a content hash; `index.html` `no-cache`. Without a header a browser may guess a lifetime for
  `index.html` and keep showing the old release after a deploy.

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
`/#/` (public) and `/#/upload` (owner), plus `/#/denied` for a refused login.

## Development

Backend on :8082 (`-Dspring.profiles.active=local`), Vite on :5173. **Vite proxies `/api`, `/oauth2`,
`/login/oauth2` and `/logout` to :8082**, leaving the `Host` header alone (`changeOrigin: false`, explicit).

*Why proxy the auth paths too:* Spring builds the OAuth2 redirect URI from the request's host, so with the
`Host: localhost:5173` header preserved, Google sends the browser back through Vite. Dev is then single-origin
exactly like production: the success handler can redirect to a relative `/#/upload` in both, the session cookie
belongs to :5173, and no `frontend-url` property is needed anywhere. Checked with curl through the running Vite
proxy: `/oauth2/authorization/google` redirects with `redirect_uri=http://localhost:5173/login/oauth2/code/google`.

**Secrets in development** go in `local-secrets.properties` in the project root (gitignored), copied from the
committed `local-secrets.properties.example`, with the same names as `pbs.env`. The tracked
`application-local.properties` imports it (`spring.config.import=optional:file:./…`, relative to the working
directory, which is the project root from IntelliJ and Maven) and switches springdoc on. *Why not a gitignored
`application-local.properties`*, as first planned: springdoc being on in `local` is configuration, not a secret,
and would then live in a file git does not know.

**Regenerating the API client:** start the backend in `local`, then `npm run generate-api` in `frontend/`, and
commit `src/communication/api/generated-api.ts`. Tests use the `test` profile
(`src/test/resources/application-test.properties`), which holds dummy Google values and `owner@example.com`.

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

**Two named events, one per kind of change** (`event: receipt` and `event: split`; names and payloads settled in
phase 4). Each still carries the whole of its part, so the rule above holds per event.
- **`receipt`** — a new receipt has been parsed: the restaurant, the items, the receipt's total. Sent on upload.
  The public page can greet it with an animation (decided later).
- **`split`** — who had what changed: the assignments and the totals that follow from them. Sent on every tick of
  a checkbox, and on corrections (phase 6). It carries the `receiptId`, so a phone that somehow holds an older
  receipt ignores it rather than applying ticks to the wrong rows.
- **The animation keys on a *new `receiptId`*, not on the `receipt` event arriving.** State-on-connect sends the
  `receipt` event again on every reconnect, and a phone that wakes from its lock screen must not replay the
  greeting for a receipt it has already shown.
- *Why two events and not one:* the receipt is the large part and changes once a dinner, the split is small and
  changes constantly, and the page reacts differently to each. Splitting by kind keeps the frequent event small
  and makes "a new receipt arrived" something the page is told rather than has to work out.

- **Send the current state as the first events on connect** — `receipt`, then `split`, when there is a
  receipt. A reconnect is then indistinguishable from a first connect, nothing on the phone ever fetches, and
  the response starts at once rather than idling towards Cloudflare's 100 s limit.
- **Keep the heartbeat** (`: ping` every 20 s), for Cloudflare's idle timeout.
- **Phones lock their screens mid-dinner.** iOS suspends the page and the connection dies; overlord's
  `live-updates.tsx` already notes that iOS can give up for good (`readyState` stuck at `CLOSED`). On
  `visibilitychange` back to visible, reopen the `EventSource` if it is closed. The state-on-connect rule makes
  that the entire recovery.
- **Retry after a failed connection too, not only on `visibilitychange`.** `EventSource` reconnects by itself
  after a dropped stream, but gives up for good (`CLOSED`) when a reconnect gets a non-200 answer — which is
  exactly Cloudflare's 502 while pbs restarts during a deploy. So on `CLOSED` it is reopened after a delay growing
  from 2 s to 30 s; otherwise every open page would stop updating after each deploy until its phone was locked
  and unlocked.
- **All streams are closed on `ContextClosedEvent`.** It comes before the web server's graceful shutdown, which
  otherwise waits its full 30 s for these never-ending responses — on every deploy with a phone on the page — and
  logs `Graceful shutdown aborted with one or more requests still active` (tried, 2026-10-25).
- **Events are named** (`event: viewers` for phase 2's dummy), never unnamed: `onmessage` sees only unnamed
  events, a named one needs `addEventListener`, so the dummy already uses the mechanism the real ones will.
- The browser's 6-connections-per-site limit does not apply: Cloudflare serves the browser over HTTP/2.
- The stream endpoint is public and read-only: `GET /api/public/events`.

## Frontend data layer — RTK Query on axios

**RTK Query for all server data, axios underneath, a Redux store that holds nothing else.** Overkill for a handful
of endpoints, chosen on purpose: the owner is learning RTK Query and continues here. Overlord already has the same
stack, so its files are the reference (see Reference implementations).

- **The SSE stream is an RTK Query streaming endpoint.** One query whose `onCacheEntryAdded` opens the
  `EventSource` and writes each event into the cache with `updateCachedData`; both pages just call its hook.
  This is RTK Query's documented streaming pattern, and a different technique from overlord's (signal, then
  refetch) — it fits because every event carries the whole of its part. One cached value holds both parts, and
  each named event has its own listener writing its own part. The `visibilitychange` reopen lives in the
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
- **The stream's payload type is invisible to springdoc** (an `SseEmitter` has no typed body), so it is
  hand-written in `types/live.ts`, beside its Java record. The stream's controller is tagged `events`, and
  `openapi-config.cjs` excludes that tag.
- **Logout is hand-written** in `auth-api.ts`: `/logout` is Spring Security's filter, so springdoc never sees it.
  It is injected into the *generated* api, so the `auth` tag it invalidates is a known tag type.
- **The login state is `useLoginState()`** (`hook/use-login-state.ts`): `loading`, `loggedOut` (401),
  `loggedIn`, `offline` — sl-dashboard's four, read from the `getMe` cache entry instead of a context. It checks
  the error before the data, because a failed refetch keeps the previous data: right after logout `data` still
  holds the owner while `error` already says 401.

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

### Look and feel — sl-dashboard's, in green

The same frame as sl-dashboard, so the owner's apps feel like one family; read its files before writing these
(see Reference implementations). Tailwind 4 through PostCSS and Roboto, as there. **The UI is in Swedish**, as
sl-dashboard's is ("Logga in", "Inget kvitto ännu"); only the app's name is English.

- **A fixed header bar**: on the left an icon (`FaRegCreditCard` from `react-icons`, leading to `/#/`) and the
  name "PriceEngine Bill Splitter" — it fits at 375 px (2026-10-25); should it ever not, it is cut with an
  ellipsis, keeping the menu button on the bar. On the right a hamburger button opening a white dropdown menu,
  closed by a click outside it: "Logga in" when logged out; "Ladda upp kvitto" and "Logga ut" when logged in —
  the first so that the owner can get back to `/#/upload` from the public page.
- **Pages**: white background, content in sl-dashboard's very slightly grey card (`bg-[#F1F2F3]`, gray-200
  border, `rounded-lg`, `shadow-sm`).
- **The header is green where sl-dashboard's is blue** (`#2870f0`). *Not* the same hue-swapped HSL values: at
  sl-dashboard's saturation and lightness, green comes out neon (`#28f072`), and white text on it is unreadable,
  because the eye sees green as far brighter than blue. The green matches the blue's *brightness* instead, so
  white text reads equally well on both: about `#1a8a47`, with a lighter hover shade and a darker button shade
  in the same relation as sl-dashboard's `#578ff3` and `#184fc2`. They are Tailwind theme colours in
  `index.css`, so changing the green is one line.
- *Possibly later:* menu items to filter the public page — "Alla" or "Bara jag" and then a name. Decided once
  phase 5 shows what that page feels like at the table.

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/ping` | Public | Health check for UptimeRobot |
| GET | `/api/auth/me` | Owner | The owner's email, or 401 |
| GET | `/api/protected/ping` | Owner | Placeholder until phase 4, so the 401 rule guards something real |
| GET | `/api/public/events` | Public | SSE stream; current state on connect, then on every change |
| POST | `/api/protected/receipt` | Owner | Multipart image → parse → replaces the state, broadcasts it |
| PUT | `/api/protected/items/{index}/assignees` | Owner | The full set of people for one row; 409 on a stale `receiptId` |
| POST | `/logout` | Owner | Ends the session; 200, not a redirect |

Corrections (edit a row, add a row, remove a row) arrive with phase 6.

## Open questions

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

**Phase 2 - A two-page shell** ✅ (2026-10-25). The one-jar build and the frontend frame, with placeholder
content: `frontend-maven-plugin` building Vite into the jar, ESLint at `verify`, the header, menu and two
placeholder pages, the Redux store with the RTK Query base api, and the SSE stream end to end — emitter registry,
state-on-connect, heartbeat, the `visibilitychange` reopen and the retry after a failed connection — released as
`v0.2.0` and proven over Cloudflare and on a phone over mobile data, locked and unlocked. What it established is
now in Infrastructure, Build, Live updates, Frontend data layer and Look and feel; the worked step plan is in git
history.

**The stream carries a dummy, the number of connected viewers, until phase 5.** *Why a viewer count and not a
timer:* it exercises every mechanism the real stream needs, with no extra code. A timer ticking every few seconds
never lets the stream go quiet, so it would never prove that the heartbeat keeps Cloudflare's 100-second limit
away — the situation a real dinner is in most of the time. A viewer count is quiet until someone connects, shows
dead-connection pruning working (a closed tab disappears within two heartbeats, 40 s: the first write to a closed
connection usually succeeds, only the next one fails), and turns a phone's lock and unlock into a visible drop and
return. The public page's connection status line ("Ansluten", "Återansluter …") is temporary too.

**Phase 3 - Login on the upload page.** The security frame, still with nothing behind it worth protecting.
- Security config with the two gotchas above (static files permitted, 401 for `/api/**`), `SameSite=Lax`, the
  email check in `OidcUserService`, `/api/auth/me`, logout.
- The header menu shows "Logga in" when logged out, and "Logga ut" when logged in. The
  public page stays open to all.
- One placeholder protected endpoint, so the 401 rule has something real to guard before the paid endpoint
  exists.
- springdoc (in `local` only) and `@rtk-query/codegen-openapi`, with `/api/auth/me` as the first generated
  endpoint — the first real JSON endpoint, since phase 2's only one is the stream, which codegen excludes.
- Tests: anonymous access to `/api/protected/**` gets 401; a different email is refused.
- Done when the owner can log in and out on `https://pbs.tarnvik.com` from a phone, and a second Google account
  is refused — and the dev login through the Vite proxy works too.

#### Phase 3 — step by step

Planned and implemented in one go (2026-10-25), at the owner's request, from the design above; what the design
did not settle is recorded in Authentication, Development and Frontend data layer.

**3.1 Security, backend** — Claude ✅
- `spring-boot-starter-security` and `-oauth2-client`. `SecurityConfig` with the static-file permits, `/error`,
  401 for `/api/**`, `oauth2Login` (success to `/#/upload`, failure to `/#/denied`), logout answering 200, CSRF
  off. `OwnerOnlyOidcUserService` compares the verified email, case-insensitively, against `pbs.allowed-email`
  and refuses to start if it is blank.
- `GET /api/auth/me` (`AuthController`, `MeResponse`), `GET /api/protected/ping` (placeholder).
- `server.servlet.session.cookie.same-site=lax`; the Google registration from `GOOGLE_CLIENT_ID` and
  `GOOGLE_CLIENT_SECRET`.
- Tests: `SecurityConfigTest` (`@SpringBootTest`, the real chain; the owner via `oidcLogin()`, whose principal is
  an `OidcUser` as `AuthController` expects — `@WithMockUser`'s is not) and `OwnerOnlyOidcUserServiceTest`. The
  `@WebMvcTest`s exclude the five security auto-configurations.

Verify, on the dev machine:
1. `mvn verify` → `BUILD SUCCESS`, `Tests run: 20, Failures: 0`.
2. `java -jar target/pbs-*.jar --GOOGLE_CLIENT_ID=x --GOOGLE_CLIENT_SECRET=x --PBS_ALLOWED_EMAIL=x@example.com`,
   then `curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' -H 'Accept: text/html' localhost:8082<path>`:
   `/`, `/index.html`, `/icon.svg` and an `/assets/` file → 200; `/favicon.ico` → 404; `/api/auth/me`,
   `/api/protected/ping` and `/api/nothing` → 401 with no redirect; `/somewhere` → 302 to
   `/oauth2/authorization/google`; `/v3/api-docs` → 404.
3. `curl -sD - -o /dev/null localhost:8082/oauth2/authorization/google | grep -i set-cookie` → `SameSite=Lax`.
4. Without `PBS_ALLOWED_EMAIL` the jar refuses to start, naming the placeholder.

**3.2 springdoc and the generated client** — Claude ✅
- `springdoc-openapi-starter-webmvc-ui` 3.1.1 (overlord's; 3.2.0 was a day old), off in `application.properties`,
  on in the tracked `application-local.properties`, which also imports `local-secrets.properties`.
- `@rtk-query/codegen-openapi`, `openapi-config.cjs` copied from overlord (port 8082, `events` excluded by tag),
  `npm run generate-api`, `generated-api.ts` committed. `ajv` 8 as a devDependency (see Build).

Verify:
1. Backend in `local`: `curl -s localhost:8082/v3/api-docs` lists `/api/auth/me` (`getMe`, tag `auth`) and
   `MeResponse` with `email` under `required`; `http://localhost:8082/swagger-ui.html` opens.
2. `npm run generate-api` in `frontend/` → `Done`, and `git status` shows `generated-api.ts` unchanged.

**3.3 Login state in the frontend** — Claude ✅
- Vite proxies `/oauth2`, `/login/oauth2` and `/logout` as well as `/api`, with `changeOrigin: false`.
- `useLoginState()`, the logout mutation, the menu ("Logga in" / "Ladda upp kvitto" + "Logga ut"), the upload
  page asking a logged-out visitor to log in, and `/#/denied`.

Verify, with `local-secrets.properties` filled in, the backend in `local` and `npm run dev`:
1. `http://localhost:5173/`: the menu shows "Logga in". It leads to Google, which shows
   `redirect_uri=http://localhost:5173/login/oauth2/code/google` in its URL; after login the browser is on
   `localhost:5173/#/upload` and the card says "Kommer i fas 4".
2. The menu now shows "Ladda upp kvitto" and "Logga ut". "Logga ut" lands on `/#/` and the menu shows "Logga in";
   `/#/upload` then asks to log in.
3. The public page's viewer count still works while logged in and out.

**3.4 Release `v0.3.0` and the production test** — owner, with Claude preparing
- `./change-version.sh 0.3.0`, push commit and tag, Ctrl-C and `just doit` on the Mini. `pbs.env` already holds
  the Google values and `PBS_ALLOWED_EMAIL`.

Verify:
1. `/ping` answers `ok`; `https://pbs.tarnvik.com/` loads for a browser that is not logged in, with no detour via
   Google.
2. `curl -si https://pbs.tarnvik.com/api/protected/ping` → 401; `/v3/api-docs` → 404.
3. **On the phone:** "Logga in" → Google → `/#/upload`. The session cookie (DevTools on the dev machine, if not on
   the phone) is `HttpOnly`, `Secure`, `SameSite=Lax`. "Logga ut" → logged out.
4. **A second Google account is refused:** it lands on `/#/denied`, and the menu still shows "Logga in".
5. A restart of pbs logs the owner out (sessions are in memory), as designed.

### Functionality

**Phase 4 - Parse a receipt.** Canvas resize in the browser, the multipart endpoint, the Claude call with the typed
schema, the sum check. Needs pbs's own Anthropic key. The upload page shows the parsed rows and any warning. No
splitting yet.

**Phase 5 - Split and broadcast.** Checkboxes, the assignment endpoint, the split calculation, the state record replacing
the dummy counter on the SSE stream, the public page showing per-person items and totals. The real dinner test.

**Phase 6 - Corrections.** Edit a row's name or price, add a row by hand (a tip, a misread item), remove a row.

**Phase 7 - Phone polish.** Layout at 375 px, tap targets, empty states.

### Operations

**Phase 8 - Terminal dashboard.** A status board on the terminal pbs runs in on the Mini, under `production`, like
publicbackend's and overlord's — so the three foreground tabs on the Mini look and work alike. Copy publicbackend's
mechanics (jline in `port.outgoing.terminal`, one render thread owning the screen, repaints driven by Spring
events plus one hourly clock, the console appender detached by name once the alternate screen is up, startup
output left visible). What it shows is discussed when the phase is planned; candidates: the version, connected
viewers, the current receipt (restaurant, total, how much is still unassigned), and the last parse — when, how
long it took, and whether it failed.
- *Why last:* it shows the state of features that do not exist yet, and nothing else depends on it.
- *Watch:* the dashboard needs a real terminal (a TTY), so `start-pbs.sh` must keep not piping through `tee`, and
  `logback-spring.xml` must declare the console appender by a name the dashboard can detach — read
  publicbackend's "Logging" section before changing either.

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
