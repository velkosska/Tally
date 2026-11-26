# Tally

Split shared costs with friends and settle up in as few payments as possible.

Log what everyone paid on a trip, in a shared flat or at dinner. Tally keeps a live balance for each
person and works out the shortest list of payments that gets everyone back to zero.

**Built with:** Java 21 · Spring Boot 3.5 · Thymeleaf · Spring Data JPA · Flyway · PostgreSQL ·
JUnit 5 · Testcontainers · Docker · GitHub Actions

## Features

- **Groups and members**: one group per trip or flat, each with its own currency.
- **Expenses**: who paid, how much, a category, and who it is split between (equally, to the cent).
- **Balances**: a diverging bar chart showing who is owed and who owes.
- **Settle up**: the minimum-payment plan, shown as a receipt.
- **Spending by category**: a doughnut chart plus a table.
- **CSV export**: expenses, balances and the payment plan, ready for Excel.
- Validation with friendly messages, a confirmation before deleting anything, custom 404 and error pages,
  and a responsive layout that works on phones.

## Run it

You need **Docker** running for everything below: the app uses PostgreSQL in every environment,
including local development and tests, so what you test is what runs in production.

**Option 1: Maven** (needs Java 21, Maven 3.9+ and Docker)

```bash
mvn spring-boot:run
```

Open <http://localhost:8080>. Spring Boot's Docker Compose support starts the `db` service from
`docker-compose.yml`, waits until it is healthy and connects to it, so there is nothing to configure.
The `dev` profile loads two demo groups into an empty database. Data is kept in the `db-data` Docker volume
between runs; `docker compose down -v` wipes it.

To look inside the database with a SQL client, find the port Docker picked with `docker compose port db 5432`
and connect as user `tally`, password `tally-dev-password`, database `tally`.

**Option 2: Docker only** (runs the app in a container too, like production)

```bash
docker compose --profile app up --build
```

**Run the tests**

```bash
mvn verify          # unit + integration tests, coverage report in target/site/jacoco/index.html
```

Integration tests start a throwaway PostgreSQL 17 container with [Testcontainers](https://testcontainers.com),
run the real Flyway migrations on it and remove it afterwards.

## How the settle-up algorithm works

This is the interesting part of the project, so it lives in its own package (`com.tally.settlement`)
with no Spring or database code, which makes it easy to test.

**Step 1: net balances.** For each person, `balance = what they paid − their share of what they took part in`.
Positive means the group owes them; negative means they owe the group. All balances always add up to zero.

**Step 2: splitting to the cent.** Money is never stored as `double`, because `0.1 + 0.2 != 0.3` in
floating point. Tally works in whole cents (`long`) and only converts back to `BigDecimal` for display.
If €100.00 is split three ways, the shares are €33.34 + €33.33 + €33.33, so no cent is ever lost.

**Step 3: the payment plan (greedy).**

1. Put everyone who owes money in one priority queue and everyone who is owed in another, largest first.
2. Take the biggest debtor and the biggest creditor. The debtor pays `min(debt, credit)`.
3. Whoever still has a balance goes back in the queue. Repeat until everyone is at zero.

Each round settles at least one person completely, so **n people never need more than n − 1 payments**.
Instead of 4 friends each paying each other back (up to 12 payments), Tally usually needs 2 or 3.

Finding the absolute minimum number of payments is an NP-hard problem (it reduces to subset sum), so real apps
use a fast greedy approach like this one. A property-based test (`SettlementCalculatorTest`) checks
the guarantees on 200 random groups every build: everyone ends at exactly zero, and there are never more than n − 1 payments.

## Architecture

```
Browser ──HTTP──▶ web            Controllers, form objects with validation, CSV export
                   │
                   ▼
                  service        Use cases (@Transactional), returns read-only view records
                   │
          ┌────────┴────────┐
          ▼                 ▼
       domain           settlement   Pure Java algorithm, no framework code
   JPA entities,
   business rules
          │
          ▼
   PostgreSQL        (schema managed by Flyway migrations)
```

```
src/main/java/com/tally
├── domain/        ExpenseGroup (aggregate root), Member, Expense, Category, repository
├── settlement/    BalanceCalculator, SettlementCalculator (the algorithm)
├── service/       GroupService, ExpenseService, DashboardService, view/ records
├── web/           Controllers, form/ objects, MoneyFormatter, CsvExporter, error handling
└── config/        DemoDataLoader
src/main/resources
├── db/migration/  Flyway SQL migrations
├── templates/     Thymeleaf pages and shared layout fragments
└── static/        CSS, a small JS file, favicon
```

## Design decisions

| Decision | Why |
|---|---|
| `ExpenseGroup` is an **aggregate root** | Members and expenses are only changed through the group, so rules such as "the payer must be in the group" and "no duplicate names" are enforced in one place. |
| **Command and view records** between layers | Services never see web form classes, and templates never see JPA entities. `open-in-view` is off, so lazy-loading bugs can't hide in templates. |
| **Algorithm is framework-free** | `settlement/` is plain Java: fast to unit-test and easy to explain. |
| **Money in cents / `BigDecimal`** | Floating point can't represent most decimal amounts exactly. |
| **Flyway + `ddl-auto: validate`** | The schema is versioned SQL, and Hibernate checks that the entities match it at startup. |
| **PostgreSQL everywhere** | Dev (Docker Compose), tests (Testcontainers) and production all run the same database, so SQL that works locally works in production. |
| **Profiles** (`dev`, `test`, `prod`) | Demo data and live template reloads locally; credentials from environment variables in production. |
| **Constructor injection, `final` fields** | Dependencies are explicit and classes are easy to test. |
| **Progressive enhancement** | Every page works without JavaScript; JS only adds the chart, a split preview and delete confirmations. |
| **CSV injection protection** | Cells starting with `=`, `+`, `-` or `@` are escaped so Excel won't run them as formulas. |

## DevOps

- **CI (GitHub Actions)**: every push and pull request compiles, runs all tests, uploads the test and
  coverage reports, then builds the Docker image. See `.github/workflows/ci.yml`.
- **Docker**: multi-stage build, a layered jar for fast rebuilds, a small JRE image, a non-root user,
  memory-aware JVM settings and a built-in health check.
- **Docker Compose**: PostgreSQL for local development (started automatically by `mvn spring-boot:run`), plus
  the app container behind the `app` profile, started in the right order using health checks.
- **Health and metrics** with Spring Boot Actuator: `/actuator/health` (with liveness and readiness probes),
  `/actuator/info` and `/actuator/metrics`.
- **Dependabot** keeps Maven, Docker and GitHub Actions dependencies up to date.
- **12-factor config**: no secrets in code; production settings come from environment variables (see `.env.example`).
- **Graceful shutdown**, so in-flight requests finish before the app stops.

## Tests

| Test | What it covers |
|---|---|
| `BalanceCalculatorTest` | Balances, uneven splits, payers who aren't participants |
| `SettlementCalculatorTest` | Payment plans, invalid input, and a 200-run randomized property test |
| `CsvExporterTest` | Quoting and formula-injection escaping |
| `ExpenseFlowIntegrationTest` | Real services against the real Flyway schema on PostgreSQL (Testcontainers): create, edit, delete, business rules |
| `WebFlowTest` | HTTP requests through MockMvc against PostgreSQL (Testcontainers) that render the real templates: validation errors, 404 page, CSV download, health check |

## Deploying

Any host that runs a Docker image works, for example Render, Railway or Fly.io:

1. Create a PostgreSQL database on the host.
2. Deploy from the `Dockerfile`, and set `DB_URL` (for example `jdbc:postgresql://host:5432/tally`),
   `DB_USERNAME` and `DB_PASSWORD`. Set `DEMO_DATA=true` to start with example groups.
3. Point the host's health check at `/actuator/health`.

## Ideas for next steps

- User accounts with Spring Security, so each person sees only their own groups
- Uneven splits (by percentage or exact amounts)
- Marking a payment as settled, and keeping a history of settlements
- PDF export of the receipt
# Tally
