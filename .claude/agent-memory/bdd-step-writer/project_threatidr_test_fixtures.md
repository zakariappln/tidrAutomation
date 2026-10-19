---
name: threatidr-test-fixtures
description: Persistent limited-permission fixture users/roles kept in the ThreatIDR app between runs, and the rules for touching the admin account
metadata:
  type: project
---

Persistent fixture accounts live in the app on purpose (created 2026-10-19, batch 1 of the UI step work): roles `qa-auto-<permission-key>` and users `qa-auto-<key>@example.com` (e.g. report-read, user-update, user-create; `qa-auto-all` for every grantable permission). Passwords are in the git-ignored `.auth-state/staging/fixture-users.properties`. They must NOT be cleaned up; everything else a scenario creates must be.

Admin account rules from the caller: never change admin@threatidr.com's password, role, active flag or profile; never delete user id 1. The admin's designation is NULL and the edit form requires one, so any successful edit of the admin is irreversible. That is why the "BUG - ... submitting another user's email" scenario stays `@wip` and unglued.

Login is rate-limited to 3 POSTs/min per IP and shared with curl logins; `pgrep -f surefire` matches its own shell, so use `pgrep -f "[s]urefirebooter"`.

**Why:** reusing fixtures saves login slots; deleting them forces new logins and recreations.
**How to apply:** when a later batch needs a limited user, reuse `Given I am logged in as a user with only the "<label>" permission`; see [[threatidr-env-quirks]].
