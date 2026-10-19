---
name: threatidr-env-quirks
description: Live ThreatIDR (tidr-deploy-v1, localhost) behaviours that break naive UI tests - users page 500 race, missing uploads dir, client-side checks shadowing server messages
metadata:
  type: project
---

Confirmed live 2026-10-19 against ThreatIDR v1.5.1 at http://localhost (tidr-deploy-v1 Docker stack).

- Users page (`/en/users/`) answers HTTP 500 when another request creates or deletes a user while it renders (template indexes `user.roles[0]` while lazy-loading; register commits the user before its role). Parallel scenarios hit this constantly; the Users/Roles features carry `@serial-users` for that reason, and `AdminHttp` reads its CSRF token from `/en/user/profile`, not the users page.
- `GET /en/users/` for a user with no user-*/role-* permission is a 500, not 401.html (known defect, own BUG scenario in role-based-access.feature).
- `/app/src/static/uploads/users/` is missing in the flask-prod container: every accepted photo upload (user create, profile) is a 500 FileNotFoundError. Those Examples rows are `@blocked-env`.
- Create-user phone uses intl-tel-input with separate dial code: submitted as `+880 <typed>`, so only 10 typed digits fit the 15-char server limit.
- Client-side JS checks run before the server: mismatch shows "Password didn't match. Please re-enter the password correctly.", never "Passwords must match". Duplicate role-name error is never made visible (d-none toggle bug).
- UI-created users default to the Administrator role (first option of the role select); make sure their cleanup works.
- Direct DB reads via `docker exec ... psql` are blocked by the permission classifier; use the app's own endpoints (e.g. `GET /en/users/update_user/<id>` with the saved admin cookie) instead.

**Why:** these cost several debugging rounds; none is visible from the source facts doc alone.
**How to apply:** before blaming a locator for a users/profile failure, check the flask-prod log (`docker logs --since 10m tidr-deploy-v1-flask-prod-1 | grep -v systemctl`) and nginx log for 500s; see also [[threatidr-test-fixtures]].
