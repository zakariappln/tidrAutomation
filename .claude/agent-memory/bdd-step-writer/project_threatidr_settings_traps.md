---
name: threatidr-settings-traps
description: ThreatIDR Settings/Update System live traps from batch 4 (2026-10-19) - named.conf DNS lockout, no bell pushes, self-referencing aria-labelledby, /patch_update permission, CSRF 413, CKEditor typing
metadata:
  type: project
---

Observed live 2026-10-19 (ThreatIDR v1.5.1, tidr-deploy-v1 on localhost):

- **Never let an RPZ save or a valid Access Control save through.** `write_named_conf` regenerates the bind-mounted `tidr-deploy-v1/config/bind9/named.conf` with `allow-query { localhost; <networks>; }` + `rndc reconfig`. The deploy file has a hand-edited `allow-query { any; }` the app cannot write back. The test host reaches bind via the docker port (gateway source address), so the test host would be locked out of DNS. Baseline md5 `7b4267a1d43e8a8f86afdb8a38438161`; check it before and after any run that touches Access Control. `CapturedCall.guarded` aborts the risky requests.
- **Bell pushes never arrive.** The Celery worker has consumed no task since 2026-10-18 13:06. The browser sees only socket.io pings on `/notifysocket`. So the stored "notification sound" flag (only readable from a push) is unknown, and the alert-save/bell scenarios are @blocked-env.
- **Alert settings save drops keys.** It does `jsonb_set(value,'{config}',...)`, replacing the whole config, so seeded keys (mail_*) vanish on any save. Only `email` is read by the app.
- **`getByLabel` fails on settings forms.** The inputs (template_name, alert_name, ...) carry `aria-labelledby` pointing at themselves, which hides the `<label>`. Use ids. Options inside hidden selects are not matched by `getByRole(OPTION)`; use CSS with `JsRegex.quote`, never `Pattern.quote` (JS has no `\Q`).
- **Update System.** Every zip upload ends in "Error saving file." (`[Errno 13] Permission denied: '/patch_update'`). An over-7 MiB POST gets a bare Flask 413 JSON, because CSRFProtect parses the form before the view. nginx `client_max_body_size 8m`.
- **CKEditor (CDN standard build).** Type with `page.keyboard()` after one click. Locator typing refocuses the body and splits list items.
- **Settings `index()` has side effects.** Rendering it inserts default rows (a syslog row with ip None) and re-saves response_policy. Any later successful syslog save therefore hits the "existing row" branch, which shuts Celery down.
- The DNS bypass zone `block_domain_to_ip.host.dtq` is not served by bind (known defect).
- A curl cookie jar built from `.auth-state/staging/admin.json` goes stale after suite runs; rebuild it before probing.

**Why:** each cost a run or was a safety stop; none is in docs/threatidr-source-facts.md.
**How to apply:** before touching Settings saves, re-read this note; see [[threatidr-env-quirks]] and [[threatidr-rpz-and-domains]].
