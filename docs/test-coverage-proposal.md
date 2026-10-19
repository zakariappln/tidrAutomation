# ThreatIDR test coverage proposal (2026-10-18)

Sources: the live app at `http://localhost` (ThreatIDR v1.5.1, image `threatidr-production:0.0.5`) explored through
the browser with the saved admin session, and the application source at
`/home/technonext/Documents/ppln/tidr-build-v1/tidr/src` (refs below are `file:line` under that folder).
Existing suite: 27 feature files, 95 scenarios, of which 24 are active and 71 are `@wip`.

Legend: **NEW** = not in any feature yet. **WIP** = a scenario exists but is `@wip`; listed only where the
source changes what it should assert. Priority: P1 ship-blocking / security, P2 core functional, P3 polish.

---

## 1. Confirmed live today (write these first, they fail or pass against facts)

| # | Area | Observation | Suggested scenario | Pri |
|---|---|---|---|---|
| 1 | DGA, History of IP, Site Risk, Backup & Restore | `GET` returns **500 TemplateNotFound** for the Super Administrator; templates ship under `_dga_domain/`, `_history_of_ip/`, `_srisk_domain/`, `_backup_and_restore/`, sidebar links commented out | NEW: "Disabled modules do not crash" — expect a redirect or 404, never 500. Keep as `@regression @negative` and let it fail until product decides | P1 |
| 2 | Whitelist/Blocklist history | `GET /en/whitelist_blocklist_history/` → **500** `TypeError: Object of type BaseQuery is not JSON serializable` (`views/whitelist_blocklist_history/views.py:30`) | NEW: route returns the history, not 500 | P1 |
| 3 | Dashboard | "Total Detection 73001600% in last 24h" rendered live | WIP "Allowed Requests percentage change is a plausible number": assert all four tiles' deltas are within -100%..+10000% | P2 |
| 4 | Dashboard header | Designation renders literally as `None` | WIP "A configured user designation is shown, not a placeholder": assert never the string `None` | P3 |
| 5 | Backup & restore | `GET /en/backup_and_restore/restore_snapshot_list` is served **without a session** (`views/backup_and_restore/views.py:76-106`) | Existing scenario "BUG - restore-snapshot list is served without authentication" is active; keep | P1 |

---

## 2. Functional gaps by module

### Login / session / password reset
- NEW P1 **Session idle timeout is 15 min** (`core/app.py:114-116`): after 15 min without a request, the next page load redirects to login; a request at 14 min keeps the session alive (sliding). Use the API probe to avoid a real browser wait, or `@slow`.
- NEW P2 **Login is case-sensitive on email** (`views/home/forms.py:29`): `Admin@threatidr.com` is rejected with "Email - Wrong credentials". Decide with product whether that is wanted; the test documents it either way.
- NEW P2 **Logging in from `/ja/` lands on `/en/dashboard/`** when default locale is `en` (`views/home/views.py:110-115`): the chosen language is lost on login.
- NEW P1 **Open redirect**: authenticated `GET /en/?next=https://evil.example` redirects off-site (`views/home/views.py:94-96`). Assert it stays on the app host.
- NEW P2 **Logout is a GET** (`/en/logout/`): a plain link or image tag logs the user out (CSRF). Assert either POST-only or a confirmation.
- NEW P1 **Password reset enumeration**: unknown email flashes "This user does not exist.", known email flashes nothing (`views/home/views.py:149-160`). Assert the same message for both.
- NEW P1 **Reset token is forgeable and reusable**: token = base64(zlib("id&&email&&seconds")) with a 300 s window (`models/user.py:62-74`, `core/utils.py:135-158`). Scenarios: a reset link works twice within 5 min; a link built from a known id + email without ever requesting a reset works. Both should be rejected.
- NEW P2 **Reset password complexity boundary**: Regexp requires 8+ chars, 1 lower, 1 upper, 1 digit, 1 special from `-+_!@#$%^&*.,?` (`views/home/forms.py:48-50`). Outline rows: 7 chars fails, 8 passes, missing each class fails, special char outside the list (e.g. `~`) fails, 40 chars passes, 41 fails.
- NEW P2 **Inactive user** (`is_active=false`) gets "User not activated" only when the password is correct; wrong password still says "Wrong credentials" (`views/home/forms.py:23-41`).

### Users
- NEW P1 **Admin role-guard bypass**: `PUT /en/users/update_user/1` with another user's email in the body changes user 1's role, because the guard looks up by submitted email (`views/user/forms.py:116-120`, `views/user/views.py:374-375`). Scenario: role of user 1 is unchanged afterwards.
- NEW P1 **User 1 can be deactivated** (`is_active_edit=No`) and **a user can delete themself**; no guard exists (`views/user/views.py:362-378, 446-455`). Expect both to be refused.
- NEW P1 **Role 1 (Administrator) can be renamed or deleted** (`:623-626`). Expect refusal.
- NEW P2 **Invalid role id still creates the user** (`views/user/views.py:122-138`): submit role `9999`; expect no user row.
- NEW P2 **Boundary: name length** differs between create (2..30, `views/user/forms.py:19-73`) and edit (3..30) and profile (3..30). Outline: 1 fails, 2 passes on create but fails on edit, 30 passes, 31 fails.
- NEW P2 **Boundary: email length 6..40**: `a@b.cd` (6) passes, 5 fails, 40 passes, 41 fails. Also `Email already registered` is case-sensitive: `Admin@threatidr.com` is accepted as a new user (`:69-72`).
- NEW P1 **Boundary: phone** 15 on create (`:73`), 20 on the edit form but the DB column is `String(15)` (`models/user.py:36`): 16..20 characters on edit → 500. Expect a validation message.
- NEW P2 **Designation** optional on create, required on edit (`views/user/forms.py:75-122`): editing a user created without one is impossible without inventing a value.
- NEW P2 **Photo upload**: `.txt` renamed `.png`, empty filename, 7 MiB + 1 byte (413 message "File size exceeds the 7 MB limit…", `core/settings.py:91`), allowed set `jpg jpeg png gif` (`:92`).
- NEW P1 **Stored XSS via names**: first name `<img src=x onerror=…>` is returned unescaped in the JSON HTML fragment (`views/user/views.py:150, 284-308`). Assert escaped text in the list.
- NEW P2 **Search** matches first/last name only, case-insensitive, never email (`views/user/views.py:39-51`); page size 9, sorted by creation ascending.
- NEW P2 **Role name has no length limit in the form, DB is 255** (`views/user/forms.py:124-173`): 255 passes, 256 → 500. **Rename to an existing name → IntegrityError 500** (duplicate check skipped on edit); expect "A role with this name already exists".
- NEW P2 **Deleting a role still assigned to users**: expect refusal with a message, not a 400 with a stack-trace string.
- NEW P2 **Hidden super admin**: user id 1 is `is_hidden` and never listed (`core/commands.py:59`); assert the Users list never shows `admin@threatidr.com` and the count excludes it.

### Allowed / blocked domains and DNS (RPZ)
- NEW P2 **Domain format outline** (both lists, `validators.domain`): valid `a.example`, `sub.a.example`, 63-char label, punycode `xn--…`; invalid: `example` (single label), `-bad.example`, `a..b`, `a_b.example`, `*.example`, trailing dot `example.com.`, 256 chars, leading/trailing spaces are stripped and then accepted. The library behaviour for IDN/wildcard/trailing dot is **unknown**: run once, then pin the outcome.
- NEW P2 **Case handling**: `Example.COM` is stored as typed; adding `example.com` afterwards is rejected as a duplicate (case-insensitive check, `views/blocked_domain/forms.py:31-73`). DNS lookups for either case must return NXDOMAIN after Apply.
- NEW P2 **Cross-list rule**: a domain in Allowed cannot be added to Blocked ("This domain is already in the Allowlist. Remove it from the Allowlist first.") and vice versa.
- NEW P1 **Delete does not unblock until Apply** (`views/blocked_domain/views.py:193-225`): after deleting an Active blocked domain, DNS still returns NXDOMAIN until "Apply Changes" is clicked. Product decision needed: is that intended? Test documents the behaviour.
- NEW P1 **Wildcard subdomains are blocked**: the zone writes `<d> CNAME .` and `*.<d> CNAME .` (`:71-112`), so `anything.<d>` must be NXDOMAIN too; `<d>x.example` must not.
- NEW P1 **Allowed beats blocked by category**: `whitelisted.host.ppln policy passthru` sits after the user blocklist but before category zones (`core/utils.py:405-424`). Scenario: a domain on the Allowed list that is also in a blocked threat category resolves. And: a domain on **both user lists** is refused by the form, so the zone order never has to decide.
- NEW P2 **Apply Changes always reports success** even when `rndc reload` fails (`:60-118`). Hard to provoke safely; document only.
- NEW P2 **Permission asymmetry**: Blocked list needs module `user`, Allowed list needs `user-create` (`views/allowed_domain/views.py:39-55`). A role with `user-update` only sees Blocked but not Allowed.
- NEW P2 **Unauthorized delete shows success in the UI**: the server answers 200 with `[{"errors":"Unauthorized"},401]` and the JS removes the row anyway (`assets/js/scripts/blockedDomains.js`). Scenario for a limited role: row must still exist after reload.
- NEW P2 **Pagination**: 10 rows per page, newest first, `?page=999` → 404 page (paginate `error_out`).
- NEW P3 **History rows**: every add/delete creates a `WhitelistBlocklistHistory` row with `action_type` create/delete and `event_type` Blocked/allowed (note the lower-case `allowed`), visible in Settings → User Log; a deleted user shows as "Deleted User" (`views/setting/views.py:144-155`).
- NEW P2 **DNS bypass toggle** writes `block_domain_to_ip.host.dtq` with `ip-api.com`, `nslookup.io`, `dnschecker.org`, … (`core/settings.py:480`), but that zone is **not declared in named.conf**, so enabling it probably has no effect. Scenario: enable, then `DnsClient` lookup of `ip-api.com` is NXDOMAIN. Expected to fail; it tells product whether the feature works.
- NEW P2 **Public `/en/nxdomain?host=`** is the block page bind redirects to: returns the threat type for a known blocked host, "Unknown" otherwise, and escapes `<script>` in `host`.
- NEW P1 **Public `/en/syslog`** triggers outbound syslog without a session and returns **500 when the threats table is empty** (`views/home/views.py:383-448`).

### Settings
- NEW P2 **RPZ category action outline** for all 14 categories: `block` lists the zone plainly, `alert` adds `policy passthru`, `off` omits it (`core/utils.py:405-424`). Observable via `DnsClient` only for user lists; for category zones assert the generated `/etc/named.conf` inside the bind9 container.
- NEW P2 **RPZ save rejects an unvalidated value**: body `{"threats_categories":{"Adware":"banana"}}` is accepted today (`views/setting/views.py:345-436`); expect 400.
- NEW P1 **`localhost` inconsistency**: accepted by Access Control (`:439-526`) but makes the next RPZ save return 400 "Invalid organization network address found." Existing WIP scenario "The literal value localhost is accepted" should assert both halves.
- NEW P2 **Access Control boundaries**: `10.0.0.0/8` passes, `10.0.0.1/8` (host bits set) fails, `::1` passes, `300.1.1.1` fails, empty list → "Invalid organization network found.".
- NEW P1 **Alert settings with comma-separated emails passes validation then crashes** (`:541`, CSV accepted by `validate_multiple_emails`, then `json.loads`). Expect either acceptance or a clean error.
- NEW P2 **Alert type name > 30 chars** → DB error (`models/setting.py:40`); **email template name > 30** likewise. Boundary 30 / 31.
- NEW P2 **Alert period "No Alert" = 52560000 minutes**: creating one must never send email (observe the 5-minute Celery task sends nothing).
- NEW P2 **Syslog settings**: port `0` accepted (bug), `65536` rejected, string port → "Invalid Port", missing `socktype` → 500, invalid IP → "Invalid IP, Enter a Valid IP", unreachable server → 409 "Connection refused…". Saving an existing row **shuts down the Celery workers** (`:1256-1259`): assert the notification task still runs a minute later.
- NEW P1 **`GET /en/settings/remove-syslog-info` deletes config on a GET** by any logged-in user (`:310-319`).
- NEW P2 **FraudGuard password is echoed into the page** in clear text (`views/setting/forms.py:209-211`). Assert the input value is empty after save.
- NEW P2 **General settings**: `bn` is not offered though `LANGUAGES` has it; timezone outside `pytz.common_timezones` rejected; language change applies immediately, timezone only after restart (`:337-338`, `core/app.py:79-82`).
- NEW P3 **Visiting Settings has side effects**: it creates default rows and calls `https://ifconfig.me` (`:209`). Assert the page still loads with outbound internet blocked.

### Reports
- NEW P1 **`report-export` is never enforced**: any logged-in user can `POST /en/reports/csv_download` and `/pdf_download` (`views/report/views.py:136, 220`). Existing WIP "A user without report permission cannot export" should expect this to fail today.
- NEW P1 **Generated PDFs are public**: `static/reports/<MonDDYYYY>_<MonDDYYYY>_<type>_report.pdf` is fetchable without a session (`tasks/report.py:46-50`).
- NEW P2 **Date boundaries**: `date` must be `dd/mm/yyyy-dd/mm/yyyy`; from > to, 31/02/2026, empty → today a 500 (`core/utils.py:174-183`). `type` other than `summary`/`details` → 500.
- NEW P2 **CSV columns and risk score map**: Timestamp, Country Destination, Threat Type, Source IP, Destination Domain, Destination IP, Tld, Asn, Operator, Risk, Risk Score with Info 0 / Gray 1 / Low 2 / High 3 / Critical 4.
- NEW P3 **"Suspicious" risk filter never matches** (`views/report/forms.py:14-22`).

### Threat detection
- NEW P2 **Opening `/en/threats/` marks every threat viewed** and resets the bell count (`views/discover/views.py:49`).
- NEW P1 **Unauthenticated data endpoints**: `/en/threats/show-threat-year-month-chart` and `/tooltip-threat-year-month-chart` return data without a session (`:71, :143`).
- NEW P1 **`/en/threats/fetch_threats` leaks the raw SQL** in its response (`:248`).
- NEW P2 **Filter boundaries**: `domain_count` 5 (≤5) and `>40`; `date` default last 30 days; `risk_category` outside the enum ignored; `length` 0 / negative / 10000; `draw` missing → 500.
- NEW P2 **Third-party lookups with a blank key** (VirusTotal, Shodan with hard-coded `8.8.8.8` `apis/shodan_scan.py:15`, AbuseIPDB, OTX, IPinfo, FraudGuard, Censys): each must return a clean error, not a 500, and must not hang (VirusTotal disables TLS verification `apis/virus_total.py:13-15`).
- NEW P2 **500 when the `mitaka` settings row is missing** (fresh install before anyone opened Settings) (`:50-66`).

### Profile
- WIP scenarios are right; add boundaries: phone is **required** on profile (`views/profile/forms.py:12-49`) but optional on user create; about 500/501; current password 6..40; new password may equal the old one (`:52-80`); other sessions stay valid after a password change (decide if that is wanted).

### Localization
- NEW P2 `/bn/dashboard/` is in `LANGUAGES` but never offered; `/xx/dashboard/` falls back to default on the home blueprint and 302s elsewhere (`core/app.py:95-112`).
- NEW P2 Japanese PDF renders Japanese glyphs (already WIP).

---

## 3. API coverage (none exists yet; `features/api/` is empty)

The integration API (`views/api/views.py`, CSRF-exempt, JWT) is the best API suite candidate:

| Endpoint | Cases |
|---|---|
| `POST /en/api/login` | happy path returns `{token, message:"Login successful"}` with HS256 JWT exp +3 days; wrong content type → 401 "Please provide correct content type"; missing field / unknown email / wrong password all → 401 "Please provide email and password" (good: no enumeration); **inactive user can log in** (bug, `:43-57`); **no rate limit** (4 attempts in 60 s all answered) |
| `POST /en/api/query/data` | token in `Authorization` **without** `Bearer`; missing → 401 "Authentication Token is missing!"; expired / unknown user → "Invalid Authentication Token"; garbage → "Token is invalid"; every validation message in §16 of the fact sheet returned with **HTTP 200**; `page` 0 → 1, negative → error; `limit` 0 → 1; `log_type` outside Full/Query/Threat; date format `%Y-%m-%d %H:%M:%S`; **SQL injection** through `source_ip`, `destination_ip`, `domain_or_subdomain` (`:130-137`): `1.1.1.1' OR '1'='1` must return no extra rows |
| Dashboard widget JSON (`/en/dashboard/*`) | all login-only, no permission check; `range` outside the enum silently means 1 day; bad `start_date` → 500; `notification-update` GET returns "Get request not allowed" with 200 |
| Legacy dashboard POSTs (`views/dashboard/views.py:116-919`) | `range` / `start_date` string-formatted into SQL: inject `'; SELECT 1--` and expect a validation error, not a query |

---

## 4. Background behaviour worth asserting

- Notification socket `/notifysocket` emits every minute with the unviewed count; `cors_allowed_origins="*"` (`core/app.py:147-152`).
- Email alerts every 5 min re-send the same threats for any period above 5 (`tasks/celery_worker.py:263-351`).
- Housekeeping deletes `dnslogs` older than the period, never `threats` (`:905-973`); period `'3'` is labelled "2 Month".
- Service monitor sends "URGENT: <name> Service Down Alert" only on transitions.

---

## 5. Suggested order of work

1. P1 security rows in §1, §2 Users, Reports, Settings (one `security.feature` per module, tagged `@security @critical`). Most will **fail** on v1.5.1; that is the point.
2. Boundary outlines for every form (Users, Domains, Profile, Settings) — cheap, deterministic, no DNS needed.
3. DNS/RPZ scenarios with `DnsClient` (wildcard, case, allowed-beats-category, delete-until-apply, bypass).
4. The API suite from §3 using the existing API glue.
5. Un-`@wip` the 71 parked scenarios one module at a time with `bdd-step-writer`.
