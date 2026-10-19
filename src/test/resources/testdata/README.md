# testdata

- `api/` JSON payloads for API scenarios (one file per resource).
- `ui/` JSON fixtures for UI tests.
- `multipart/<Controller>.<Action>.json` form fields for `Given I prepare a multipart request with the form fields`
  (`"@file:<name>"` attaches `files/<name>`).
- `QaSelfTest.Sample.json` / `files/qa-selftest.txt` are framework self-test fixtures used by
  `MultipartFixturesTest`; keep them.
