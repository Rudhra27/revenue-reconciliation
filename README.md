# Reconciler

A web application that ingests an orders export and a payment-processor export, reconciles
them with a deterministic engine, and presents the result as a dashboard someone responsible
for a store's revenue could act on. An LLM layer explains individual discrepancies in plain
language; it never decides whether two records match.

---

## Quick start

**Requirements:** JDK 21 and Docker (Docker is used for the local database and for the tests).

### Run it

```bash
./gradlew bootTestRun
```

This starts the app against a throwaway PostgreSQL container — nothing else to install. Then:

1. Open <http://localhost:8080>, create an account.
2. Create a dataset, and click **Load sample data** — this loads the two bundled files
   (`src/main/resources/sample-data/`) in one step.
3. Click **Reconcile**, then **Open dashboard**.

To run against your own database instead, set the connection details (see `.env.example`) and:

```bash
./gradlew bootRun
```

Health check: <http://localhost:8080/actuator/health>

### LLM explanations (optional)

Set `OPENAI_API_KEY` to enable the **Explain** buttons. Without a key the app works exactly
the same — the buttons just report that the explanation service isn't configured. All other
config is in `.env.example`.

### Tests

```bash
./gradlew test
```

91 tests. The integration tests spin up PostgreSQL via Testcontainers, so Docker must be
running.

---

## Architecture

A single Spring Boot application — one deployable JAR. It serves server-rendered HTML,
exposes a few HTML-fragment endpoints driven by htmx, runs the reconciliation engine
in-process, and talks to PostgreSQL and (optionally) OpenAI. Nothing else is deployed.

```
Browser
  │  session cookie (HttpOnly)
  ▼
Spring Security filter chain
  ▼
Controllers ─► Services ─► JPA repositories ─► PostgreSQL
     │              │
     │ Thymeleaf    ├─► ReconciliationEngine   (pure, in-process, no I/O)
     │ + htmx       └─► LlmService ─► OpenAI    (backend only; key from an env var)
     ▼
HTML pages + HTML fragments
```

### Stack

| Concern | Choice | Why |
|---|---|---|
| Framework | Spring Boot 4.1, Java 21 | |
| Database | PostgreSQL | The core task is joins and grouping with per-user filtering — a relational workload. `jsonb` holds the untouched CSV rows and the LLM output. |
| Migrations | Liquibase | Schema is rebuilt from the changelog on every boot. |
| Auth | Spring Security, form login, **server-side session**, BCrypt | Simplest correct option for a server-rendered app: no token lifecycle to get wrong, CSRF stays on. |
| Frontend | Thymeleaf + htmx + Chart.js | Keeps it a genuine monolith with one build. htmx gives async fragment swaps — exactly what the "LLM call in flight / failed" states need. htmx and Chart.js are vendored under `static/js/`, so the page has no external dependencies. |
| LLM | OpenAI `gpt-4o-mini`, called from the backend only | See [LLM approach](#llm-approach). |

### How data is scoped to a user

`user_id` is denormalised onto every row. Every service method loads by `(id, userId)` and
returns **404** — not 403 — when the id belongs to someone else, so the response never
confirms that another user's resource exists. `TenancyTest` walks every data-bearing route
as a non-owner and checks all of them.

### Data model

```
app_user              id, email (citext, unique), password_hash, created_at

dataset               id, user_id, name,
                      status [CREATED | ORDERS_LOADED | PAYMENTS_LOADED | RECONCILED],
                      created_at

order_row             id, dataset_id, user_id, source_line_no, raw_json (jsonb),
                      order_id, order_date, customer_email, currency,
                      gross_amount, discount, net_amount, status,
                      data_quality_flags (text[]), is_duplicate_of

payment_row           id, dataset_id, user_id, source_line_no, raw_json (jsonb),
                      transaction_ref, processed_at,
                      order_reference, order_reference_raw, currency,
                      amount, fee, net_settled, type, status,
                      data_quality_flags (text[])

reconciliation_result id, dataset_id, user_id, created_at, engine_version, as_of,
                      total_orders, total_payments, matched_orders, discrepancy_count,
                      value_reconciled, value_in_dispute, money_at_risk

discrepancy           id, run_id, dataset_id, user_id,
                      type, subtype, severity, direction,
                      order_id, order_row_id, payment_row_ids (uuid[]),
                      currency, amount_impact, detail (jsonb), search_text

llm_explanation       id, dataset_id, discrepancy_id, user_id, scope, input_hash,
                      status [OK | INVALID | FAILED],
                      model, temperature, prompt_version,
                      summary, likely_cause, recommended_action, confidence,
                      raw_response, error, created_at
```

The reconciliation run is replaced wholesale each time you reconcile; the discrepancies and
any explanations cascade with it.

### Package layout

```
com.reconciler
  config/          Spring Security
  user/            accounts, sign-up / login, the authenticated principal
  dataset/         Dataset, OrderRow, PaymentRow, their repositories, the dataset pages
  ingest/          CSV parsing and normalisation, the upload flow, the sample-data loader
  reconciliation/  the pure engine, plus persistence of a run and its discrepancies
  dashboard/       the dashboard page and the drill-down query
  llm/             OpenAI client, prompt building, the explanation service and endpoints
  web/             the "/" redirect
```

---

## Reconciliation logic

### Principles

The engine is a pure module: `ReconciliationEngine.run(orders, payments, asOf)`. No Spring,
no database, no wall clock. The only outside input is `asOf`, used solely to set the urgency
of a pending settlement — it never changes any money figure. Inputs are sorted before
processing and all money is `BigDecimal` with `HALF_UP`, so the same input always produces
the same result. No LLM is involved in matching.

### Matching and normalisation

Order and payment records are matched on the order id / order reference, compared **without
regard to case or surrounding whitespace** (this alone repairs `  ord-1801  ` and `ord-1802`
in the payments file). The two files use different date formats, so each has its own parser.
Money is parsed to two decimal places. Byte-identical repeat rows in the orders file are
flagged on import and skipped by the engine, so a doubled line isn't counted as a second
order.

### Tolerance

Two amounts are treated as equal when their difference is at most **`max($0.05, 0.5% of the
order net)`**. A flat five-cent floor absorbs rounding and small FX drift on low-value
orders; the 0.5% term keeps the same leniency proportional on large orders without letting a
material error hide. Anything outside that band is a real discrepancy.

### Classification

Each order produces **exactly one** primary discrepancy (or none). The checks run in a fixed
order:

1. **Currency mismatch first.** If the money isn't even in the same currency, nothing else
   about the amount can be trusted.
2. **Then a branch on the order's own status**, because "no payment" or "partial payment"
   means completely different things for a completed vs. cancelled vs. refunded order:
   - *cancelled* → charged anyway? `CHARGE_ON_CANCELLED`. Otherwise it reconciles.
   - *refunded* → the charge still exceeds the refund? `INCOMPLETE_REFUND`. Otherwise it
     reconciles.
3. **Within a completed order**, in order: no charge at all → `MISSING_PAYMENT`; every
   charge failed → `FAILED_PAYMENT`; a charge that hasn't settled → `PENDING_SETTLEMENT`;
   two or more settled charges → `DUPLICATE_PAYMENT`; a settled refund that nets the order
   to zero → `UNRECORDED_REFUND`; charge vs. order total outside tolerance →
   `AMOUNT_MISMATCH` (`OVER` or `UNDER`).
4. Anything left over is a clean match.

A settled payment whose reference matches no order is an `ORDER_NOT_FOUND` (an orphan), on
the payment side.

> This ordering is a small refinement of the design I started with, which had a single flat
> priority list. Two cases forced the change: a pending charge makes `effectivePaid` zero,
> which looked like a large undercharge and fired `AMOUNT_MISMATCH` before the pending check;
> and a cancelled order that was correctly never charged fell through to `AMOUNT_MISMATCH`
> too. Branching on order status first fixes both.

### Discrepancy catalog

| Type | Money impact | Direction | Severity |
|---|---|---|---|
| `ORDER_NOT_FOUND` | payment amount | Needs investigation | High |
| `MISSING_PAYMENT` | order net | We are owed | High |
| `FAILED_PAYMENT` | order net | We are owed | High |
| `DUPLICATE_PAYMENT` | settled charges − order net | We may owe back | High |
| `CHARGE_ON_CANCELLED` | effective paid | We may owe back | High |
| `INCOMPLETE_REFUND` | charge − refund | We may owe back | Medium |
| `UNRECORDED_REFUND` | refund amount | Already lost | Medium |
| `CURRENCY_MISMATCH` | 0 (unquantified) | Unquantified | Medium |
| `AMOUNT_MISMATCH / OVER` | paid − net | We may owe back | Medium |
| `AMOUNT_MISMATCH / UNDER` | net − paid | We are owed | Medium |
| `PENDING_SETTLEMENT` | order net | Watching | Low → Medium → High with age |

### Money at risk

**Money at risk = "We are owed" + "We may owe back"** — uncollected revenue plus refund and
clawback liability. It's the actionable exposure: what the business loses or has to pay out
if nothing is done. Over- and under-charges are never netted against each other — they
concern different customers and different actions.

Reported *separately*, because they aren't a recoverable or payable exposure:

- **Needs investigation** — a settled payment with no order behind it.
- **Already lost** — a refund that already went out but the order system still counts as a sale.
- **Watching** — a pending settlement. It might still land, so you chase the processor rather
  than reserve against it. Its severity rises with age (recent → weeks → longer), but it
  never counts toward money at risk regardless of when you run the reconciliation.
- **Unquantified** — a currency mismatch, which can't be valued without an FX rate.

> The design I started with moved an aged pending charge into "money at risk" after seven
> days. I changed that: a pending charge is a chase-the-processor item, not something you put
> in a reserve, so it stays a "watch" item and only its urgency changes. The upshot is that
> money at risk is the same number whether you reconcile the data today or back when it was
> exported.

### Headline figures

| Figure | Meaning |
|---|---|
| Value reconciled | net value of orders that matched cleanly |
| Value in dispute | notional value of every record touched by a discrepancy (excluding watch items) — how *broad* the problem is |
| Money at risk | the actionable exposure — how *severe* it is |

---

## What we found in the data

185 order rows (184 once the duplicate is removed) and 187 payment rows. After
reconciliation:

| | |
|---|---|
| Matched | 168 of 184 orders |
| Discrepancies | 19 (16 order-side, 3 orphan payments) |
| Value reconciled | $39,963.28 |
| Value in dispute | $2,547.37 |
| **Money at risk** | **$1,349.43** |

### We are owed — $720.85

| Finding | Orders | Amount |
|---|---|---|
| Completed order, no payment at all | ORD-1201, ORD-1202, ORD-1203, ORD-1204 | $392.35 |
| Completed order, the charge failed | ORD-2001 | $310.00 |
| Charged less than the order | ORD-1402 | $18.50 |

### We may owe back — $628.58

| Finding | Orders | Amount |
|---|---|---|
| Order charged twice, both settled | ORD-1501, ORD-1502 | $248.58 |
| Cancelled order that was still charged | ORD-1701 | $175.00 |
| Marked refunded, only half the money returned | ORD-1702 | $120.00 |
| Charged more than the order | ORD-1401, ORD-1403 | $85.00 |

### Reported separately

| Finding | Orders | Amount |
|---|---|---|
| Settled payment with no matching order | ORD-1301, ORD-1302, ORD-1303 | $308.00 (investigate) |
| Charge and full refund, but the order still reads "completed" | ORD-1703 | $99.00 (already lost) |
| Charge still pending | ORD-2002 | $67.00 (watching) |
| Order and payment in different currencies (amounts numerically equal, currencies look swapped) | ORD-1601, ORD-1602 | unquantified |

### Repaired on import, not counted as discrepancies

- Payment references `  ord-1801  ` (spaces + lower case) and `ord-1802` (lower case) — normalised to match.
- `ORD-1004` appears twice, byte-identical — de-duplicated.
- The two files use different date formats — parsed separately.
- `ORD-2201` has no email and a blank discount; one payment has no timestamp — flagged, and the rows still reconcile.
- Rounding differences of a cent or two on ORD-1901 / ORD-1902 / ORD-1903 fall inside tolerance and match.

### What it means for the business

- About **$721** of fulfilled sales was never collected. Missing and failed charges are pure
  leakage — re-bill the customer or chase the processor.
- About **$629** is a liability: two double charges, a charge on a cancelled order, an
  unfinished refund, and two overcharges. Each is a refund waiting to happen, and a
  chargeback risk if the customer disputes instead of asking.
- Three settled payments (~**$308**) have no order behind them — either revenue that isn't
  being recognised, or charges booked against the wrong reference.
- One order is counted as a completed sale but was fully refunded — reported revenue is
  **overstated by $99**.
- Currency tagging between the two systems is unreliable and needs an FX-aware check before
  those two orders can be trusted.

The engine is proven against this data by `ReconciliationOnSampleDataTest`, which parses the
two bundled files, runs the engine, and asserts every figure and every discrepancy above.

---

## LLM approach

The LLM adds a layer of explanation on top of the deterministic results. It is called only
from `LlmService`, on the backend. It never influences whether two records match, and the
dashboard is fully usable whether or not any explanation call succeeds.

**What it's given.** A small hand-built JSON object: the discrepancy type, the computed
numbers (`settledCharges`, `settledRefunds`, `effectivePaid`, the difference), amounts and
currency. Only derived facts — no customer details.

**Model and parameters.** `gpt-4o-mini`. **Temperature 0.2** — low enough that the same
discrepancy yields a stable explanation on a repeat (results are cached, and a reviewer may
re-open the same row), but not 0, because the output is prose rather than arithmetic and a
little variation reads more naturally. `max_completion_tokens` is capped; the client has a
10-second connect / 20-second read timeout and retries once on a timeout or a 5xx.

**Structured output.** The request uses a strict `json_schema` response format for
`{ summary, likely_cause, recommended_action, confidence }`, so a well-formed reply is the
norm.

**Bad responses.** The reply is still parsed and validated. A non-JSON reply or one missing
a field is stored as `INVALID` with the raw text kept; a failed call is stored as `FAILED`.
Either way the UI shows the problem and offers a **Try again** button — it never breaks the
page.

**Caching.** Each explanation is keyed by `SHA-256(prompt version | model | temperature |
context JSON)`. A repeat request for an unchanged discrepancy returns the stored result with
no API call.

**Without a key.** `LlmService` checks its configuration first; with no key it returns a
`FAILED` "not configured" result and never calls out.

---

## Testing

91 tests. Highlights:

- `ReconciliationEngineTest` — one scenario per rule, plus a determinism check.
- `ReconciliationOnSampleDataTest` — the engine over the two bundled files, every figure pinned.
- `IngestServiceTest` / the parser tests — normalisation, per-row rejection, duplicate flagging, the orders-before-payments rule.
- `LlmServiceTest` — valid reply stored, non-JSON and missing-field become `INVALID`, a failed call becomes `FAILED`, the second request hits the cache.
- `WebSecurityTest` / `TenancyTest` — the anonymous redirect, and every data-bearing route rejected for a non-owner.

Integration tests use Testcontainers, so a real PostgreSQL is exercised — including that the
Liquibase changelog applies cleanly.

---

## Deployment

The app is a single container (`Dockerfile`, multi-stage: Gradle build → slim JRE 21). It
needs a PostgreSQL database and, optionally, `OPENAI_API_KEY`. Liquibase migrates the schema
on boot; the health check is at `/actuator/health`.

**Database URL.** Most hosts hand the database over as a single
`postgres://user:pass@host:port/db` string in `DATABASE_URL`. An
`EnvironmentPostProcessor` converts that to a JDBC URL and credentials on start, so Render,
Neon, Railway and Fly all work with no manual URL formatting. For local runs, set
`SPRING_DATASOURCE_*` instead (see `.env.example`).

**Render.** `render.yaml` is a blueprint that provisions a free Postgres and a Docker web
service. Deploy with *New → Blueprint*, then set `OPENAI_API_KEY` in the dashboard if you
want the Explain buttons.

Live URL and test credentials are in the submission notes.

---

## What I'd build next

- **Processor fees on duplicate charges.** The fee on the extra charge is usually
  non-refundable even after you refund the customer, so it's a permanent loss the current
  "money at risk" doesn't include.
- **Risk-weighting.** Multiply the liabilities by a chargeback probability and add the
  per-dispute penalty, for a probability-adjusted exposure figure.
- **An FX-aware currency check.** Pull a reference rate for the order date and validate the
  converted amount, instead of only flagging the mismatch.
- **Configurable tolerances** and the pending-age thresholds, surfaced in the UI rather than
  fixed in code.
- **Background processing** for large uploads, and running the LLM call asynchronously with
  a progress state rather than holding the request open.
- **An audit trail** — who ran which reconciliation and when, with a diff between runs.
- A proper slide-out **detail drawer** and a few custom error pages.

---

## A note on AI tools

I used an AI coding assistant throughout — for scaffolding, boilerplate, test fixtures, and
working through the Spring Boot 4 / Jackson 3 API changes. Every decision here is mine and I
can walk through any part of it: the reconciliation rules and where the tolerances came
from, the priority-order refinement, why pending settlements are a watch item, the tenancy
model, and the LLM parameter choices. The commit history is deliberately incremental so the
build-up is easy to follow.
