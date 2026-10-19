---
name: threatidr-dashboard-threats-reports
description: Live traps on ThreatIDR Dashboard, Threat Detection, Reports and localization found in batch 3 (2026-10-19) - hidden filter selects, JS errors, PDFs without text layer, shared PDF filename, heatmap date bug
metadata:
  type: project
---

Observed live 2026-10-19 (ThreatIDR v1.5.1, tidr-deploy-v1 on localhost):

- Dashboard Filter menu reloads widgets but `insertArgsToQueryString` (global.js) throws `TypeError: "key" is read-only`, so `?range=` / `?start=&end=` never reach the URL. The Custom range picker has `maxDate: today`. The Allowed Requests tile has no drill-down trigger ("See All" commented out).
- Sidebar version: source template says "Ver. 1.0.1" but the live page shows "ThreatIDR v1.5.1" (set at runtime) - trust the browser, not curl of the template.
- Threat Detection filter `<select>`s are hidden behind a custom `div.styledSelect#styledSelect-<id>` + `ul.options li` (and select2 underneath); `selectOption` times out. No search box is rendered. `recordsTotal` ignores the domain_count filter.
- Threat heatmap start uses `getMonth()+2` (discover.js): wrong in December and at month-ends.
- Reports PDFs (wkhtmltopdf) have 0 fonts and no text layer (text drawn as paths), en and ja; Japanese glyphs render blank (no CJK font in image). PDF file name is `<range>_<type>_report.pdf` regardless of language, so parallel generations overwrite each other; it is publicly fetchable.

**Why:** each cost a run; none is in docs/threatidr-source-facts.md.
**How to apply:** check the live bundle/DOM before trusting templates; see [[threatidr-env-quirks]] and [[threatidr-rpz-and-domains]].
