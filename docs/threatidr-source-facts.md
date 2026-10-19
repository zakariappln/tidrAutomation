# ThreatIDR fact sheet for UI and API test design

I read the code only. I did not run the app or modify anything.

**Path convention.** All `file:line` refs are relative to `/home/technonext/Documents/ppln/tidr-build-v1/tidr/src/`. For example, `views/user/views.py:449` means `/home/technonext/Documents/ppln/tidr-build-v1/tidr/src/views/user/views.py:449`.

**Library versions.** The Docker build uses Pipfile.lock: Flask 2.2.5, Flask-SQLAlchemy 2.5.1, WTForms 3.2.1, Flask-WTF 1.2.2, Flask-Limiter 3.11.0, validators 0.35.0. pyproject.toml/poetry.lock disagree (fsqla 3.0.2), so the deployed version is not fully certain.

---

## 0. Cross-cutting facts (they affect every module)

### URLs and routing
- Every route sits under `/<lang_code>/…`. `LANGUAGES` = `en`, `ja`, `bn` (`core/settings.py:53-57`).
- An unmatched URL or a 405 (`request.view_args is None`) gets a **302 redirect to `/<lang>/`**, not a 404 (`core/app.py:95-112`). A dead route therefore behaves like "redirect to login/dashboard".
- 404/401/403/410/500/413/429 raised inside matched views render `<code>.html`. They return JSON `{code,name,description}` when the `Accept` header prefers JSON (`core/app.py:210-236`).
- A 413 flashes `"File size exceeds the 7 MB limit. Please upload a smaller file."` and renders `home/update.html` (`core/app.py:230-232`).

### Auth, session, CSRF, rate limit
- Session: `session.permanent=True`, `permanent_session_lifetime = 15 min`, `session.modified=True` on every request. This is a **sliding 15-minute idle timeout** (`core/app.py:114-116`).
- `login_user(..., remember=False)` (`views/home/views.py:104`).
- Unauthenticated requests are redirected to `home.index`, i.e. the login page, not 401 (`views/home/views.py:79-87`).
- CSRF is global via `CSRFProtect` (`core/app.py:138`). `WTF_CSRF_TIME_LIMIT = 3600` s (`core/settings.py:72`).
- The `api` blueprint is CSRF-exempt (`core/app.py:202`).
- JS sends the `X-CSRFToken` header from a meta tag (`assets/js/scripts/util/axios.js:5`, `assets/js/script.js:3`).
- Rate limit: there is exactly one, `with limiter.limit("3/minute")` around login POST (`views/home/views.py:102`).
  - Exceeding it renders `429.html` with status 429 (`:119-120`).
  - The key is `get_remote_address` (`core/extensions.py:33`). There is no ProxyFix anywhere, so behind nginx all clients probably share one bucket.
  - Strategy is fixed-window, storage is Redis (`core/settings.py:95-97`).
  - Successful logins also count toward the limit.
- There is **no account lockout and no failed-attempt counter** anywhere.
- License middleware applies only if `LICENSE_MANAGER_SERVICE_URL != ''` (`example_env:43` sets `''`). It only checks the hard-coded `/en/...` paths (`core/app.py:120-128`), so `/ja/...` bypasses it.

### Permission model
- `has_permission(p)`: true if any role has `p` or `*` (`models/user.py:81-93`).
- `has_module_permission(m)`: true if any allowance prefix before `-` equals `m`, or the role has `*` (`models/user.py:95-109`).
- Permission keys (`core/settings.py:217-236`): `*`, `report-read`, `report-export`, `setting-update`, `user-create/update/delete`, `role-create/update/delete`, `alerttype-create/update/delete`, `emailtemplate-create/update/delete`, `dga-active`, `backup-active`.
- The Role form choices exclude `*`, `dga-active` and `backup-active` (`views/user/forms.py:129-148`). Only CLI-created roles can hold them.

### Response conventions (important for assertions)
- **"Unauthorized" responses are HTTP 200.** The pattern `jsonify({'errors': 'Unauthorized'}, 401)` serialises to the array `[{"errors":"Unauthorized"},401]` with status **200**. Examples: `views/user/views.py:313,462,550,633`, `views/blocked_domain/views.py:190,219`, `views/setting/views.py:342,436,526,566,665,789,872,978,1007,1027,1055`, `views/report/views.py:134`.
- Syslog uses `jsonify(..., 400), 409`, which gives HTTP 409 with an array body (`views/setting/views.py:1226-1267`).
- Page-level denial is `render_template("401.html")` with **HTTP 200**. Examples: users `:91`, blocked `:57`, allowed `:55`, settings `:308`, reports `views/report/views.py:41`.
- Form validation errors usually come back as `{"errors": form.errors}` with HTTP 200. The exception is user PUT, which returns 400 `{"message":"Invalid data","errors":…}` (`views/user/views.py:444`).

### Uploads and admin bootstrap
- `MAX_CONTENT_LENGTH = 7 MiB` (`core/settings.py:91`).
- Image extensions: `jpg`, `jpeg`, `png`, `gif` (`:92`). Patch extension: `zip` (`:486`).
- Admin is bootstrapped by CLI `create_admin_user` (`core/commands.py:21-71`).
  - Default credentials `admin@threatidr.com` / `abcd1234` (`:22`).
  - Role "Administrator" with `["*"]` (`:52-53`).
  - The user is created with `is_hidden=True` (`:59`), so the super admin never appears in the Users list.
  - Code treats **user id 1** as the protected admin.

---

## 1. Login, password reset, logout, localization (`views/home/`)

### Routes

| Path | Methods | Guard | Notes |
|---|---|---|---|
| `/<lang>/` | GET, POST | public | Login. An authenticated GET redirects to `?next=` or the dashboard, an **open redirect** (`views/home/views.py:94-96`). |
| `/<lang>/logout/` | GET | login | Sets cookie `last_logout` (max_age 2y). Flashes `"You have logged out."` (`:131-141`). GET means logout is CSRF-able. |
| `/<lang>/reset/` | GET, POST | public | Redirects to dashboard if logged in (`:144-161`). |
| `/<lang>/reset_with_token/<token>` | GET, POST | public | `:164-180` |
| `/<lang>/nxdomain` | GET | **public by design** | See §5. |
| `/<lang>/syslog` | GET | **public** | See §5. |
| `/<lang>/license`, `/<lang>/save-license` | GET, POST | login | See §15. |
| `/<lang>/update`, `/<lang>/apply_update/<name>`, `/<lang>/service_status` | GET, POST | login **only** | See §10g. |

### Validation (`views/home/forms.py`)
- LoginForm: `email` DataRequired, `password` DataRequired. There is no Email() validator (`:14-15`).
- Custom `validate` (`:23-41`):
  - Unknown email: error on the **email** field, `"Wrong credentials"`.
  - Wrong password: error on the **password** field, `"Wrong credentials"`.
  - `is_active` false: `"User not activated"`. This check runs only *after* a correct password.
- Errors are flashed by `flash_errors` as `"<Label> - <error>"` (`core/utils.py:39-43`).
  - Unknown email shows `"Email - Wrong credentials"`.
  - Wrong password shows `"Password - Wrong credentials"`.
  - **This is user enumeration.**
- The email lookup is an exact, case-sensitive match (`views/home/forms.py:29`).
- After a successful login, the redirect goes to `/<BABEL_DEFAULT_LOCALE>/dashboard/`. The lang segment is string-replaced, so logging in at `/ja/` lands on `/en/` when the default is en (`views/home/views.py:110-115`).
- ForgotForm: `email` DataRequired + Email (`views/home/forms.py:44`).
- ResetPasswordForm (`:48-50`):
  - `password` DataRequired plus Regexp `(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[-+_!@#$%^&*.,?]).{8,}$`.
  - Message: `"Your password must be at least 8 characters, Must have at least 1 number, Must have at least 1 upper case character, Must have at least 1 lower case character, Must have at least 1 special character."`
  - `password2` uses DataRequired + EqualTo('password') with the WTForms default message.

### Reset business rules
- Unknown email: flashes `"This user does not exist."`, then redirects to login.
- Known email: sends mail via the OneSignal API and redirects with **no flash** (`views/home/views.py:149-160`). This is also an enumeration oracle.
- Mail subject `'[ThreatIDR] Reset Your Password'` (`:155`). The link is `url_for(..., _external=True)` (`templates/mail/password_reset.html:4`). `SERVER_NAME` is commented out, so this is likely open to Host-header poisoning.
- **The token is unsigned.** It is `urlsafe_b64(zlib("id&&email&&seconds_since_created_at"))` (`models/user.py:62-74`).
  - Validity check: `(now-created_at) - delta <= 300` (`core/utils.py:135-158`). That means 5 minutes, it is reusable within the window, and it is forgeable by anyone who knows the id and email.
  - Invalid or expired token: silent redirect to login (`views/home/views.py:169-170`).
  - Success flashes `"Your password has been reset."` (`:177`).

### Localization
- The login page offers EN/JA. The BN link is commented out (`templates/home/login.html:42-55`).
- General settings only allow `en` and `ja` (§10a).
- An unknown lang code on the home blueprint falls back to the default locale (`views/home/views.py:62-70`).

---

## 2. Dashboard (`views/dashboard/`)

**Page.** `GET /<lang>/dashboard/`, login only (`views/dashboard/views.py:24-32`).

### Widget JSON APIs
All are login only, with no permission check. Routes come from `views/dashboard/apis/url.py`.

| Route | Methods | File:line | Input / output |
|---|---|---|---|
| `/dashboard/c2-threats-detected` | POST | `apis/c2_threats.py:15` | Returns `{total_c2_count}` |
| `/dashboard/allowed-requests` | POST | `apis/allowed_requests.py:16` | Returns `{total_allowed_count}` |
| `/dashboard/total-threats-detected` | GET, POST | `apis/total_threats_detected.py:15` | Returns `{total_threats_count, nxdomain_count, passthru_count}`. Grouped by `forwarder`; `NXDOMAIN` means blocked, anything else counts as passthru. |
| `/dashboard/blocked-threats-detected` | GET, POST | `apis/blocked_threats.py:15` | Count where `forwarder=='NXDOMAIN'` |
| `/dashboard/top-ten-threat-domains` | GET, POST | `apis/top_ten_treat_domains.py:19` | List of `{bucket, count, destination_domain, source_ip, threat_type}` |
| `/dashboard/dns-activity-trend` | GET, POST | `apis/dns_activity_trend.py:19` | `{series, title:'30 Days DNS Activity Trend'}` |
| `/dashboard/detected-threat-categories` | POST | `apis/detected_threat_categories.py:18` | List of `[{name, y(%), drilldown}]` |
| `/dashboard/daily-threat-detection` | GET, POST | `apis/daily_threats_detection.py:15` | Only GET computes anything |
| `/dashboard/dashboard-statistics` | GET, POST | `apis/dashboard_statistics.py:14` | `{total_threat, blocked_threat, c2_threat, allowed_query}`, each with `30_days_summery` and `24_hours_summery` |
| `/dashboard/latest-threat-update-time` | GET, POST | `apis/latest_threat_update_time.py:15` | `{data}`. Default `'Scanning...'`. |
| `/dashboard/license-info` | GET, POST | `apis/license_info.py:15` | `{data}`. Default `'No Licens Info found...'` (typo in code). |
| `/dashboard/notification-update` | GET, POST | `apis/notification_update.py:12` | POST `{"notify_id":[ids]}` sets `threats.is_viewed=true`. GET returns `"Get request not allowed"` with status 200. |
| `/dashboard/see-all-query-popup` | GET, POST | `apis/see_all_query_popup.py:18` | Fixed 30/180-day windows |
| `/dashboard/see-all-rpz-popup` | POST | `apis/see_all_rpz_popup.py:25` | JSON `{"widget_type"}`. The value `'c2-threats'` filters to C2. |

### Date input contract
Parsed by `views/dashboard/utils/date_utlis.py:4-35`.
- Either `{"range": "24 hours"|"48 hours"|"7 days"|"15 days"|"30 days"}`. Any other range value means 1 day.
- Or `{"start_date":"%Y-%m-%d %H:%M:%S","end_date":…}`. A bad format raises ValueError, giving a 500.

### Data rules and likely bugs
- "30_days_summery" excludes the last 24 hours (`timestamp <= NOW()-24h`) (`apis/dashboard_statistics.py:46-47`).
- `blocked_threat` is copied from `total_threat` (`:31-34`).
- The C2 policy list is `botnetcc.edit.host.ppln`, `botnetcc.hacked.host.ppln`, `botnetcc.ip.ppln`, `cc2.host.ppln` (`apis/c2_threats.py:37-42`, `apis/dashboard_statistics.py:71,76`, `views/discover/views.py:703-708`). It does **not** include `botnetcc.host.ppln`, which is the C2 key in `THREATS` (`core/settings.py:148`).
- "Top ten" uses `order by bucket, count limit 10`, which is ascending by time, not top by count (`apis/top_ten_treat_domains.py:38`).

### Legacy dashboard POST routes (SQL injection)
These are in `views/dashboard/views.py:116-919`:
`feed_update_status`, `number_of_threats`, `threats_count_by_severity`, `detected_infection_by_type`, `unique_source_ips`, `top_tlds`, `top_domain_query_type`, `detected_threats_by_type`, `top_requested_threat_domains`, `top_threat_destinations_by_type`, `latest_threats_detected`, `top_threat_destinations`, `top_infection_by_type`, `slds_with_most_subdomains`, `unique_threat_domain_by_type`, `top_threat_detected_by_client`, `world_wide_threat_destination`.

- They string-format the JSON `range`, `start_date` and `end_date` straight into SQL. Examples: `:131-138`, `:173-180`, `:290-297`.
- `daily-threat-detection` does the same (`apis/daily_threats_detection.py:25-32`).
- `top_requested_threat_domains` is still referenced by the front end (`assets/js/scripts/dashboard_apis/urls.js`).

---

## 3. Users and Roles (`views/user/`)

### Routes

| Path | Methods | Guard |
|---|---|---|
| `/<lang>/users/?page=&search=` | GET (POST renders `401.html`) | module `user` OR `role` (`views/user/views.py:36-91`) |
| `/<lang>/users/register` | POST | `user-create` (`:93-313`) |
| `/<lang>/users/update_user/<id>` | GET, PUT | `user-update` (`:315-444`) |
| `/<lang>/users/update_user/<id>` | DELETE | `user-delete` (`:446-460`) |
| `/<lang>/users/role` | POST | `role-create` (`:470-550`) |
| `/<lang>/users/role/<id>` | GET, PUT | `role-update` |
| `/<lang>/users/role/<id>` | DELETE | `role-delete` (`:552-633`) |

**List.** 9 per page, sorted by `created_at` ascending. Hidden users are excluded. Search is a case-insensitive `ilike` on first or last name only, not email (`:39-51`).

### RegisterForm (`views/user/forms.py:19-73`)
- `email`: DataRequired, Email, Length(6, 40).
- `first_name`, `last_name`: DataRequired, Length(2, 30).
- `designation`, `address`, `website`: Length(max=150).
- `about`: Length(max=500).
- `phone`: Length(max=15).
- `password`: InputRequired, the same Regexp and message as reset, Length(8, 40).
- `confirm`: InputRequired, EqualTo with message `"Passwords must match"`.
- `role`: NoValidationSelectField, coerce=int, with no choice validation.
- Uniqueness: `"Email already registered"`. This is an exact, case-sensitive match (`:69-72`).
- Photo (`views/user/views.py:106-120`):
  - Wrong extension gives `{'error':'Invalid photo!'}`.
  - A photo part with an empty filename gives `{'error':'No selected file'}`.
  - The file is saved as `<email with . and @ → _>.<ext>`.
- Success response: `{'status':'Ok', user (HTML card), userListView (HTML row), message:'User created successfully.'}` (`:310-311`).
- **Bug:** an invalid or missing role id still commits the user (`User.create` at `:122-134`) before `Role.get_by_id` fails at `:136-138`.

### EditUserForm (`views/user/forms.py:75-122`)
- `email`: DataRequired, Email, Length(6, 40). The email is *not* persisted on update.
- `first_name`, `last_name`: Length(**3**, 30). Register allows 2.
- `designation`: **required**, Length(2, 150).
- `phone`: Length(max=**20**), but the DB column is `String(15)` (`models/user.py:36`), so 16 to 20 characters will cause a DB error.
- `is_active_edit`: Yes/No.
- Admin guard: if the user found **by the submitted email** has id 1 and `role > 1`, the error is `"Sorry! Can not change this role"` (`:116-120`).
  - The guard looks up by form email, not by the URL `<id>`.
  - The view-side check is a no-op `pass` (`views/user/views.py:374-375`).
  - **Bypass:** send PUT `/update_user/1` with another user's email.
- Success: `'User updated successfully'` with status 200 (`:442-443`).

### Delete
- id 1 returns **403** `{"error":"Sorry! Can not delete the Admin user"}` (`:447-450`).
- Otherwise it is a hard delete, returning `"User deleted successfully"` (`:452-455`).
- There is no self-delete check and no check against deactivating user 1.
- An unknown id returns 400 `{"error": "<python exception text>"}` (`:464-468`).

### RoleForm (`views/user/forms.py:124-173`)
- `name`: DataRequired. There is no length limit; the DB column is `String(255)` with a unique constraint.
- `permissions`: MultiCheckbox over the 15 choices.
  - Selecting none gives `"At least one permission must be selected."`.
  - A name that already exists on create gives `"A role with this name already exists"`. The check is exact-case and is skipped on edit, so renaming to an existing name hits a DB IntegrityError (500).
- Messages: `'Role created successfully.'` (`views/user/views.py:547`), `'Role updated successfully'` (`:618`), `"Deleted"` (`:626`).
- Nothing protects role 1 (Administrator) from rename, edit or delete.
- Deleting a role that is still assigned has no explicit handling. Any exception becomes 400 `{"error": str(e)}`.

### XSS candidates
User names, designation and role names are interpolated **unescaped** into the HTML fragments returned in JSON (`views/user/views.py:150, 284-308, 398, 531-545`).

---

## 4. Allowed domains / Blocked domains / Whitelist-blocklist history

### Routes

| Path | Methods | Guard |
|---|---|---|
| `/<lang>/blocked_domains/?page=` | GET | module `user` (`views/blocked_domain/views.py:41-57`) |
| `/<lang>/blocked_domains/add` | POST | `user-create` (`:122-190`) |
| `/<lang>/blocked_domains/delete/<id>` | DELETE | `user-delete` (`:193-225`) |
| `/<lang>/blocked_domains/generate_blacklist` ("Apply Changes") | POST | module `user` (`:60-118`) |
| `/<lang>/allowed_domains/?page=` | GET | **`user-create`**. This is inconsistent with the blocked list (`views/allowed_domain/views.py:39-55`). |
| `/<lang>/allowed_domains/add` | POST | `user-create` (`:121-189`) |
| `/<lang>/allowed_domains/delete/<id>` | DELETE | `user-delete` (`:192-223`) |
| `/<lang>/allowed_domains/generate_whitelist` | POST | module `user` OR `role` (`:58-117`) |
| `/<lang>/whitelist_blocklist_history/` | GET | `user-create`. Calls `jsonify(Query)`, so it **will 500**. Without permission it returns `{"error":"Current user has no permission to access it"}` (`views/whitelist_blocklist_history/views.py:24-35`). |

The real history UI is the Settings "User Log" tab (§10e).

### Validation
Blocked: `views/blocked_domain/forms.py:31-73`. Allowed: `views/allowed_domain/forms.py:31-73`. Both have the same shape.
- `domain`: DataRequired, Length(max=255).
- The value is `.strip()`ped, then `validators.domain(...)` must pass, else `"Please enter a valid domain"`.
- Duplicates are checked case-insensitively with `func.lower`:
  - Blocked: `"Blocked domain already added"`.
  - Allowed: `"Whitelisted domain already added"`.
- Cross-list check:
  - Blocked form: `"This domain is already in the Allowlist. Remove it from the Allowlist first."`
  - Allowed form: `"This domain is already in the Blocklist. Remove it from the Blocklist first."`
- Normalisation is **strip only**. Case is stored as typed, and there is no trailing-dot or wildcard handling in code.
- Acceptance of a trailing dot, `*.x`, IDN/punycode, underscores or single-label names is decided by `validators==0.35.0`, which is not vendored. Treat these as test inputs, not known outcomes.

### Business rules
- Pagination: 10 per page, `create_at` descending. The `per_page` query parameter in pager links is ignored. An out-of-range `?page=` triggers paginate `error_out` (404 page).
- There is no maximum list size.
- New rows have `is_active=False`, shown as an "Inactive" badge (`models/blocked_domain.py:21`, `views/blocked_domain/views.py:171-175`).
- Add messages: `'Blocklist domain created successfully.'` / `'Whitelisted domain created successfully.'`.
- Delete messages: `"Blocklist domain deleted successfully"` / `"Whitelisted domain deleted successfully"`.
- Delete with an unknown id returns 400 `{"error":"'NoneType' object has no attribute 'delete'"}`.
- **Delete does not rewrite the zone.** The domain stays effective until the next Apply.
- History rows (`WhitelistBlocklistHistory`): `action_type` is `create`/`delete`; `event_type` is `Blocked` or `allowed` (`models/whitelist_blocklist_history.py:7-8`).
- The front-end delete handler shows `"Item deleted successfully"` and removes the row regardless of the response body (`assets/js/scripts/whitelistedDomains.js:110-118`, same pattern in `blockedDomains.js`). An unauthorized delete returns 200, so the UI falsely shows success.

### Apply Changes
1. Sets every `is_active=False` row to True (`views/blocked_domain/views.py:67`, `views/allowed_domain/views.py:65`).
2. Rewrites the zone file (see §5).
3. Runs `rndc -c /etc/bind/rndc.conf reload <zone>` without checking the exit code.
4. Always flashes `'Successfully added Blocklist domains'` / `'Successfully added Allowlist domains'`. On permission failure it flashes `'Failed to add …'`.
5. Redirects to the list.

If the `BIND_DIR` env var is unset, `None + str` raises a 500.

---

## 5. DNS / RPZ specifics

### Zone files
SOA: TTL 60, refresh 120, retry 60, expire 432000, `need.to.know.only.`, `hostmaster.pipelinesecurity.net.`, `NS localhost.`

| Zone (file at `$BIND_DIR/<name>`; example `BIND_DIR=/var/named/`) | Written by | Records per domain | Serial |
|---|---|---|---|
| `pplnbl.host.ppln` (user blocked) | `views/blocked_domain/views.py:71-112` | `<d> CNAME .` and `*.<d> CNAME .` (NXDOMAIN) | `int(time.time())` |
| `whitelisted.host.ppln` (user allowed) | `views/allowed_domain/views.py:71-111` | `<d> CNAME rpz-passthru.` and `*.<d> CNAME rpz-passthru.` | `int(time.time())` |
| `block_domain_to_ip.host.dtq` (DNS bypass) | `views/setting/views.py:1057-1101` | `CNAME .` and wildcard for each `DNS_BYPASS` entry (`core/settings.py:480`: `ip-api.com`, `domaintoipconverter.com`, `nslookup.io`, `dnschecker.org`, `infobyip.com`, `reverseip.domaintools.com`). An empty file when disabled. | fixed `1648705081` |
| `pplnbl.dga.pplnb` (DGA) | `views/discover/views.py:925-983`, triggered by `GET /threats/dga` | `CNAME .` and wildcard | fixed, **no reload** |

- There are no "redirect IP" rules anywhere in the code. Only `CNAME .` (NXDOMAIN) and `CNAME rpz-passthru.` are used.
- `DNS_BYPASS` zone and the DGA zone are **not declared** in the generated `named.conf`. Whether DNS bypass takes effect is therefore unclear and likely not.

### named.conf generation
`write_named_conf` (`core/utils.py:360-570`) overwrites `/etc/named.conf` and runs `rndc -c /etc/bind/rndc.conf reconfig` (`:580`). `restart_named` returns True even when rndc fails, because there is no `check` (`:578-581`).

- `response-policy` order (`:405-424`):
  1. `pplnbl.host.ppln` first.
  2. Then `whitelisted.host.ppln policy passthru`.
  3. Then each category zone: `block` is listed plainly, `alert` gets `policy passthru`, `off` is omitted.
- Category zones become `type slave` with `masters {209.182.234.53;}`. `off` zones are not defined.
- These keys are always skipped: `nrd0-3`, `malware2`, `bad-nameservers.host.ppln`, `bad-nameservers.ip.ppln` (`:415, 429`).
- Settings: `listen-on any`; `allow-query` and `allow-recursion` = `localhost` plus the stored org networks; `dnssec-validation no`.
- Logs: `/var/named/bind.log`, `rpz.log`, `query.log`.
- Category to zone map: `THREATS_CATEGORIES` (`core/settings.py:123-138`). Display names: `THREATS` (`:139-164`). Levels: `THREATS_LEVELS` (`:167-192`).

### Public route: `GET /<lang>/nxdomain?host=<h>`
`views/home/views.py:183-203`
- Takes the last two labels of `host` and runs `LIKE %x%` on `threats.query_domain`, using the latest row.
- Renders `threat_type` from `THREATS`, or `"Unknown"`, or none.
- `{{host}}` is autoescaped; there is no `|safe` in `templates/home/nxdomain.html:162-167`.

### Public route: `GET /<lang>/syslog`
`views/home/views.py:383-448`
- Takes the newest threat and builds the plain-text message:
  `THREAT policy_name=<name> source_id=<ip> domain=<d>  risk=<lvl> risk_score=<0-4> app='Pipeline ThreatIDR'`
  - Note the key is `source_id`, not `source_ip`.
  - Scores: Info 0, Gray 1, Low 2, High 3, Critical 4.
- If `syslog_settings` exists it calls `rpz_to_syslog`, then returns the message.
- With **no threats in the DB it returns None, giving a 500**.
- Unauthenticated callers can trigger outbound syslog.

### Syslog wire format
`core/utils.py:636-741`
- Shape: `<134>1 <ISO ts in THREATIDR_LOCAL_TIMEZONE> <hostname=query_domain> ThreatIDR <pid> <TCPRPZ-|UDPRPZ-|TCPQUERY-|UDPQUERY-><id> [<message>]`.
- TCP is used if `socktype` is true, else UDP.
- On `ConnectionRefusedError` the code sets `config.is_forwarding=false` in the DB (`:719-732`). A test can observe this.

---

## 6. DGA domains

### Routes
- `GET|POST /<lang>/dga_domains/?page=` (`views/dga_domain/views.py:40-88`).
  - Guard: module `dga`, which only `*` roles can have in practice. The check runs **after** the query has already executed.
  - Renders `dga_domain/index.html`, but only `templates/_dga_domain/` exists. There is no `template_folder` override, so expect **TemplateNotFound (500)**.
  - The sidebar link is commented out (`templates/sidebar.html` ~`:289-316`).
  - POST `filter_by_date` is concatenated into SQL: `text("CURRENT_DATE - INTERVAL '"+filter_by_date+" DAY'")` (`:59`). **SQL injection.**
  - `filter_by_source` is parameterised.
  - Pagination: 10 per page, `prediction == 1`, ordered by `create_at` descending.
- `GET /<lang>/threats/dga` (`views/discover/views.py:863-923`), login only.
  - Pulls the last hour of dnslogs and posts them to the ML API using a **hard-coded `Secret-Key`** (`:879`).
  - Inserts `dga_informations` rows and writes the DGA zone without a reload.
  - It can raise NameError when the ML response lacks `output`.

### Settings
`POST /<lang>/settings/dgasettings` (module `setting`; `views/setting/views.py:1010-1027`). `period` choices `1`..`12` hour (`views/setting/forms.py:216-233`). The tab is commented out.

---

## 7. Threats / threat detection (`views/discover/`, prefix `/<lang>/threats`)

### Pages and routes
- `GET /threats/`, login only (`views/discover/views.py:46-68`).
  - **Marks all threats `is_viewed=True`** (`:49`), which resets the notification count.
  - It 500s (UnboundLocalError) if the `mitaka` setting row does not exist yet (`:50-66`). That row is only created by visiting Settings.
- **Unauthenticated:**
  - `/threats/show-threat-year-month-chart` (GET, POST; `:71`) returns `[{t:'YYYY-MM-DD', v}]` for the last 365 days.
  - `/threats/tooltip-threat-year-month-chart` (`:143`) takes JSON `{"tooltip_date":"%m/%d/%Y"}`.
- `POST /threats/data` (also GET, which 500s on `int(None)`) (`:416-632`). DataTables form fields:
  - `draw`, `start`, `length`, `search[value]`
  - `threat` (comma-joined policy list or `Unknown`)
  - `risk_category` (`Critical`, `High`, `Low`, `Gray`, `Info`)
  - `date` (`dd/mm/yyyy-dd/mm/yyyy`; default is the last 30 days)
  - `domain_count` (`5` means ≤5, `>40` means >40, otherwise ≤N)
  - `group_by` (country, threat or risk) plus `group_by_val`
  - `short_by` (`asc`, else desc)
  - Search filters on the threat-type display name.
- `POST /threats/data/groups` (`:310-413`).
- `POST /threats/{c2_threats,total_threats,blocked_request,allowed_request}/data` (`:635-656`).
- `POST /threats/fetch_threats` returns `{data, date, sql}`, **leaking the raw SQL** (`:225-248`).
- `POST /threats/threat_queries_count_daily` (`:251`).
- `POST /threats/get_whois` and `/threats/get_virustotal` take JSON `{"domain"}`, concatenated into third-party URLs without validation (`:194-222`).
- Scan APIs, all POST and login only, keyed by Settings → API Integration:

| Route | File |
|---|---|
| `/threats/virus-total-scan`, `/threats/virus-total-report` | `apis/virus_total.py:10, 40`. TLS verify disabled (`:13-15`). |
| `/threats/sodan_ip_scan` (also GET) | `apis/shodan_scan.py:9`. **Hard-coded `8.8.8.8`** (`:15`). |
| `/threats/sodan_host_scan`, `/threats/sodan_host_lookup` | `apis/shodan_scan.py` |
| `/threats/abuse_ipdb_scan` | `apis/abuse_ipdb_scan.py` |
| `/threats/otx_ip_scan`, `/threats/otx_host_scan` | `apis/alien_vault_otx.py` |
| `/threats/ip_info_scan` | `apis/ip_info_scan.py` |
| `/threats/fraudguard_ip_scan`, `/threats/fraudguard_host_scan` | `apis/fraud_guard.py` |
| `/threats/censys_ip_scan` | `apis/censys_scan.py` |

### SearchForm
`ip_address` Length(3, 25). `risk_category` and `domain_count` enums (`views/discover/forms.py:19-57`).

---

## 8. Reports (`views/report/views.py`)

### Routes
- `GET /reports/`: module `report`, else `401.html` (`:29-41`).
- `POST /reports/data`: `report-read` (`:43-134`).
  - DataTables fields `draw`, `start`, `length`, `date`.
  - `date` is **required** in the format `dd/mm/yyyy-dd/mm/yyyy` (`core/utils.py:174-183`); anything else is a 500.
- `POST /reports/csv_download`: JSON `{"range"}`, **login only** (`:136-214`).
  - Returns a JSON array. Column keys: Timestamp, Country Destination, Threat Type, Source IP, Destination Domain, Destination IP, Tld, Asn, Operator, Risk, Risk Score.
  - Risk Score map: Info 0, Gray/Suspicious 1, Low 2, High 3, Critical 4.
- `GET|POST /reports/pdf_download`: JSON `{"lang","range","type"}`, **login only** (`:220-326`).
  - `type` must be `summary` or `details`; anything else gives KeyError, a 500 (`tasks/report.py:52-59`).
  - Output file: `static/reports/<MonDDYYYY>_<MonDDYYYY>_<type>_report.pdf` (`tasks/report.py:46-50`).
  - Response `{"data": url}`, or `{}` on failure.
  - The file is publicly fetchable as a static asset with a predictable name.
- `report-export` is **never enforced** anywhere.

### Form and other notes
- ReportForm `risk_category` includes `Suspicious`, which no `THREATS_LEVELS` value uses (`views/report/forms.py:14-22`).
- `report_type` choices: `summary`, `csv`.
- JS calls `/reports/download` (`assets/js/script.js:165`), which has no route, so it gets a 302.

---

## 9. Profile (`views/profile/`, prefix `/<lang>/user`)

### Routes
`GET /user/profile`, `PUT /user/update_profile`, `PUT /user/update_password`, all login only (`views/profile/views.py:35, 44, 84`).

### EditProfileForm (`views/profile/forms.py:12-49`)
- `first_name`, `last_name`: DataRequired, Length(3, 30).
- `designation`: DataRequired, Length(2, 150).
- `address`: ≤150. `about`: ≤500.
- `phone`: **DataRequired**, ≤15.
- Email is not editable.
- Photo errors: `'Invalid photo'` / `'No selected file'`.
- Success: `'Profile updated successfully.'`.

### UpdatePasswordForm (`views/profile/forms.py:52-80`)
- Current password: InputRequired, Length(6, 40); wrong value gives `"Incorrect password"`.
- New password: InputRequired, the same complexity Regexp, Length(8, 40).
- Confirm: EqualTo with `"Passwords must match"`.
- Success: `'Password updated successfully.'` (`views/profile/views.py:94`).
- New password equal to the current one is allowed.
- Other sessions are not invalidated.

---

## 10. Settings (`views/setting/views.py`, prefix `/<lang>/settings`)

**Page.** `GET /settings/` is allowed if the user has module `setting` OR `alerttype` OR `emailtemplate`, else `401.html` (`:52-308`).

**Tab visibility** (`templates/setting/partials/alert_settings.html`):
- General and Alert Settings need `setting-update`.
- Alert List needs module `alerttype`.
- Email Template needs module `emailtemplate`.
- API Integration, Help & Support, User Log and System Log are always shown.
- Update System, Housekeeping, DGA Config and Detect DNS Bypass tabs are **commented out** (`:106-162`). The `detect_dns` pane is still included (`:172`).

**Side effect of the page itself.** GET `/settings/` lazily inserts default rows for `mitaka`, `dga`, `dns_bypass` (`Disable`), `housekeeping` (`period 1`), `threat_categories`, `response_policy` and `syslog_settings` (`:70, 108, 125, 162, 186-201, 229-234, 275`). It also calls `https://ifconfig.me` on every load (`:209`).

### a. General tab
- `POST /settings/general-settings` (module `setting`; `:321-342`).
  - `default_lang` choices are **`en` and `ja` only**; `default_timezone` must be in `pytz.common_timezones` (`views/setting/forms.py:87-95`).
  - Success `{"status":"Ok"}`.
  - The language change applies to the in-process `BABEL_DEFAULT_LOCALE` immediately (`:337-338`). The timezone is only read at app start (`core/app.py:79-82`).
- `POST /settings/general-bind-settings-policy` (RPZ Zone Settings; module `setting`; `:345-436`).
  - JSON body: `{"threats_categories": {"<Category_with_underscores>": "alert"|"block"|"off"}}`.
  - Values are **not validated** against that enum.
  - It re-validates the stored `dns_allowed_networks` with `validators.ipv4(net, host_bit=False)` or ipv6, **without** a `localhost` exception. Invalid gives **400** `{"errors":"Invalid organization network address found. "}`.
  - Then it writes `named.conf` and returns `{"status":"Ok"}`, or `{"errors":"Something is wrong in bind configuration please contact administrator First. "}`, or on exception `"…administrator. "`.
  - UI fixes `User_Allowed=alert` and `User_Blocked=block` via disabled options (`templates/setting/partials/alert_settings/general.html:92-139`).
  - The confirm dialog text is `"You are about to save the rpz settings. This may affect the system. Do you want to proceed?"`.
- `POST /settings/general-bind-settings-ip` (Access Control; `:439-526`).
  - JSON `{"dns_allowed_networks": "<Tagify JSON string [{\"value\":…}]>"}`.
  - Each entry must be ipv4 with `host_bit=False`, or ipv6, or `localhost`. Otherwise the 200 response is `{"errors":"Invalid organization network address found. ","data":[…],"invalid_network":…}`.
  - An empty list gives `"Invalid organization network found."`.
  - A missing policy row gives `"Something is wrong. Default policy information is missing. "`.
  - A bind write PermissionError gives `"Something is wrong in domain name server restart. Please contact administrator."` (`core/exceptions.py`).
  - Inconsistency: `localhost` is accepted here but later makes the RPZ policy save return 400.
  - listen-on is fixed to `any`.
- Default networks shown on the page: `"127.0.0.1/32, <public ip>/32"` (`:224-226`).

### b. Alerts
- `POST /settings/alert-settings` (module `setting`; `:529-566`).
  - `email` uses `validate_multiple_emails`, which accepts JSON (Tagify) or CSV. Invalid gives `"Invalid email address(es): a, b"` (`views/setting/forms.py:54-70`).
  - The view then runs `json.loads(email)`, so **CSV input passes validation but crashes (500)** (`:541`).
  - An empty value is allowed.
  - `is_notification_bell_enable` is an optional bool. The UI also mirrors it to localStorage `notification_sound`.
- Alert List (`AlertCategoryForm`, `views/setting/forms.py:149-170`):
  - `alert_name`: DataRequired, no length limit, but the DB column is `String(30)` (`models/setting.py:40`).
  - `alert_period` choices: `52560000` (No Alert), `5`, `15`, `30`, `60`, `120`, `1440` minutes.
  - `email_template`: coerce=int, choices from DB, so a template must exist.
  - `categories`: multi, values are comma-joined zone lists. Zero categories is allowed. Names are not unique.
  - Routes:
    - `POST /settings/alert-types` (`alerttype-create`; `:568-665`) → `'AlertType created successfully.'`
    - `GET|PUT|DELETE /settings/alert-types/<id>` (`:667-789`) → `'Alert Type updated successfully'` / `"Deleted"`. An unknown id is a 500.

### c. API integration
- `POST /settings/mitaka-settings` (module `setting`; `:981-1007`).
- All fields optional: `virusTotalapiKey`, `abuseIPDBapiKey`, `iPinfoapiKey`, `ShodanapiKey`, `FraudGuardUsername`, `FraudGuardPassword`, `AlienVaultOtxKey`.
- Stored in plaintext. The FraudGuard password is re-rendered into the page (`PasswordInput(hide_value=False)`, `views/setting/forms.py:209-211`).

### d. Email templates
- `EmailTemplateForm`: `template_name` DataRequired, DB `String(30)`. `content` is CKEditor with no validator and no sanitisation (`views/setting/forms.py:172-177`).
- Routes:
  - `POST /settings/email-templates` (`emailtemplate-create`; `:791-872`) → `'Email Template created successfully.'`
  - `GET|PUT|DELETE /settings/email-templates/<id>` (`:874-978`) → `'Email template updated successfully.'` / `"DELETED"`.
- Deleting a template that an alert type references probably fails on the FK and returns 400 `{"error": …}`.
- Placeholders replaced in the mail body: `RECIPIENT`, `BLOCKCOUNT`, `BLOCKDETAILS` (`tasks/celery_worker.py:236-238`).

### e. Logs and support
- Help & Support:
  - `GET|POST /settings/send-diagnostics`, **login only** (`:1104-1181`).
  - Returns `/tmp/threatidr-diagnostics-<date>.bin` (tar.gz) containing the grok log, the grok config with the `database` and `redis` sections stripped, the nginx error log, **`/var/log/messages`**, `bind.log`, `rpz.log`, the redis log and `machine-status.txt`.
- User Log:
  - Built from `WhitelistBlocklistHistory` ordered by `action_time` descending. A deleted user is shown as `"Deleted User"` (`:144-155`).
  - DataTable `pageLength: 20`, ordered by column 4 descending (`templates/setting/partials/alert_settings/logs.html` ~`:177-181`).
  - The Event Type cell renders as `User {{event_type|capitalize}}`.

### f. Syslog and DNS bypass
- `POST /settings/syslog-settings` (module `setting`; `:1217-1272`). JSON body `{ip, port, is_forwarding, socktype}`.
  - Missing `ip` or `is_forwarding` gives 409 `[{"errors":"Invalid Input"},400]`.
  - A falsy port becomes 514. The port must be an int in `range(65536)`, so **0 is accepted** and a string port gives `"Invalid Port"`.
  - The IP must be ipv4 or ipv6, else `"Invalid IP, Enter a Valid IP"`.
  - Connectivity test: TCP connect or UDP send of `b"UDP connection test"`, 5 s timeout. Failures:
    - `"Connection refused when trying to connect to destination IP."`
    - `"Connection timed out when trying to connect to destination IP."`
    - `"Unable to connect to the syslog remote server."`
    - All return 409.
  - A missing `socktype` key is a 500 `[{"errors":"Error processing request."},500]`.
  - **Updating an existing row broadcasts a Celery `shutdown`** (`:1256-1259`).
  - Success `{"status":"Ok"}` with 200; the UI shows `"Configuration set successfully"`.
- `GET|POST /settings/remove-syslog-info`, **login only, deletes on GET** (`:310-319`).
- `POST /settings/dns-bypass` (module `setting`; `:1030-1055`).
  - JSON `{"status":"Enable"}`; any other value means Disable.
  - Rewrites the bypass zone, reloads it, returns `{"status":"Ok"}`.
  - GET returns `{"errors":"Invalid Settings"}`.

### g. Update system
The tab is disabled. The routes are live and login only (`views/home/views.py:455-622`).
- `POST /<lang>/update` (multipart field `file`):
  - Only `.zip` (case-insensitive). Size ≤ 7 MiB.
  - Messages: `'No file part'`, `'No selected file'`, `'Invalid file format. Please upload a file in the correct format.'`, `'File is empty or invalid. Please upload a valid file.'`, `'File uploaded successfully.'`.
  - Redis lock `patch_upload_lock` with `ex=600`, message `'Another upload is in progress'`. The lock is released only on error paths, so after a successful upload new uploads are refused for 10 minutes.
- `POST /<lang>/apply_update/<zip>`:
  - Extracts the zip and requires a top-level directory starting with `tidr`.
  - Requires **exactly one `.sh`** and executes it with `bash` (`core/utils.py:817-842`).
  - Then checks 8 systemd services.
  - Returns `{"success":"Successfully applied patch.","redirect_url"}` or `{"errors":…}`.
- `GET|POST /<lang>/service_status` returns the service-alert email HTML.
- `GET|POST /settings/system-update` returns None, so it 500s (`:1184-1187`).

### Housekeeping
- Settings route: `POST /settings/housekeeping` does not exist. The real route is `POST /<lang>/backup_and_restore/housekeeping` (module `setting`; `views/backup_and_restore/views.py:57-74`).
- `period` choices: `'1'` "1 Month", **`'3'` labelled "2 Month"**, `'6'`, `'12'`, `'100'` "Test" (1 hour) (`views/setting/forms.py:179-189`).

---

## 11. History of IP (`views/history_of_ip/views.py`)

- `GET|POST /<lang>/history_of_ip/`, module `user` (`:63-144`).
- Renders `history_of_ip/index.html`, but only `templates/_history_of_ip/` exists, so expect a 500. The sidebar link is commented out.
- GET picks the IP with the most hits; on an empty DB `fetchone()` returns None, giving a TypeError (500).
- POST field `ip_address` must parse with `ipaddress.ip_network` or `validators.domain`, else flashes `'IP address or Domain is invalid'`.
- The validated input is then `.format()`ed into SQL (`:91-118`).
- `POST /history_of_ip/search` returns `0`, a 500 (`:146-148`).

---

## 12. Site risk indicator (`views/site_risk_indicator/views.py`)

- `GET /<lang>/site_risk_indicator/`, module `user`. Renders `srisk_domain/index.html`, but only `_srisk_domain` exists, so expect a 500 (`:85-100`).
- `POST /site_risk_indicator/search`, `user-create` (`:102-124`).
  - `domain` DataRequired, Length ≤ 255, `validators.domain`. An invalid domain returns `{"errors":{}}` with no message (`views/site_risk_indicator/forms.py:20-39`).
  - Calls `SITE_RISK_SERVER_URL/<info>` for each of `sslinfo`, `whoisinfo`, `techinfo`, `ipinfo`, `github`, `darkweb`, `telegramset`, `telegramget`, `hbpwned` (`:48`).
  - Returns `{status, srisk_info1 (str), srisk_info, message:'Site risk of domain is available.'}`. Per-source failure gives `{"Error":"Something went wrong"}`.
- `views1.py` defines a `srisk_domains` blueprint that is not registered.

---

## 13. Audit / access log and alerts

- The `access_log` and `alert` blueprints are **not registered** (`core/app.py:40-44, 186-203`), so their URLs get a 302.
- Nothing writes `AccessLog` or `Alert` anywhere, so no audit trail of logins exists.
- The only audit data is the whitelist/blocklist history (§4, §10e).

---

## 14. Backup and restore (`views/backup_and_restore/views.py`)

- `GET /<lang>/backup_and_restore/`: module `backup`. Renders `backup_and_restore/index.html`, but only `_backup_and_restore` exists, so expect a 500 (`:34-55`).
- `POST /backup_and_restore/housekeeping`: module `setting` (`:57-74`).
- `GET /backup_and_restore/restore_snapshot_list` is **unauthenticated** and returns 5 hard-coded fake snapshots (`:76-106`).
- No backup or restore logic exists.

---

## 15. License

- `/<lang>/license` (GET, POST; login; `views/home/views.py:206-235`).
  - If a License row exists, POSTs to `<LM_URL>/validate_license/tidr/` with **no timeout**.
  - If the response is valid, redirects to the dashboard; otherwise renders `home/license.html`.
- `/<lang>/save-license` (GET, POST; login; `:238-295`).
  - If there is no row, POSTs to `generate_license` with `timeout=5`. Outcomes:
    - Success: plain text `'License update sucessfully'`.
    - Error from the server: 404 `{"msg":…}`.
    - `HTTPError`: 500 `"HTTP error occurred: …"`.
    - Any other error: 404 `"Other error occurred: …"`.
  - If a row exists, returns 200 JSON including the **license key**.
  - It changes state on GET.

---

## 16. JSON API for integrations (`views/api/views.py`, CSRF-exempt)

### `POST /<lang>/api/login` (`:27-62`)
- Requires `Content-Type: application/json` exactly, else 401 `"Please provide correct content type"`.
- Body `{email, password}`.
- Missing field, unknown email and wrong password all return the same 401 `"Please provide email and password"`. There is no enumeration here.
- **It does not check `is_active`.**
- There is no rate limit.
- Returns `{"token","message":"Login successful"}`. The token is HS256 JWT `{email, id, exp: +3 days}` signed with `SECRET_KEY`.

### `POST /<lang>/api/query/data` (`:65-204`)
Auth is `@token_required` (`core/utils.py:312-357`). The **raw JWT goes in the `Authorization` header with no `Bearer ` prefix.**

| Auth failure | Status | `message` |
|---|---|---|
| Missing token | 401 | `"Authentication Token is missing!"` |
| User not found, no `exp`, or expired | 401 | `"Invalid Authentication Token"` |
| Decode error | 401 | `"Token is invalid"` |

All of them carry `"error":"Unauthorized"`.

Body:
- `start-date`, `end-date` (required, `%Y-%m-%d %H:%M:%S`)
- `page` (default 1; 0 is treated as 1; negative is an error)
- `limit` (default 10000; 0 is treated as 1)
- `log_type` ∈ `Full`, `Query`, `Threat`
- `source_ip`, `destination_ip`, `domain_or_subdomain`
- `threat_type` (a key of `THREATS_CATEGORIES`)

Validation messages, all returned with **HTTP 200**:
- `"Please provide start-date and end-date"`
- `"Please provide valid log type. Allowed log types are: Full, Query and Threat."`
- `"Please provide start-date and end-date with correct format. E.g: 2023-09-05 11:20:16"`
- `"Please provide page number with correct format. It must be a number greater than 0."`
- `"Please provide limit with correct format. It must be a number greater than 0."`
- `"Please provide valid threat type"`
- `"Please provide correct content type"`
- `"No data is available for given parameters."`

Success: `{page, number_of_pages, dns_log_list:[{query, timestamp, source_ip, destination_ip, destination_domain, tld, cc_tld_name, geoip, geoip_city, query_type, threat_type}]}`.

**SQL injection:** `source_ip`, `destination_ip` and `domain_or_subdomain` are f-stringed into SQL (`:130-137`).

---

## 17. Background and async behaviour

### Celery beat (`core/celeryconfig.py:72-124`)

| Task | Schedule | What a test can observe |
|---|---|---|
| `alert_notification_server` | every 1 min | Socket.IO `/notifysocket` event `notify` with `{html[], total (unviewed threats), id[], is_notification_bell_enable}`. Also forwards syslog for threats and dnslogs ingested in the last minute when `is_forwarding` is on (`tasks/celery_worker.py:633-851`). |
| `alert_updated_log_time` | every 1 min | `/notifyUpdateLogTime` event `notifyUpdateLogTime` carrying the last `ingested_at` (`:860-903`). |
| `send_email_alerts` | every 5 min | For each AlertType with period ≠ 52560000, threats in `[now-period, now]` produce a OneSignal email. Subject: `"Pipeline Security: You've Got security Alert Notifications"`. Recipients: the alert config emails split on `,` (`:263-351`). Because the window is longer than the 5-minute cadence, any period above 5 likely **re-sends the same threats**. |
| `system_status_monitor` | every 10 min | `/system` event `system_status`. Down and recovery emails are sent only on state transitions: subjects `URGENT: <name> Service Down Alert` and `INFO: <name> Service Recovered`. State is stored in Redis `service_state:<name>` with ex 86400 (`:418-631`). |
| `housekeeping` | daily 00:00 | Deletes `dnslogs` older than `<period> months`, or 1 hour when the value is `100`. It also runs `drop_chunks` and refreshes the aggregates. It does **not** prune `threats` (`:905-973`). |

- Redis locks prevent overlapping runs (`acquire_lock`).
- Socket.IO uses `cors_allowed_origins="*"` (`core/app.py:147-152`). The `authenticated_only` decorator exists (`tasks/celery_worker.py:75-82`) but is never applied, and there are no `@socketio.on` handlers.

### Synchronous side effects tied to UI actions
- Apply Changes writes the zone and runs `rndc reload`.
- RPZ and Access Control saves write `/etc/named.conf` and run `rndc reconfig`.
- DNS bypass writes its zone and reloads it.
- Visiting `/threats/` marks all threats viewed.
- Saving syslog settings shuts down the Celery workers.

---

## 18. Bugs and security gaps worth a negative test

Each is one line plus a ref.

**Authentication and session**
1. Login enumeration via the error label: `Email - Wrong credentials` vs `Password - Wrong credentials` (`views/home/forms.py:31,35`, `core/utils.py:43`).
2. Reset enumeration: `"This user does not exist."` appears only for unknown emails (`views/home/views.py:159`).
3. Forgeable, reusable, unsigned reset token with a 300 s window (`models/user.py:62-74`, `core/utils.py:135-158`).
4. Open redirect via `?next=` for an authenticated GET on `/` (`views/home/views.py:95`).
5. The login rate-limit key is the proxy IP with no ProxyFix, so there is one shared bucket. There is no lockout (`core/extensions.py:33`).
6. API login ignores `is_active` and has no rate limit (`views/api/views.py:43-57`).

**Authorization**

7. Admin role-guard bypass, plus user 1 can be deactivated, self-delete is allowed, and role 1 can be deleted (`views/user/forms.py:116-120`, `views/user/views.py:362-378, 446-455, 623-626`).
8. Any logged-in user can:
   - upload and run a patch script (`views/home/views.py:455-580`)
   - download diagnostics (`views/setting/views.py:1104`)
   - remove syslog config via GET (`:310`)
   - export CSV/PDF (`views/report/views.py:136, 220`)
   - trigger the DGA job (`views/discover/views.py:863`)
9. Unauthenticated: threat chart/tooltip data, `restore_snapshot_list`, the `/syslog` trigger, and generated PDFs in `static/reports/`.
10. "Unauthorized" responses return HTTP 200 with an array body, and the domain delete UI shows success anyway.

**SQL injection**

11. API filters (`views/api/views.py:130-137`), DGA `filter_by_date` (`views/dga_domain/views.py:59`), legacy dashboard routes and `daily-threat-detection` (§2).

**XSS and content**

12. Unescaped user and role names in HTML fragments (`views/user/views.py:150, 284-308`).
13. Email template HTML is stored raw.

**Information leaks and secrets**

14. Raw SQL leaked by `/threats/fetch_threats` (`views/discover/views.py:248`).
15. Hard-coded ML secret (`:879`).
16. The FraudGuard password is echoed into the page (`views/setting/forms.py:209-211`).
17. The license key is returned by `/save-license` (`views/home/views.py:286-295`).

**Data correctness**

18. C2 counts omit `botnetcc.host.ppln`.
19. Top-ten ordering is ascending by time, not by count.
20. `blocked_threat` equals `total_threat`.
21. Housekeeping `'3'` is labelled "2 Month".
22. Report `Suspicious` risk never matches anything.
23. The Shodan IP is hard-coded.

**Crashes (500)**

24. TemplateNotFound on DGA, History of IP, Site Risk and Backup.
25. `whitelist_blocklist_history` jsonify of a Query.
26. `/settings/system-update` returns None.
27. `/syslog` with no threats.
28. Alert settings with CSV email.
29. Missing `socktype` in syslog settings.
30. Role rename to an existing name (IntegrityError).
31. Edit-user phone of 16 to 20 characters.
32. Missing or bad DataTables `draw` or `date`.

**Validation inconsistencies**

33. Name minimum 2 on register vs 3 on edit and profile.
34. Designation optional on register, required on edit.
35. `localhost` accepted in Access Control but rejected by RPZ save (400).
36. Syslog port 0 accepted.
37. General language list omits `bn`.
38. Length limits on AlertType and EmailTemplate names are only enforced by the DB (`String(30)`).

**State and side effects**

39. Domain delete is not reflected in DNS until Apply.
40. Apply always reports success because the rndc exit code is ignored.
41. Saving syslog shuts down Celery.
42. A successful patch upload holds the lock for 10 minutes.
43. Email alerts likely repeat for any period above 5 minutes.
44. DNS-bypass and DGA zones are never declared in `named.conf`, so they are likely ineffective (unclear).
45. The license middleware covers only `/en/` paths.

---

### Unclear items (not determinable from code alone)
- `validators==0.35.0` behaviour for a trailing dot, wildcards, IDN, underscores and single-label domains.
- Whether nginx sets `X-Forwarded-For` or `X-Real-IP` in deployment.
- Whether FK violations on role delete or email-template delete actually occur; this depends on the migrations.
- The real effect of BIND zones not referenced in `named.conf`.
- The template folders with a leading underscore may be intentionally disabled features.
