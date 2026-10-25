# UI feature conventions (ThreatIDR)

Authority for business rules: `docs/threatidr-source-facts.md` (from the app source, with
`file:line` refs) and `docs/test-coverage-proposal.md`. Plan mapping:
`../ThreatIDR_Automation_Coverage.xlsx`.

## Tags that control whether a scenario runs

| Tag | Meaning | Default run |
|---|---|---|
| `@wip` | written, step glue not finished yet | excluded |
| `@known-defect` | asserts the **correct** behaviour; fails on build 0.0.5 because of a confirmed product defect named in the scenario comment. Title starts with `BUG -` | excluded |
| `@blocked-env` | needs something this environment does not have (comment says what: valid license key, patch zip, syslog listener, container rebuild, ...) | excluded |
| `@out-of-scope` | decided 2026-10-18 not to automate (forgot password, REST API, Backup & Restore, API-key integration) | excluded |

Scenarios with no glue also carry `@wip`, including `@blocked-env`, `@out-of-scope` and `@known-defect` ones, so `not @wip` always means "fully glued".

Defect report run:
`mvn clean test -Dsuite=testng.xml -Dcucumber.filter.tags="@known-defect and not @wip and not @blocked-env and not @out-of-scope"`

## Tags that control how a scenario runs

| Tag | Effect |
|---|---|
| `@serial-auth` | submits the real login form; serialised through `LoginRateLimitGuard` (3 logins / 61 s) |
| `@login-window` | with `@serial-auth`: needs the whole rate-limit window to itself |
| `@serial-settings` | changes global app state (language, timezone, RPZ actions, Access Control, DNS bypass, syslog, alert feed). Serialised against each other and restores the original value afterwards |
| `@serial-users` | (Users and Roles features, and every scenario that creates or deletes a user while running) never overlaps another `@serial-users` scenario: the Users page answers HTTP 500 while another request creates or deletes a user (it indexes `user.roles[0]` while lazily loading roles). See `SerialUsersHooks` |
| `@destructive` | touches the only admin account; safe only because the app is expected to refuse |
| `@custom-dns` | end-to-end blocking of a real domain (google.com) checked from a fresh browser. Needs the test host's DNS set to ThreatIDR's bind9 (`nameserver 127.0.0.1`) by hand. Blocks/unblocks can take up to ~60 s to be enforced after Apply (BIND RPZ `min-update-interval`); the steps wait up to 75 s and attach the measured delay. Serial with every `@serial-settings` scenario |

Lock order when tags combine: `@serial-users`, then `@serial-settings`, then `@serial-auth`.
For `@serial-settings`, read the original value before changing it and restore it in cleanup:
`ctx.restoreAfterwards(() -> readCurrent(), original -> restore(original))`.

## Limited users

`Given I am logged in as a user with only the "<permission label>" permission` uses one role +
one user per permission set (`FixtureUsers`): role `qa-auto-<key>`, user
`qa-auto-<key>@example.com`, created as admin over HTTP the first time and then **kept and reused
by later runs** (password in the git-ignored `.auth-state/<env>/fixture-users.properties`; delete a
line there to have that user recreated). Logged in once through the guard, session cached by
`StorageStateManager.forCredentials`. Never create one per scenario: each real login costs a
rate-limit slot. Labels are the Roles form's (`PermissionCatalog`, 15 keys).

The step replaces whatever session the scenario already has (a feature `Background` usually logs
in as admin first), so it must swap the browser context's storage state, not add to it. Variant:
`Given I am logged in as a user holding every grantable permission` (one more fixture user).
Profile-editing scenarios also run as the "Read Reports" fixture user (never the admin, whose NULL
designation could not be restored) and are `@serial-settings` because they share its profile.

`Given I am logged in as a disposable user` creates a throw-away account (Read Reports role),
logs it in once through the form (tag the scenario `@serial-auth`) and deletes it in cleanup.
Preconditions and cleanups that need admin rights use `ctx.admin()` (`AdminHttp`, the admin's
saved session over HTTP), independent of whichever account the browser is logged in as.

## Shared step vocabulary (one Java method per phrase)

| Purpose | Phrase |
|---|---|
| open a page while logged in | `When I navigate directly to "<path>"` |
| open a page with no session (browser) | `When I navigate directly to "<path>" without logging in` |
| raw request with no session (redirects not followed) | `When I call "<path>" without an active session` |
| denial page (401.html, HTTP 200) | `Then I should see an access-denied page` / `Then I should not see an access-denied page` |
| full page load is not a 500 | `Then the page should load without a server error` |
| an AJAX save / action is not a 500 | `Then the request should not cause a server error` |
| anonymous request bounced to login | `Then the anonymous response should be a redirect to the login page` |
| settings tabs | `Given I am on the "<tab label>" settings tab` (General Settings, Alert Settings, Alert List, Email Template, API Integration, Help & Support, User Log, System Log) |
| sidebar menu entries | `Then the sidebar should (not )show the menu label "<label>"` |
| toast / dialog text (or a flashed `.alert[role=alert]`) | `Then I should see the message "<text>"`; static page text: `Then I should see the text "<text>"` |
| domain form errors | `Then I should see the domain error "<text>"` |
| User Log rows | `Then the user log should contain:` table with `domain`, `event type`, `action` |
| domain preconditions | `Given "<domain>" is already a blocked domain` / `... an allowed domain` (admin over HTTP, cleaned up) |
| RPZ zone content | `Then the RPZ zone "<zone>" should map "<domain>" to "<target>"` / `... should have no rule for "<domain>"` (bind9 CNAME of `<domain>.<zone>`) |
| limited / disposable sessions | `Given I am logged in as a user with only the "<label>" permission`, `Given I am logged in as a user holding every grantable permission`, `Given I am logged in as a disposable user` |
| buttons, static text | `Then I should see the "<label>" button`; click anything labelled: `When I click "<label>"` |
| field errors of the form just submitted | `Then I should see a required-field error for "<field>"` / `Then I should see a validation error for "<field>"` (the submitting step registers its form's errors with `ctx.useFieldErrors`) |
| a request a step sent itself | `Then the delete request should be answered with HTTP status <n>` (reads `ctx.lastHttp()`) |
| a request a step sent itself was refused | `Then the request should be denied` (401/403, a bounce to the login form, the 401.html page, or the app's HTTP-200 `[{"errors":"Unauthorized"},401]`; a 200 with data is not a denial) |
| URL checks | `Then the URL should start with "<path prefix>"`, `Then the URL should contain the query parameter "<name>" with value "<decoded value>"` |
| page title | `Then the browser title should end with "<text>"` |
| per-browser language (URL segment, never the global default) | `When I switch the language to "English\|Japanese"` (login page or header dropdown) |
| reports | `When I generate a "<type>" PDF report for the last <n> days in language "<en\|ja>"` (stores `{generatedPdfPath}`), `When I generate a CSV report for the last <n> days` |

A step that only prepares an action triggered later by the generic `When I click "<label>"` (for
example `I choose the "CSV" report format` before `I click "Generate"`) registers the expected call
with `ctx.expectPendingCall(...)`; `Then the request should not cause a server error` waits for it
first, so it never judges before the answer arrived.

Steps that drive an AJAX save capture its response with `CapturedCall` (route + fetch, so the body
survives the page's `location.reload()`), and assert on the server's answer as well as the UI.

Values stored by a step (for example `{newestThreatDomain}`, `{generatedPdfPath}`) are read back
through the same `{name}` placeholder syntax as `{uniqueString}`.
