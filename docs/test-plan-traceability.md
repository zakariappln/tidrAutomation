# Test plan traceability: `ThreatIDR_Test_Plan.xlsx` → `features/ui/**` (2026-10-18)

Sheet "Test Cases": 132 cases. Status column in the workbook is "Not Run" for every row.
Legend: **ACTIVE** = scenario exists and runs; **WIP** = scenario exists, tagged `@wip` (no glue yet);
**PARTIAL** = scenario covers only part of the case; **MISSING** = no scenario.

| Result | Count |
|---|---|
| ACTIVE | 17 |
| WIP | 73 |
| PARTIAL | 5 |
| MISSING | 37 |

## Authentication (TC-AUTH)
| ID | Case | Status | Scenario |
|---|---|---|---|
| 001 | Valid login | ACTIVE | login: Shows the dashboard after a valid login |
| 002 | Invalid password, no enumeration | ACTIVE | login: wrong password + "BUG - unregistered email reveals…" (documents the enumeration defect) |
| 003 | Empty email/password rejected | MISSING | |
| 004 | Rate limit 3/min | ACTIVE | login: Blocks a 4th login attempt |
| 005 | Window resets after 60 s | ACTIVE | login: Allows login again after the window |
| 006 | Logout invalidates session | ACTIVE | login: Logging out ends the session |
| 007 | Reset link for valid email | MISSING | password-reset.feature is fully commented out (needs a way to read the token) |
| 008 | Reset for unknown email | MISSING | |
| 009 | Reset complexity | MISSING | |
| 010 | Expired / reused token | MISSING | |
| 011 | Inactive user message | MISSING | needs a seeded inactive account |
| 012 | Protected page redirects to login | ACTIVE | login: Redirects an anonymous visitor (4 rows); public-routes outline (WIP) |
| 013 | License valid / invalid key | PARTIAL | license: Rejects an invalid license key (WIP); valid-key path missing |

## Dashboard (TC-DASH)
| ID | Case | Status |
|---|---|---|
| 001 | Core widgets | ACTIVE |
| 002 | Filter updates widgets | WIP |
| 003 | % change sanity | WIP |
| 004 | Zero-threat empty state | WIP |
| 005 | Service Status widget reflects backend health | MISSING |
| 006 | Tile drill-down | WIP |
| 007 | Designation shown | WIP |
| 008 | Branding / logo renders | MISSING |

## Threat Detection (TC-THR)
| ID | Case | Status |
|---|---|---|
| 001 | Heatmap date range matches filter | MISSING |
| 002 | Combined filters | WIP |
| 003 | Search by domain / source IP | PARTIAL (domain only, WIP) |
| 004 | Filter by risk category | WIP |
| 005 | Filter by threat type | MISSING |
| 006 | Pagination | WIP |
| 007 | Discover panel shows external intel | MISSING (only the outage case exists) |
| 008 | Third-party outage handled | WIP |
| 009 | IP search boundary / format | MISSING |
| 010 | No-threats dialog dismissible | WIP |

## Blocked Domains (TC-BLK)
| ID | Case | Status |
|---|---|---|
| 001 | Add valid domain | ACTIVE |
| 002 | Duplicate rejected | WIP |
| 003 | 255 / 256 boundary | WIP |
| 004 | Invalid format | WIP |
| 005 | Delete | WIP |
| 006 | Apply blocks DNS end-to-end | ACTIVE |
| 007 | Pagination | WIP |
| 008 | Status column Active/Inactive | PARTIAL (asserted inside 001 and 006, no dedicated scenario) |

## Allowed Domains (TC-ALW)
| ID | Case | Status |
|---|---|---|
| 001 | Add valid domain | ACTIVE |
| 002 | Duplicate rejected | WIP |
| 003 | Length / format boundary | MISSING (only Blocked has it) |
| 004 | Delete | WIP |
| 005 | Apply propagates to BIND allow-list (passthru) | MISSING |
| 006 | Cannot be Blocked and Allowed at once | MISSING |
| 007 | Pagination | MISSING |

## Reports (TC-RPT)
| ID | Case | Status |
|---|---|---|
| 001 | Quick-range buttons | ACTIVE |
| 002 | Summary report renders | WIP |
| 003 | CSV columns | WIP |
| 004 | PDF renders | WIP |
| 005 | From > To rejected | WIP |
| 006 | Zero-data range | WIP |
| 007 | Download endpoints respect permission | WIP (reports + authorization features) |
| 008 | 30-day retention messaging | MISSING |

## Users and Roles (TC-USR)
| ID | Case | Status |
|---|---|---|
| 001 | Mandatory fields | WIP |
| 002 | Email format + duplicate | WIP |
| 003 | Complexity + confirm mismatch | WIP |
| 004 | Phone format | MISSING |
| 005 | Photo type and size | MISSING |
| 006 | Search | WIP |
| 007 | Edit persists | WIP |
| 008 | Delete revokes session | ACTIVE |
| 009 | Super admin cannot be deleted | ACTIVE |
| 010 | Super admin role cannot be downgraded | ACTIVE (+ "omitting the role field") |
| 011 | Duplicate role name on create | WIP |
| 012 | Rename role to existing name | WIP |
| 013 | At least one permission | WIP |
| 014 | Least-privilege role | WIP (users + authorization) |
| 015 | List / card view toggle | MISSING |

## Profile (TC-PRF)
| ID | Case | Status |
|---|---|---|
| 001 | View own profile | ACTIVE |
| 002 | Update within limits | WIP |
| 003 | Exceeding max length | WIP |
| 004 | Photo type and size | PARTIAL (type only, WIP) |
| 005 | Wrong current password | WIP |
| 006 | Reusing the same password | MISSING |
| 007 | Complexity 8 vs 7 | WIP |

## Settings
| ID | Case | Status |
|---|---|---|
| GEN-001 | Language + timezone | WIP |
| GEN-002 | Bengali availability | WIP |
| GEN-003 | RPZ category actions | WIP |
| GEN-004 | Access Control multiple IPs | WIP |
| GEN-005 | Invalid IP | WIP |
| GEN-006 | `localhost` literal | WIP |
| GEN-007 | Auto-detect local IP fallback | MISSING |
| ALT-001 | Notification email config | WIP |
| ALT-002 | Alert type + "No Alert" | WIP |
| ALT-003 | Alert type needs valid template reference | MISSING |
| ALT-004 | Edit / delete alert type | WIP |
| ALT-005 | Bell mark-as-viewed | WIP |
| MAIL-001 | Rich-text template | WIP |
| MAIL-002 | Edit / delete referenced template | PARTIAL (delete only, WIP) |
| MAIL-003 | Script sanitised | WIP |
| API-001 | Save valid API keys | MISSING |
| API-002 | FraudGuard autofill | WIP |
| API-003 | Blank key degrades gracefully | WIP |
| LOG-001..004 | User Log, System Log, Help, Diagnostics | WIP (all four) |
| SYS-001 | Valid syslog IP+port, connection test | MISSING |
| SYS-002 | Out-of-range port | WIP |
| SYS-003 | DNS Bypass toggle | WIP |
| UPD-001 | Upload valid zip | MISSING |
| UPD-002 | Non-zip renamed | WIP |
| UPD-003 | Over 7 MB | WIP |
| UPD-004 | Concurrent uploads serialised | MISSING |
| UPD-005 | Patch survives rebuild | MISSING (infrastructure test) |
| UPD-006 | Path traversal | WIP |

## Hidden modules (TC-HID)
| ID | Case | Status |
|---|---|---|
| 001 | DGA permitted role | WIP |
| 002 | DGA denied | WIP |
| 003 | Site Risk tolerates partial third-party failure | MISSING |
| 004 | Site Risk permission | WIP |
| 005 | History of IP default + lookup | WIP |
| 006 | History of IP malformed input | WIP |
| 007 | Housekeeping options | ACTIVE |
| 008 | Restore snapshot list requires auth | ACTIVE |
| 009 | Whitelist/Blocklist history captures add/delete | WIP |
| 010 | Access Log loads + filters | WIP |
| 011 | Access Log reachable by any user | WIP |

Note: on build 0.0.5 the DGA, History of IP, Site Risk and Backup & Restore pages return HTTP 500
(TemplateNotFound) for every role, so HID-001/004/005/006 cannot pass until product ships the templates.

## Public routes (TC-PUB)
| ID | Case | Status |
|---|---|---|
| 001 | /nxdomain public | WIP |
| 002 | /syslog public | WIP |
| 003 | Other URLs protected | ACTIVE (login outline) + WIP (public-routes outline) |

## REST API (TC-API-001..008)
All eight **MISSING**: `features/api/` is empty. The API glue (`RequestSteps`, `ResponseSteps`,
`VariableSteps`) is already implemented, so these are feature files plus `api.base.url` away.

## Localization (TC-LOC)
| ID | Case | Status |
|---|---|---|
| 001 | Locale persists after login | WIP |
| 002 | Japanese locale + PDF fonts | WIP (reports feature) |
| 003 | Unsupported lang code | WIP |

## Not automated by design
Sheet "Docker-Infra Tests" (20 rows) has no counterpart in the suite; they are container and
deployment checks, not UI/API scenarios.

## Recommendations
1. Tag every scenario with its plan id (`@TC-BLK-006`) so the Allure report and this matrix stay in sync
   automatically; the workbook Status column can then be filled from the run instead of by hand.
2. Write the 37 missing cases; the REST API block (8) and Allowed Domains (4) are the cheapest, the
   password-reset block (4) is blocked on reading the reset token (mail or DB).
3. Un-`@wip` the 73 parked scenarios module by module with the `bdd-step-writer` agent.
