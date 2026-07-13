# cloud-itonami-isco-4227

Open Business Blueprint for **ISCO-08 4227**: Survey and Market Research Interviewers — an ISCO
**Wave 0 (cognitive substrate)** occupation per ADR-2607121000:
pure-cognitive work, the LLM-first wave, **no robotics gate** —
eligible for actor implementation now.

**Maturity: `:implemented`** — MarketResearchInterviewersAdvisor ⊣
MarketResearchInterviewersGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
15 tests / 30 assertions green.

The fieldwork HARD invariants — arithmetic and a consent gate, not a
courtesy:

1. **Segment basis + quota ceiling** — a response must cite a
   registered segment, and the running segment count must not exceed
   the registered quota for that segment (quota is a number, not a
   suggestion).
2. **Consent gate** — when the study registers consent as required, a
   response without obtained consent is refused — unconsented
   recording is a violation, not data.

Also HARD: unregistered/foreign study, unregistered organization,
non-`:propose` effect. Escalations (always human sign-off):
`:approve-quota-reopening` (reopening a closed segment quota), low
confidence (< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
