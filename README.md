# cloud-itonami-isco-4227

Open Business Blueprint for **ISCO-08 4227**: Survey and Market Research Interviewers — an ISCO
**Wave 0 (cognitive substrate)** occupation per ADR-2607121000:
pure-cognitive work, the LLM-first wave, **no robotics gate** —
eligible for actor implementation now.

**Maturity: `:implemented`** — MarketResearchInterviewersAdvisor ⊣
MarketResearchInterviewersGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
51 tests / 124 assertions green.

Actor components: `operation` (the closed registry of legal
operations), `governor` (the independent refusal layer), `store`
(SSoT), `ledger` (chained append-only audit trail), `phase` (the
declared run phases the graph is built from), `sim` (dry-run against
live data, writes nothing).

```bash
clojure -M:test    # 51 tests / 124 assertions
clojure -M:gate    # demonstrate that the governor REFUSES, and why
clojure -M:lint    # clj-kondo over src, test, tools
```

`clojure -M:gate` runs eight refusal cases through the real
compiled graph and **pins the rule literal each must be refused for** —
a case refused for a *different* reason fails rather than counting as a
demonstration. It exits `0` on a clean run, `1` when a case is not
refused / is refused for the wrong reason / when the valid control
proposal fails to commit / when zero refusals fire, and `2` when it
could not run at all, so a gate that never executed is not mistaken for
one that ran and found nothing.

Two defects it was written after finding, both measured on the tree at
`a06b9ae` and both now closed:

- **an operation nobody defined committed a record.** Every
  study/segment/quota/consent rule was guarded on
  `(= :approve-response op)`, so `{:op :exfiltrate-respondent-pii}`
  skipped all of them, reached `:ok? true`, and wrote a record. The
  governor's default for an unrecognised op was *allow*.
- **a non-numeric response count bypassed the quota ceiling.** The
  rule only fired when `segment-count-after` was a number, so the
  string `"9999"` against a quota of 100 was not over quota — it was
  unchecked, and unchecked returned the same `:ok? true` as
  checked-and-fine.

The fieldwork HARD invariants — arithmetic and a consent gate, not a
courtesy:

1. **Segment basis + quota ceiling** — a response must cite a
   registered segment, and the running segment count must not exceed
   the registered quota for that segment (quota is a number, not a
   suggestion).
2. **Consent gate** — when the study registers consent as required, a
   response without obtained consent is refused — unconsented
   recording is a violation, not data.

Also HARD: **unregistered operation** (`:op` must be a key of
`marketresearch.operation/registry`, carrying that op's declared fields
with their declared types), unregistered/foreign study, unregistered
organization, non-`:propose` effect. Escalations (always human sign-off):
`:approve-quota-reopening` (reopening a closed segment quota), low
confidence (< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
