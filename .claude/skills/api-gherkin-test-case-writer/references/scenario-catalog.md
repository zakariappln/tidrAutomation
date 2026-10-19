# Scenario Catalog

A completeness checklist. Go through the relevant section and either write the scenario, or consciously note why it is out of scope. Every item marked **(confirm)** needs the user's answer from discovery before it may be written — if not confirmed, it goes in the "open questions" list, not in the feature.

## Common to every method

### Positive
- Happy path with all required fields → success code, headers, schema, field values.
- Happy path with all optional fields supplied.
- Happy path with only required fields → optional fields have documented defaults **(confirm defaults)**.
- Correlation header echoed back (`X-Request-Id`) **(confirm)**.

### Security
- No auth → 401, error body shape **(confirm shape)**.
- Expired / malformed token → 401.
- Valid token, insufficient role → 403 **(confirm which role)**.
- Cross-tenant access → 403 or 404 **(confirm which)**.
- Response never contains sensitive fields (`password`, `secret`, tokens).

### Contract
- Response body matches JSON schema.
- `Content-Type` header exact value.
- Unsupported `Accept` → 406 **(confirm)**.
- Wrong method on the path → 405 with `Allow` header **(confirm)**.

### Robustness
- Unknown query parameter → ignored or 400 **(confirm)**.
- Path parameter in wrong format (e.g. `abc` where UUID expected) → 400 vs 404 **(confirm)**.
- Very long string in a text field at max+1 → 400 with field error.
- Unicode / emoji in text fields accepted and round-tripped.
- Response time under SLA **(confirm value)**.

## POST (create)

- 201 + `Location` header pointing to the new resource; GET on `Location` returns it (chained scenario).
- Missing each required field → 400, one Scenario Outline row per field.
- Wrong type per field (string where number, etc.) → 400 rows.
- Boundary values: min, max, min-1, max+1 for lengths and ranges → Outline.
- Enum field with unknown value → 400.
- Malformed JSON body → 400.
- Empty body → 400.
- Missing `Content-Type` / wrong `Content-Type` → 415 **(confirm)**.
- Duplicate on unique field → 409 **(confirm code + body)**.
- Idempotency-Key replay → same response, no second resource **(confirm exists)**.
- Server-generated fields cannot be set by client (`id`, `createdAt` in body → ignored or 400) **(confirm)**.

## GET (single)

- Existing id → 200 + full body.
- Non-existent id → 404 + error body.
- Soft-deleted resource → 404 or 200 with flag **(confirm)**.
- `ETag` returned; `If-None-Match` → 304 **(confirm)**.
- Cache headers **(confirm)**.

## GET (collection / list)

- Default page returned with pagination metadata (total, page, size or cursor) **(confirm shape)**.
- `page`/`size` honoured; `size` above max is capped or 400 **(confirm)**.
- Empty result set → 200 with empty array, not 404 **(confirm)**.
- Each filter parameter individually → only matching items.
- Sorting ascending/descending on each sortable field **(confirm fields)**.
- Invalid filter value → 400.
- Items do not include fields outside the list-view schema.

## PUT (full replace)

- Full valid body → 200 (or 204) with updated fields **(confirm code)**.
- Missing required field → 400 (PUT is total).
- Non-existent id → 404 (or 201 upsert **(confirm)**).
- Immutable field changed → 400 or silently ignored **(confirm)**.
- Optimistic locking: stale `If-Match`/`version` → 412 or 409 **(confirm)**.

## PATCH (partial)

- Single field update leaves others untouched (assert unchanged fields).
- Empty patch → 200 no-op or 400 **(confirm)**.
- Patch to invalid value → 400.
- Patch on immutable field → **(confirm)**.
- JSON Merge Patch vs JSON Patch semantics — `null` removes field? **(confirm)**.

## DELETE

- Existing → 204 (or 200 with body **(confirm)**), then GET → 404.
- Non-existent → 404 or idempotent 204 **(confirm)**.
- Delete twice → second response **(confirm)**.
- Resource in a state that forbids deletion → 409 **(confirm states)**.
- Cascading effects on children **(confirm)**.

## Dependent / workflow scenarios

- Create → Get → Update → Get → Delete → Get chain as a single `@regression` end-to-end scenario, each step asserting status and key fields.
- Parent must exist before child creation; child creation with missing parent → 404/422 **(confirm)**.
- State transitions: each allowed transition → 200; each forbidden transition → 409 **(confirm the state machine — never guess it)**.

## Coverage matrix format (put in the reply)

| # | Scenario title | Method | Category | Suite | Depends on |
|---|----------------|--------|----------|-------|------------|
| 1 | Returns 201 and Location when payload is valid | POST | positive | smoke | — |
| 2 | Returns 400 when <field> is <problem> (6 rows) | POST | negative | regression | — |
| 3 | Returns 409 when email already exists | POST | negative | regression | precondition: existing user |
