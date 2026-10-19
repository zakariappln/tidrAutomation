# Undefined UI step phrases (work list for the step writers)

Generated 2026-10-19 from the Cucumber dry-run
`mvn -o -q test -Dsuite=testng.xml -Dcucumber.execution.dry-run=true -Dcucumber.filter.tags="not @nothing"`
(Tests run: 251, Failures: 221 - every failure is an undefined step; 0 ambiguous).

**Batch 1 update (2026-10-19): now defined, reuse them:** `I am logged in as a user with only the {string}
permission`, `I am logged in as a user holding every grantable permission`, `I am logged in as a disposable
user`, `I navigate directly to {string}`, `I should see an access-denied page`, `I should not see an
access-denied page`, `the page should load without a server error`, `the request should not cause a server
error`, `the sidebar should show the menu label {string}`, `the sidebar should not show the menu label
{string}`, `I should see the message {string}`, `I should see the text {string}`, `I should see the {string}
button`, `I should see a required-field error for {string}`, `I should see a validation error for {string}`,
`the anonymous response should be a redirect to the login page`, `{string} is already a blocked domain`,
`I reload the blocked domains page`, `I send a request to delete the blocked domain {string}`, `the delete
request should be answered with HTTP status {int}`, `the blocked domain {string} should have no delete
control`, plus every auth/license/users/roles/profile phrase except password reset and `I send a request
to change user {int}'s role using that user's email in the form` (deliberately unglued: it would rewrite the
admin account). Infrastructure: `@serial-settings` / `@serial-users` hooks, `ctx.restoreAfterwards(...)`,
`ctx.admin()` (AdminHttp), `CapturedCall`, `ctx.useFieldErrors(...)`.

**Batch 2 update (2026-10-19): now defined, reuse them:** `I am on the {string} settings tab` (opens
Settings and selects the tab), `the user log should contain:` (rows looked up by domain through the User Log
DataTable's search), `I should see the domain error {string}`, `I should not see the domain error {string}`,
`the RPZ zone {string} should map {string} to {string}`, `the RPZ zone {string} should have no rule for
{string}` (both query bind9 for `<domain>.<zone>` CNAME with `DnsClient.queryCname`), `{string} is already an
allowed domain`, `I delete the blocked|allowed domain {string}`, `{string} should no longer appear in the
blocked|allowed domains list`, `I reload the allowed domains page`, the pager/order/length phrases of both
domain lists, `I add and then delete the blocked|allowed domain {string}`, `I should not be on the login
page`, `the anonymous response body should match {string}` / `should not contain {string}`, the block-page
and access-log phrases. Infrastructure: `DomainLists` (UI add/delete with captured answer, HTTP
preconditions, cleanup by id; re-applies the RPZ zone in cleanup only in `@serial-settings` scenarios),
`AdminHttp.createDomain/deleteDomain/applyDomains/newestThreat`, `SettingsPage` (tabs, User Log),
`DomainTableComponent`.

**Batch 3 update (2026-10-19): now defined, reuse them:** `the request should be denied` (reads
`ctx.lastHttp()`; accepts every refusal shape the app uses, see PageAssertionSteps), `the URL should start
with {string}`, `the URL should contain the query parameter {string} with value {string}`, `the browser title
should end with {string}`, `I switch the language to {string}`, `I reload the dashboard`, `my profile
designation is set to {string}` (fixture user only, restored), every dashboard / threat-detection / reports
phrase of those three folders except `I search the threats table for {string}` (no search box on the page)
and the Discover / FraudGuard / empty-dialog phrases (`@out-of-scope` / `@blocked-env`), and the two
localization phrases. Infrastructure: `ctx.expectPendingCall(...)` / `ctx.awaitPendingCalls()` (outcome
waits for a call a generic click triggers), `CapturedCall.await(timeoutMs)` and a 120 s fetch timeout in
`CapturedCall`, `LanguageSwitcherComponent`, `LoginPage` locators by id (works under `/ja/`), PDFBox 2.0.22
(test scope) for PDF text.

**Batch 4 update (2026-10-19): now defined, reuse them:** every phrase of settings/general-settings
(language/timezone, RPZ fixed actions, `I add {string} to {string}` (Access Control only), `I remove every
network from {string}`, `I save Access Control`, `I should see the access control error {string}`),
settings/alerts (alert settings, alert types, the bell), settings/email-templates, settings/logs-and-support,
settings/syslog-and-dns-bypass (`a DNS lookup for {string} should not return NXDOMAIN` sits next to the existing
NXDOMAIN step), settings/update-system (rejection paths and `apply_update`), dga-domains and history-of-ip.
Deliberately NOT glued: RPZ saves and successful Access Control saves (`I set the {string} RPZ action to
{string}`, `I save the RPZ zone settings and confirm the prompt`, `I add {string} and {string} to {string}`,
`I remove {string} from {string}`, ...): they rewrite bind9's named.conf and would lock the test host out of DNS
(see the feature comment). Infrastructure: `CapturedCall.guarded(...)` (inspects a request body and aborts it
before it reaches the server: Access Control and syslog saves that could succeed), the generic `I click` now picks
the visible match (Settings renders "Save" in every tab pane), `NotificationFeed` (bell socket pushes),
`TarGzArchive` (in-memory diagnostics bundle), `SettingsFixtures` (alert types / email templates over HTTP, cleanup
by id), settings tab components under `ui/pages/settings/`, `AdminHttp` settings endpoints.

- 277 distinct step expressions are undefined (taken from the snippets Cucumber prints, so
  quoted values are `{string}` and numbers `{int}`). Each is listed once, under the first feature folder
  (alphabetical) that uses it; other folders that use it are named in brackets.
- The dry-run "The step '...'" lines (first undefined step per scenario, literal values) give 138 distinct entries.
- Phrases already glued are not listed; reuse them (see `features/ui/README.md`, "Shared step vocabulary").
- Scenarios tagged `@out-of-scope` or `@blocked-env` are included for completeness; implement them last.

## audit

- `I add and then delete the allowed domain {string}`
- `I add and then delete the blocked domain {string}`
- `I am logged in as a user with only the {string} permission` (also: authorization, dashboard, profile, reports, settings, site-risk-indicator)
- `I am on the {string} settings tab` (also: settings)
- `I filter the access log by the last {int} days and the client IP {string}`
- `I navigate directly to {string}` (also: authorization, backup-restore, dga-domains, history-of-ip, localization, site-risk-indicator)
- `I should see an access-denied page` (also: authorization, dga-domains, site-risk-indicator)
- `every row shown should have client IP {string}`
- `the access log page should be shown, not a redirect to the dashboard`
- `the page should load without a server error` (also: authorization, dga-domains, history-of-ip, localization, settings, site-risk-indicator)
- `the table should group rows by client IP with hit counts`
- `the user log should contain:` (also: domains, settings)

## auth

- `I am on the license page`
- `I have a password-reset link that was already used once`
- `I have a valid password-reset link`
- `I log in as that inactive user`
- `I request a password reset for {string}`
- `I set my new password to {string}`
- `I should be able to log in with the new password`
- `I should see a generic password-reset confirmation message`
- `I should see a password complexity error` (also: users)
- `I should see an invalid-or-expired-token error`
- `I should see the message {string}` (also: history-of-ip, threat-detection)
- `I should see the text {string}` (also: reports, settings)
- `I should see the {string} button`
- `an inactive user exists`
- `my password should be changed successfully`
- `the dashboard license info should be {string}`
- `the dashboard license info should not be {string}`
- `the login form should not be submitted`
- `the {string} field should be flagged as required`

## authorization

- `I reload the blocked domains page` (also: domains)
- `I send a request to delete the blocked domain {string}`
- `I should not see an access-denied page`
- `the blocked domain {string} should have no delete control`
- `the delete request should be answered with HTTP status {int}`
- `the sidebar should not show the menu label {string}`
- `the sidebar should show the menu label {string}` (also: localization)
- `{string} is already a blocked domain` (also: domains, settings)

## backup-restore

- `the anonymous response should be a redirect to the login page` (also: public-routes, reports)
- `the housekeeping period options should not include {string}`

## dashboard

- `I choose a custom dashboard range entirely in the future`
- `I choose {string} from the dashboard Filter menu`
- `I click the {string} tile`
- `I reload the dashboard`
- `my profile designation is set to {string}`
- `the ThreatIDR logo should be visible in the sidebar`
- `the URL should contain the query parameter {string} with value {string}`
- `the browser title should end with {string}`
- `the dashboard widget requests should be sent with range {string}`
- `the footer should show {string}`
- `the header should show the designation {string}`
- `the header user block should not contain the text {string}`
- `the sidebar should show the ThreatIDR version {string}`
- `the sidebar should show the {string} with an IPv4 value`
- `the {string} detail dialog should open with a table`
- `the {string} panel should show {string}`
- `the {string} percentage change should match the {string} values from dashboard-statistics`
- `the {string} time should be shown`

## dga-domains

- `I am logged in as a user holding every grantable permission`
- `I should see the DGA-predicted domains list`

## domains

- `I add a blocked domain that is {int} characters long`
- `I add an allowed domain that is {int} characters long`
- `I delete the allowed domain {string}`
- `I delete the blocked domain {string}`
- `I go to page {int} of the allowed domains list`
- `I go to page {int} of the blocked domains list`
- `I reload the allowed domains page`
- `I should not see the domain error {string}`
- `I should see the domain error {string}`
- `I type a {int}-character value into the allowed domain field`
- `I type a {int}-character value into the blocked domain field`
- `more than {int} allowed domains exist`
- `more than {int} blocked domains exist`
- `the RPZ zone {string} should have no rule for {string}`
- `the RPZ zone {string} should map {string} to {string}`
- `the allowed domain field should hold at most {int} characters`
- `the allowed domains list should be ordered by {string} newest first`
- `the allowed domains list should show {int} rows`
- `the allowed domains pager should show page {int} as current`
- `the blocked domain field should hold at most {int} characters`
- `the blocked domains list should be ordered by {string} newest first`
- `the blocked domains list should show {int} rows`
- `the blocked domains pager should show page {int} as current`
- `{string} is already an allowed domain`
- `{string} should no longer appear in the allowed domains list`
- `{string} should no longer appear in the blocked domains list`

## history-of-ip

- `I look up {string} on the history of IP page`
- `history for the single highest-hit source IP should be shown`
- `the history table should be scoped to {string}`

## localization

- `I generate a {string} PDF report for the last {int} days in language {string}` (also: reports)
- `I switch the language to {string}`
- `the PDF should contain Japanese characters, not missing-glyph boxes`
- `the URL should start with {string}`

## profile

- `I am logged in as a disposable user`
- `I change my password from {string} to {string}`
- `I change my password to my current password`
- `I change my password to {string}`
- `I should see a required-field error for {string}` (also: settings, users)
- `I should see the password message {string}`
- `I update my profile leaving {string} empty`
- `I update my profile with:`
- `I update my profile {string} with a {int}-character value`
- `I upload {string} as my profile photo`
- `my profile should show the updated values after reloading`
- `the password change should be {string}`
- `the photo upload should be {string}`
- `the profile update should be {string}`

## public-routes

- `I have noted the destination domain and threat type of the newest threat`
- `I should not be on the login page`
- `no script from the host parameter should have executed`
- `the anonymous response body should match {string}`
- `the anonymous response body should not contain {string}`
- `the block page should show no threat type`
- `the block page should show the threat type {string}`

## reports

- `I choose the {string} report format`
- `I generate a CSV report for the last {int} days`
- `I request the CSV report download directly`
- `I set the report From Date after the To Date`
- `I set the report range to next month`
- `every Risk Score should match its Risk \(Info {int}, Gray {int}, Low {int}, High {int}, Critical {int})`
- `the CSV should contain the columns:`
- `the downloaded PDF should have at least one page of extractable text`
- `the request should be denied` (also: settings)
- `the request should not cause a server error` (also: settings, users)
- `the share of rows with an empty Asn and Operator should be attached to the report`

## settings

- `I add {string} and {string} to {string}`
- `I add {string} to {string}`
- `I change the period of the alert type {string} to {string}`
- `I create an alert type leaving the name empty`
- `I create an alert type named {string} with period {string}`
- `I create an alert type named {string} without choosing an email template`
- `I create an alert type whose name is {int} characters long`
- `I create an email template named {string} whose content includes {string}`
- `I create an email template named {string} with bold and list content`
- `I delete the alert type {string}`
- `I delete the email template {string}`
- `I disable DNS Bypass`
- `I enable DNS Bypass`
- `I open the API integration tab with autofill enabled`
- `I open the alert notification feed`
- `I open the email template {string}`
- `I remove every network from {string}`
- `I remove {string} from {string}`
- `I rename the email template {string} to {string}`
- `I request Send Diagnostics directly`
- `I request apply_update for the name {string}`
- `I save API keys for VirusTotal, AbuseIPDB, IPinfo, Shodan and AlienVaultOtx`
- `I save Access Control`
- `I save the RPZ zone settings and confirm the prompt`
- `I save the syslog target {string} port {string} over {string}`
- `I set the default language to {string} and timezone to {string}`
- `I set the notification email to {string}`
- `I set the notification emails to {string} and {string}`
- `I set the syslog target to that host and port`
- `I set the {string} RPZ action to {string}`
- `I should see a validation error for {string}` (also: users)
- `I should see the RPZ settings message {string}`
- `I should see the access control error {string}`
- `I should see the alert list message {string}`
- `I should see the alert settings error {string}`
- `I should see the syslog message {string}`
- `I should see the template message {string}`
- `I should see the upload message {string}`
- `I should see the {string} and {string} fields`
- `I start a second patch upload`
- `I submit the alert settings with the email value {string}`
- `I trigger Send Diagnostics`
- `I trigger a lookup that depends on FraudGuard`
- `I upload a text file renamed to {string} to the update endpoint`
- `I upload a valid test patch file under 7MB`
- `I upload an 8MB zip file to the update endpoint`
- `I upload {string} to the update endpoint`
- `a DNS lookup for {string} should not return NXDOMAIN`
- `a DNS lookup from the test host should still be answered`
- `a disposable test listener is reachable at a known host and port`
- `a patch upload is already in progress`
- `a test patch has been applied`
- `an alert type named {string} exists`
- `an email template {string} is referenced by an alert type`
- `at least one unread alert is shown in the bell`
- `every link on the help tab should answer with a non-error status`
- `no script from the template content should have executed`
- `opening {string} should show the same formatted content`
- `reloading the settings page should not show {string} port {string} as the syslog target`
- `reloading the settings page should show each key saved`
- `reloading the settings page should show {string} and {string}`
- `reloading the settings page should show {string} as the notification email`
- `reloading the settings page should show {string} set to {string}`
- `reloading the settings page should show {string} under {string}`
- `the FraudGuard API key is left blank`
- `the FraudGuard section should show a {string} state, not a crash`
- `the FraudGuard username and password fields should be empty`
- `the alert type should still reference {string}`
- `the app cannot reach {string}`
- `the apply response should not contain {string}`
- `the archive should contain {string}`
- `the archive should not contain database or redis credentials`
- `the browser has the ThreatIDR admin login saved for autofill`
- `the default language options should be exactly {string} and {string}`
- `the default network list should contain {string}`
- `the downloaded archive should be a readable tar.gz`
- `the flask-prod container is rebuilt`
- `the next notification push should not list those alerts as unviewed`
- `the original alert settings are recorded for restore`
- `the original general settings are recorded for restore`
- `the original syslog and DNS bypass settings are recorded for restore`
- `the page should send a notification update for the alerts shown in the feed`
- `the patch's changes should still be present`
- `the user log entry for {string} should name the admin as {string}`
- `the {string} RPZ action should be {string} and not editable`
- `{string} should appear in the alert types list with period {string}`
- `{string} should appear in the email templates list`
- `{string} should not appear in the alert types list`
- `{string} should not be shown under {string}`
- `{string} should not be stored as a notification recipient`

## site-risk-indicator

- `I look up the domain {string} on the site risk indicator`
- `one site-risk backing service is intentionally unreachable`
- `the other sources should still return results`
- `the unreachable source should show {string}`

## threat-detection

- `I dismiss the dialog`
- `I filter the threats table by risk category {string}`
- `I filter the threats table by threat {string}`
- `I go to page {int} of the threats table`
- `I open the Discover panel for {string}`
- `I search the threats table for {string}`
- `I set the date range to the last {int} days`
- `I set the domain count filter to {string}`
- `I toggle the threats table sort order`
- `a threat exists for domain {string}`
- `every row shown should be dated within the last {int} days`
- `every row shown should have at most {int} hits for its domain on that day`
- `every row shown should have risk {string} or the table should be empty`
- `every row shown should have threat type {string} or the table should be empty`
- `the FraudGuard API key is not configured`
- `the FraudGuard section should show an unavailable state`
- `the Risk Heatmap should cover the last {int} days`
- `the VirusTotal, AbuseIPDB, IPinfo, Shodan, FraudGuard and AlienVaultOtx sections should load`
- `the other Discover sections should still load`
- `the page should not contain any configured API key value`
- `the threats table filters should remain usable`
- `the threats table has more than one page of rows`
- `the threats table has no rows in the last {int} days`
- `the threats table should be ordered by time newest first`
- `the threats table should be ordered by time oldest first`
- `the {string} label should match the rows displayed`

## users

- `I create a role named {string} with no permissions selected`
- `I create a user leaving {string} empty`
- `I create a user with email {string}`
- `I create a user with password {string}`
- `I create a user with password {string} and confirm password {string}`
- `I create a user with phone {string}`
- `I create a user with the photo {string}`
- `I create a user with {string} set to a {int}-character value`
- `I delete that user`
- `I edit that user's phone number to {string}`
- `I rename the {string} role to {string}`
- `I search users for that last name in lower case`
- `I search users for that user's email`
- `I send a request to change user {int}'s role using that user's email in the form`
- `I send a request to update the Super Administrator with the role field blank`
- `I should see the role error {string}`
- `I should see the user form error {string}`
- `I switch the users list to card view`
- `I switch the users list to list view`
- `a non-admin user exists`
- `a non-admin user exists and is logged in in a separate session`
- `a role named {string} already exists`
- `a user exists with a unique letters-only last name`
- `no users should be listed`
- `reloading the user should show the phone number {string}`
- `roles named {string} and {string} both exist`
- `that user should be the only user listed`
- `the user creation should be {string}`
- `the users should be shown as cards`
- `the users should be shown as table rows`
- `their active session should be redirected to the login page on its next action`
