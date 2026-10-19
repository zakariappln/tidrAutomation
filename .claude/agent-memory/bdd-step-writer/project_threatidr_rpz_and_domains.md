---
name: threatidr-rpz-and-domains
description: How ThreatIDR's Blocked/Allowed Domains, RPZ zones (bind9) and the Settings User Log behave live, and the traps found while automating them (batch 2, 2026-10-19)
metadata:
  type: project
---

Observed live 2026-10-19 (ThreatIDR v1.5.1, tidr-deploy-v1 on localhost):

- bind9 on 127.0.0.1:53 has `allow-query { any; }` and answers the RPZ zones directly: `dig @127.0.0.1 <domain>.pplnbl.host.ppln CNAME` gives `.` (block); `<domain>.whitelisted.host.ppln` gives `rpz-passthru.`; a literal `*.<domain>` reads the wildcard rule. AXFR works too. bind logs go to files in the `dnslogs` volume (`/var/named/bind.log`), not to `docker logs`.
- Apply Changes (`generate_blacklist` / `generate_whitelist`) writes EVERY row (Active or not) into the zone and runs `rndc reload <zone>` ("zone reload queued", async). A probe showed the new rule visible within 1 s even 10 s after a previous Apply. Once, under 4-way parallel load, the allowlist rule never appeared within 15 s and the cause could not be traced (`docker exec` into bind is blocked by the permission classifier).
- "Apply Changes" is rendered only while the current list page has at least one row, so a UI cleanup that clicks it can fail. Cleanups delete by id over HTTP instead.
- The Settings > User Log partial loads jQuery + DataTables from CDNs a second time, so `$` on that page has no `.DataTable`. Use the `window.DataTable` global (`DataTable.isDataTable`, `new DataTable.Api(table)`).
- Once, the flask-prod gunicorn (single gevent worker) aborted ("Unexpected error 9 on netlink descriptor", worker code 134) during a run: in-flight requests got nginx 502 and two scenarios failed. It is an app/environment crash, not a test bug.

**Why:** each of these cost a debug round. None of them is in docs/threatidr-source-facts.md.
**How to apply:** for an RPZ assertion, query the zone, not the domain (.example is NXDOMAIN anyway). Before blaming a locator for a sudden 502, grep the flask log for `code 134`. See [[threatidr-env-quirks]] and [[threatidr-test-fixtures]].
