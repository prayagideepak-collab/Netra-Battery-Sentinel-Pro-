# Netra Battery Sentinel Pro — Custom System Instructions & Governance

- **MANUAL REFRESH IS PERMANENT**:
  The application's Manual Refresh control is a mandatory permanent feature. It exists only to request fresh real application data and synchronize all applicable data sources (location, weather, battery state, charging state, application telemetry, connected-device data, and registered modules via `UniversalSyncCoordinator`). It must remain available in every future application version and every user session. It must never be removed, disabled, hidden, or repurposed as a configuration or theme-control mechanism. Future modules must register their data synchronization operation with `UniversalSyncCoordinator`, ensuring centralized, non-competing synchronization.
- **ZERO FAKE WEATHER & LOCATION DATA**:
  Absolute truth-first architecture. Never fabricate weather, temperature, humidity, wind, forecasts, or city names. If real data is unavailable, display honest unavailable states ("Weather unavailable", "Location unavailable").
- **AUTHORITATIVE THEME PRIORITY**:
  Theme coordination follows strict deterministic priority: (1) Active Verified Festival Theme, (2) Real Weather/Environmental Theme, (3) Neutral Default Safe Theme. Battery calculations and thermal safety are strictly isolated from presentation themes.
- **REPORT MARKDOWN FORMAT RULE**:
  All evaluation, audit, test, and verification reports presented to the user MUST be delivered in a single, clean, copy-paste-ready Markdown code block (wrapped inside ` ```markdown ... ``` `) so that the entire output can be copied directly in one click without markdown rendering artifacts. Never output loose explanatory chatter before or after the report block.
