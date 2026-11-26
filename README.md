# Tally

Split shared costs with friends and settle up in as few payments as possible.

Log what everyone paid on a trip, in a shared flat or at dinner. Tally keeps a live balance for each
person and works out the shortest list of payments that gets everyone back to zero.

**Built with:** Java 21 · Spring Boot 3.5 · Thymeleaf · Spring Data JPA · Flyway · SQL Server / Azure SQL ·
JUnit 5 · Testcontainers · Docker · GitHub Actions · Azure App Service

## Features

- **Accounts**: sign up, log in and log out with email and password, with an optional "keep me logged in".
  Everyone only sees the groups they are a member of.
- **Groups and members**: one group per trip or flat, each with its own currency. The person who creates a group
  is its first member; other members can be added by name.
- **Invite links**: any member can create a share link and paste it in a group chat (Tally never sends emails).
  Whoever opens it logs in or signs up, then either joins as someone new or claims a name that was added before
  they had an account, which keeps that name's expenses. Links work for 14 days and can be switched off at any time.
  The home page lists the people you share groups with.
- **Expenses**: who paid, how much, a category, and who it is split between.
- **Uneven splits**: split an expense equally, by exact amounts, by percentages or by shares (for example nights
  stayed). The form shows each person's part as you type and says when amounts or percentages don't add up.
- **Balances**: a diverging bar chart showing who is owed and who owes.
- **Settle up**: the minimum-payment plan, shown as a receipt.
- **Spending by category**: a doughnut chart plus a table.
- **CSV export**: expenses (with how each was split and everyone's part), balances and the payment plan,
  ready for Excel.
- Validation with friendly messages, a confirmation before deleting anything, custom 404 and error pages,
  and a responsive layout that works on phones.

## Run it

You need **Docker** running for everything below: the app uses SQL Server in every environment,
including local development and tests, so what you test is what runs in production.

**Option 1: Maven** (needs Java 21, Maven 3.9+ and Docker)

```bash
mvn spring-boot:run
```

Open <http://localhost:8080>. Spring Boot's Docker Compose support starts the `db` service (SQL Server 2022) from
`docker-compose.yml`, waits until it is healthy and connects to it, so there is nothing to configure.
The `dev` profile creates two demo accounts, both with the password `tally-demo`:

| Email | Sees |
|---|---|
| `ana@example.com` | "Lisbon weekend" and "Flat 3B" |
| `ben@example.com` | only "Lisbon weekend", which shows that people only see their own groups |

To try invites, open a group as Ana, click **Create invite link** under *Invite people*, and open the link in a
private window. Sign up there and you can claim "Carla" or "Dani", who were added by name.
In "Lisbon weekend", the apartment is split by shares (nights stayed), so you can see an uneven split right away.

Or create your own account at <http://localhost:8080/signup>. Data is kept in the `db-data` Docker volume
between runs; `docker compose down -v` wipes it. (Groups created before accounts existed belong to nobody and
stay hidden, so wipe the volume once after upgrading.)

To look inside the database with a SQL client (for example the VS Code "SQL Server (mssql)" extension), find the
port Docker picked with `docker compose port db 1433` and connect as user `sa`, password `Tally-dev-Passw0rd`,
database `tally`, with "trust server certificate" on.

On an Apple Silicon Mac the SQL Server image runs through Rosetta: in Docker Desktop, keep **Settings → General →
Use Rosetta for x86_64/amd64 emulation** switched on. The first start downloads about 1.5 GB.

**Option 2: Docker only** (runs the app in a container too, like production)

```bash
docker compose --profile app up --build
```

**Run the tests**

```bash
mvn verify          # unit + integration tests, coverage report in target/site/jacoco/index.html
```

Integration tests start a throwaway SQL Server 2022 container with [Testcontainers](https://testcontainers.com),
run the real Flyway migrations on it and remove it afterwards.

## How the settle-up algorithm works

This is the interesting part of the project, so it lives in its own package (`com.tally.settlement`)
with no Spring or database code, which makes it easy to test.

**Step 1: net balances.** For each person, `balance = what they paid − their share of what they took part in`.
Positive means the group owes them; negative means they owe the group. All balances always add up to zero.

**Step 2: splitting to the cent.** Money is never stored as `double`, because `0.1 + 0.2 != 0.3` in
floating point. Tally works in whole cents (`long`) and only converts back to `BigDecimal` for display.
If €100.00 is split three ways, the shares are €33.34 + €33.33 + €33.33, so no cent is ever lost.

Uneven splits work the same way (`SplitCalculator`). Exact amounts must add up to the total and percentages to
100. For percentages and shares, everyone first gets their share rounded down to the cent; the cents left over
then go one each to the people whose share was rounded down the most (the *largest remainder* method). So
€100.00 split 1 : 2 : 3 is €16.67 + €33.33 + €50.00. The form's live preview uses the same rule, so what you see
before saving is what gets stored.

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
Browser ──HTTP──▶ security       Spring Security: login, remember-me, CSRF; every page needs a login
                   │
                   ▼
                  web            Controllers, form objects with validation, CSV export
                   │
                   ▼
                  service        Use cases (@Transactional), returns read-only view records;
                   │             loads groups only for a user who is a member (the access check)
                   │
          ┌────────┴────────┐
          ▼                 ▼
       domain           settlement   Pure Java algorithm, no framework code
   JPA entities,
   business rules
          │
          ▼
   SQL Server        (Azure SQL Database in production; schema managed by Flyway migrations)
```

```
src/main/java/com/tally
├── account/       AppUser, AccountService (sign-up, BCrypt password hashes)
├── invite/        GroupInvite (a share link), InviteService (create, switch off, join, claim a name)
├── security/      SecurityConfig, TallyUser (the logged-in user), user lookup, login after sign-up
├── domain/        ExpenseGroup (aggregate root), Member (optionally linked to a user), Expense, Category, repository
├── settlement/    SplitCalculator, BalanceCalculator, SettlementCalculator (the algorithms)
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
| **Access checks in the service layer** | Every service method takes the user's id and loads the group with `ExpenseGroupRepository.getForUser`, so no URL can skip the check. A group you aren't in answers 404, the same as one that doesn't exist, so ids can't be probed. |
| **Invite links store only a hash** | A link holds 32 random bytes. The database keeps their SHA-256 hash, so someone who reads the database still can't join groups. Unknown, expired and switched-off links all show the same "doesn't work any more" page. |
| **Claiming a name instead of merging people** | People are often added by name before they have an account. Joining through a link can link the account to that existing member, so the expenses already logged for them stay correct. Only members without an account can be claimed. |
| **Remember-me tokens in the database** | "Keep me logged in" uses Spring Security's persistent tokens (`persistent_logins` table): the cookie holds a random token, not the password hash, it rotates on every use and can be revoked. |
| **Algorithm is framework-free** | `settlement/` is plain Java: fast to unit-test and easy to explain. |
| **Money in cents / `BigDecimal`** | Floating point can't represent most decimal amounts exactly. |
| **Flyway + `ddl-auto: validate`** | The schema is versioned SQL, and Hibernate checks that the entities match it at startup. |
| **SQL Server everywhere** | Dev (Docker Compose), tests (Testcontainers) and production (Azure SQL Database, free tier) all run the same engine, so SQL that works locally works in production. Text columns are `nvarchar`, so names in any language are stored as typed. |
| **Profiles** (`dev`, `test`, `prod`) | Demo data and live template reloads locally; credentials from environment variables in production. |
| **Constructor injection, `final` fields** | Dependencies are explicit and classes are easy to test. |
| **Progressive enhancement** | Every page works without JavaScript; JS only adds the chart, the live split preview, copy buttons and delete confirmations. |
| **CSV injection protection** | Cells starting with `=`, `+`, `-` or `@` are escaped so Excel won't run them as formulas. |

## DevOps

- **CI (GitHub Actions)**: every push and pull request compiles, runs all tests, uploads the test and
  coverage reports, then builds the Docker image. See `.github/workflows/ci.yml`.
- **CD (GitHub Actions → Azure)**: when CI passes on `main`, the exact commit it tested is built and deployed to
  Azure App Service, then checked with the health endpoint. See `.github/workflows/deploy.yml` and
  [Deploy to Azure](#deploy-to-azure).
- **Docker**: multi-stage build, a layered jar for fast rebuilds, a small JRE image, a non-root user,
  memory-aware JVM settings and a built-in health check.
- **Docker Compose**: SQL Server for local development (started automatically by `mvn spring-boot:run`), plus
  the app container behind the `app` profile, started in the right order using health checks.
- **Health and metrics** with Spring Boot Actuator: `/actuator/health` (with liveness and readiness probes),
  `/actuator/info` (both public) and `/actuator/metrics` (needs a login).
- **Dependabot** keeps Maven, Docker and GitHub Actions dependencies up to date.
- **12-factor config**: no secrets in code; production settings come from environment variables (see `.env.example`).
- **Graceful shutdown**, so in-flight requests finish before the app stops.

## Tests

| Test | What it covers |
|---|---|
| `SplitCalculatorTest` | Equal, exact, percentage and share splits, error messages, and a 200-run randomized test that shares always add up to the total |
| `BalanceCalculatorTest` | Balances, uneven splits, payers who aren't participants |
| `SettlementCalculatorTest` | Payment plans, invalid input, and a 200-run randomized property test |
| `CsvExporterTest` | Quoting and formula-injection escaping |
| `ExpenseFlowIntegrationTest` | Real services against the real Flyway schema on SQL Server (Testcontainers): create, edit, delete, business rules, uneven splits saved and reloaded, users only seeing their own groups |
| `WebFlowTest` | HTTP requests through MockMvc against SQL Server (Testcontainers) that render the real templates: validation errors, splitting by shares through the form, percentages that don't add up, 404 page, CSV download, health check |
| `SecurityFlowTest` | Sign-up (validation, duplicate email, BCrypt hash), login, wrong password, remember-me cookie, logout, CSRF rejection |
| `AuthorizationWebTest` | Walks every group, expense, member, invite and export URL as a user who isn't in the group: all 404, nothing changes |
| `InviteFlowTest` | Creating links, joining as someone new, claiming a name (and keeping its expenses), names that are taken, expired and switched-off links, only a hash stored, signing up from a link and landing back on it, the people-you-share-groups-with list |

## Deploy to Azure

Tally runs on **Azure App Service** (Java 21) with **Azure SQL Database** on its free tier. Every push to
`main` that passes CI is deployed automatically by `.github/workflows/deploy.yml`. Secrets live in App Service
settings and GitHub secrets, never in the code.

You do steps 1 to 5 once. They need the [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli)
(`brew install azure-cli` on a Mac) and an Azure for Students subscription.

### 1. Sign in and pick names

```bash
az login
az account show --query "{name:name, id:id, tenant:tenantId}" -o table   # check this is your student subscription

RG=tally-rg
LOCATION=francecentral            # must be a region your subscription allows; see the note below
APP=tally-$RANDOM                 # becomes https://$APP.azurewebsites.net, must be unique worldwide
SQL_SERVER=tally-sql-$RANDOM      # becomes $SQL_SERVER.database.windows.net
SQL_USER=tallyadmin
SQL_PASSWORD="$(openssl rand -base64 18 | tr -d '/+=')Aa1"   # Azure SQL wants upper, lower and a digit
echo "App: $APP  Database server: $SQL_SERVER  Database password: $SQL_PASSWORD"   # save these somewhere safe
```

Azure for Students only lets you create resources in a handful of regions, and the list differs per account.
See yours with:

```bash
az policy assignment list --query "[].parameters.listOfAllowedLocations.value" -o tsv
```

Pick one of those for `LOCATION`. If a command still fails with `RequestDisallowedByAzure`, the region isn't
allowed: change `LOCATION` and run that command again. (The resource group itself may live in any region.)

### 2. Create the database

A new subscription has to switch on the database and web app services once (takes a minute or two each):

```bash
az provider register -n Microsoft.Sql --wait
az provider register -n Microsoft.Web --wait
```

Then create a logical SQL server and one database on Azure's
[free offer](https://learn.microsoft.com/azure/azure-sql/database/free-offer) (free for the life of the subscription,
one free database per subscription):

```bash
az group create -n $RG -l $LOCATION

az sql server create -g $RG -n $SQL_SERVER -l $LOCATION \
  --admin-user $SQL_USER --admin-password "$SQL_PASSWORD"

# 0.0.0.0 is Azure's shorthand for "services running inside Azure", such as the web app. Not the internet.
az sql server firewall-rule create -g $RG -s $SQL_SERVER -n AllowAzureServices \
  --start-ip-address 0.0.0.0 --end-ip-address 0.0.0.0

az sql db create -g $RG -s $SQL_SERVER -n tally \
  -e GeneralPurpose -f Gen5 -c 2 --compute-model Serverless \
  --use-free-limit --free-limit-exhaustion-behavior AutoPause
```

The free database pauses after about an hour without traffic. The next visitor then waits up to a minute while it
wakes up; the app is configured to wait for it (`application-prod.yml`). If the month's free allowance runs out,
`AutoPause` pauses it until the next month instead of charging your credit.

### 3. Create the web app

App Service quota is set per region, and student subscriptions often have 0 for both the paid B1 and the free F1
plan in some regions ("Operation cannot be completed without additional quota"). The web app doesn't have to be in
the database's region, so this tries the free F1 plan in each region you're allowed to use and keeps the first that
works (failed attempts leave nothing behind):

```bash
for L in $(az policy assignment list --query "[].parameters.listOfAllowedLocations.value[]" -o tsv); do
  echo "Trying $L..."
  if az appservice plan create -g $RG -n tally-plan -l $L --is-linux --sku F1 -o none 2>/dev/null; then
    WEB_LOCATION=$L; echo "Created the plan in $L"; break
  fi
done
```

F1 is free and enough for a school project. It doesn't support "always on", so the app sleeps when idle. If you
get B1 quota later (portal → **Quotas**, provider *App Service*), move up with
`az appservice plan update -g $RG -n tally-plan --sku B1` and then `az webapp config set -g $RG -n $APP --always-on true`.

```bash
az webapp create -g $RG -p tally-plan -n $APP --runtime "JAVA:21-java21"

az webapp config appsettings set -g $RG -n $APP --settings \
  SPRING_PROFILES_ACTIVE=prod \
  DB_URL="jdbc:sqlserver://$SQL_SERVER.database.windows.net:1433;database=tally;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=60" \
  DB_USERNAME=$SQL_USER \
  DB_PASSWORD="$SQL_PASSWORD" \
  REMEMBER_ME_KEY="$(openssl rand -base64 32)"

az webapp update -g $RG -n $APP --https-only true
az webapp config set -g $RG -n $APP --http20-enabled true \
  --generic-configurations '{"healthCheckPath": "/actuator/health/readiness"}'
```

The app reads all of these as environment variables (see `application-prod.yml`). Flyway creates the tables on the
first start. Don't set `DEMO_DATA` here: the demo accounts' password is in this README.

### 4. Let GitHub Actions deploy

GitHub signs in to Azure with OpenID Connect, so no Azure password is stored in GitHub.

```bash
SUBSCRIPTION_ID=$(az account show --query id -o tsv)
TENANT_ID=$(az account show --query tenantId -o tsv)
WEBAPP_ID=$(az webapp show -g $RG -n $APP --query id -o tsv)

CLIENT_ID=$(az ad app create --display-name tally-github-deploy --query appId -o tsv)
az ad sp create --id $CLIENT_ID
az role assignment create --assignee $CLIENT_ID --role "Website Contributor" --scope $WEBAPP_ID

az ad app federated-credential create --id $CLIENT_ID --parameters '{
  "name": "github-production",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:velkosska/Tally:environment:production",
  "audiences": ["api://AzureADTokenExchange"]
}'

echo "AZURE_CLIENT_ID=$CLIENT_ID"
echo "AZURE_TENANT_ID=$TENANT_ID"
echo "AZURE_SUBSCRIPTION_ID=$SUBSCRIPTION_ID"
echo "AZURE_WEBAPP_NAME=$APP"
```

Then on GitHub, open the repository's **Settings**:

1. **Environments → New environment** named `production`.
2. **Secrets and variables → Actions → Secrets**: add `AZURE_CLIENT_ID`, `AZURE_TENANT_ID` and
   `AZURE_SUBSCRIPTION_ID` with the values printed above.
3. **Secrets and variables → Actions → Variables**: add `AZURE_WEBAPP_NAME` with your app name.

Until `AZURE_WEBAPP_NAME` is set, the deploy workflow skips itself, so CI stays green before Azure is ready.

**If `az ad app create` is refused** (some university accounts aren't allowed to create app registrations), use a
publish profile instead. It is a password for this one app, so keep it secret:

```bash
az resource update -g $RG --namespace Microsoft.Web --resource-type basicPublishingCredentialsPolicies \
  --parent sites/$APP -n scm --set properties.allow=true
az webapp deployment list-publishing-profiles -g $RG -n $APP --xml
```

Paste the whole XML output into a GitHub secret named `AZURE_WEBAPP_PUBLISH_PROFILE`, skip the three `AZURE_*_ID`
secrets, and still add the `AZURE_WEBAPP_NAME` variable. The workflow uses the publish profile when it is set.

### 5. Deploy

Push to `main`, or open **Actions → Deploy to Azure → Run workflow**. The workflow builds the jar of the commit CI
tested, deploys it, then waits (up to 10 minutes) until `/actuator/health` reports `UP`. The live URL appears on the run's summary.

### Day to day

| Task | How |
|---|---|
| See the logs | Azure portal → your web app → **Log stream**, or `az webapp log tail -g $RG -n $APP` |
| Change a setting | `az webapp config appsettings set -g $RG -n $APP --settings NAME=value` (the app restarts) |
| Connect with a SQL client | Allow your IP once: `az sql server firewall-rule create -g $RG -s $SQL_SERVER -n my-ip --start-ip-address <your IP> --end-ip-address <your IP>`, or use the portal's **Query editor** |
| Save credit when you're not using it | `az webapp stop -g $RG -n $APP` (`start` to resume). The database pauses by itself and costs nothing on the free offer |
| Remove everything | `az group delete -n $RG` |

**Cost:** the F1 plan is free; B1 costs roughly 13 USD a month from your student credit. The database is free:
100,000 vCore seconds and 32 GB a month, and with `AutoPause` it never bills. Check **Cost Management** in the
portal now and then. On F1 the app sleeps after about 20 minutes without visitors and has 60 CPU minutes a day, so the
first visit after a quiet spell is slow (the app and the database both wake up).

### Other hosts

Anything that runs the `Dockerfile` with a SQL Server database works too: set `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD` and `REMEMBER_ME_KEY` (the app won't start without it), and point the host's health
check at `/actuator/health`. `DEMO_DATA=true` adds the demo accounts, whose password is public, so never set it on a
server other people can reach.

## Ideas for next steps

- Marking a payment as settled, and keeping a history of settlements
- PDF export of the receipt
