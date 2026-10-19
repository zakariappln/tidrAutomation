# Discovery Questions

Walk every section. Tick what the supplied material (spec / curl / Postman / docs / sample payloads) already answers; ask the rest. Ask in one grouped, numbered message. Where a default is reasonable, propose it as an explicit assumption the user can veto — but **never** default the items marked ⛔.

## A. Endpoint & auth

1. Full path and HTTP method(s) in scope? Path parameters and their format (UUID, numeric, slug)? ⛔
2. Auth scheme — Bearer JWT, API key header, Basic, OAuth2 client-credentials, none? Header name? ⛔
3. Which roles/scopes exist and which can call this endpoint? Is there a role that must be **denied**?
4. Is there tenant / account scoping (e.g. `X-Tenant-Id`)? Can a caller read another tenant's data (expected 403 or 404)?
5. Versioning — URL (`/v1/`), header (`Accept: application/vnd.x.v1+json`), or none?

## B. Request

6. Required request headers beyond auth (`Content-Type`, `Accept`, `X-Request-Id`, `Idempotency-Key`, correlation ids)? Which are mandatory vs optional? ⛔
7. Request body schema: field list, types, required vs optional, formats (email, ISO-8601, enum values), min/max lengths, numeric ranges, array limits. ⛔
8. Query parameters: filters, sorting, pagination (`page/size` or cursor?), defaults and maximums.
9. Are unknown/extra fields rejected (400) or ignored?
10. Is the request idempotent? Is there an `Idempotency-Key` contract? What happens on duplicate submission — 200 replay, 409, or new resource?

## C. Response

11. Success status per method — 200 vs 201 vs 202 vs 204? ⛔
12. Response headers to assert — `Content-Type`, `Location` (on 201), `ETag`, `Cache-Control`, `X-Request-Id` echo, rate-limit headers, CORS? ⛔
13. Success body schema (fields, types, nullable, which fields are server-generated like `id`, `createdAt`, `version`). Can you share a real sample response? ⛔
14. Error body contract — shape (`{code, message, details[]}`? RFC 7807 problem+json?), and the exact code/message for each validation failure. ⛔
15. Which error statuses exist for this endpoint: 400, 401, 403, 404, 405, 409, 415, 422, 429, 500? Which condition triggers each? ⛔

## D. Business rules

16. Uniqueness constraints (e.g. email unique per tenant)? What is returned on conflict?
17. State rules — can the resource be modified/deleted in every state, or only some? What is returned otherwise?
18. Derived/computed fields and their rule (totals, statuses, defaults applied server-side)?
19. Soft-delete vs hard-delete? Does GET after DELETE return 404 or the resource with a flag?
20. Any limits: max items per user, quotas, time windows?

## E. Dependencies & preconditions

21. Which values must come from **another** call (ids, tokens, parent resources)? Which endpoints create them?
22. Can preconditions be created via API, or must some exist as seed data? If seed data — how is it referenced (fixed ids, lookup by name)?
23. Is cleanup required / possible? Which endpoint deletes the created data? Any resources that cannot be deleted?

## F. Data & environment

24. Are there restrictions on test data (no real PII, specific email domains, sandbox card numbers)?
25. Are timestamps/ids deterministic enough to assert, or should tests assert format/presence only?
26. Is the environment shared (parallel runs need unique data) or isolated?
27. Non-functional expectations to include: response-time SLA, rate limit values? (Include only if confirmed.)

## G. Scope & output

28. Which scenario categories are in scope — happy path only, full negative, security, contract?
29. Preferred feature-file granularity: one per endpoint+method, or one per resource?
30. Any existing step vocabulary / step definitions in the repo that must be reused? (Check the repo yourself first; ask only if ambiguous.)

## How to phrase the question message

```
Before I write the feature files I need a few facts I can't infer from the spec.
Items marked (assume) are what I'll use unless you correct me.

Endpoint & auth
1. ...
Request
2. ...
Response
3. Error body shape — I can see 400 exists but not its body. Can you paste one?
Dependencies
4. ...

(assume) Content-Type: application/json is required on POST → 415 otherwise.
(assume) Unknown fields are ignored.
```
