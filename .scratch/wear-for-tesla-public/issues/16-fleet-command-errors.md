# 16: Honest outcomes for Fleet API command errors

**What to build:** A cloud command that Tesla rejects with an HTTP error is reported as "No connection" today. A 4xx reply (403, 429…) means the command was not forwarded to the car: report it as an error naming the Fleet API code. A 5xx reply may come after Tesla forwarded it: report "unknown outcome". A connect timeout stays "unknown" (the request may have left; OkHttp does not tell connect and read timeouts apart reliably).

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] 4xx → error with the code, 5xx → unknown outcome (FleetTransport / CommandRouter tests)
- [ ] Network failures before sending still read "No connection" and may still fall back as today

## Comments

- 2026-10-24: From the final review (deferred minors on FleetTransport error mapping).
