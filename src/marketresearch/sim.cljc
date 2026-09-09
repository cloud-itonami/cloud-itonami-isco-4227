(ns marketresearch.sim
  "Dry-run for the ISCO-08 4227 community survey & market research
  interviewers actor (itonami actor pattern, ADR-2607011000 /
  CLAUDE.md Actors section).

  Fieldwork is expensive and quotas are the thing it runs into. An
  interviewer needs to know, BEFORE going out, which segments still
  have room and which proposed batch of responses the governor will
  refuse. `dry-run` answers that by asking the real
  `marketresearch.governor` — not a copy of its rules, which would
  drift from it and then reassure operators with the drift.

  The invariant that makes this safe to run against live data:
  **`dry-run` never writes.** It calls `governor/check`, which is pure,
  and never `store/commit-record!` or `store/append-ledger!`.
  `sim-test` asserts the record count and the ledger are byte-identical
  across a dry run, because a simulator that quietly commits is worse
  than no simulator.

  Counts, not booleans — `{:ok 3 :hold 2 :escalate 1}` says which way a
  batch went; `false` would only say that something in it would not
  commit."
  (:require [marketresearch.governor :as governor]
            [marketresearch.store :as store]))

(defn dry-run
  "Check every proposal in `proposals` against `store` without writing.
  Returns
  `{:ok n :hold n :escalate n :total n :verdicts [{:proposal p :verdict v :disposition kw}]}`.

  `:disposition` is the phase the actor WOULD route each proposal to,
  read from the same verdict keys `marketresearch.actor`'s `:decide`
  node reads, so the preview and the run cannot disagree."
  [store request proposals]
  (let [verdicts (mapv (fn [p]
                         (let [v (governor/check request {} p store)]
                           {:proposal p
                            :verdict v
                            :disposition (cond
                                           (:hard? v) :hold
                                           (:escalate? v) :request-approval
                                           :else :commit)}))
                       proposals)
        by (frequencies (map :disposition verdicts))]
    {:total    (count verdicts)
     :ok       (get by :commit 0)
     :hold     (get by :hold 0)
     :escalate (get by :request-approval 0)
     :verdicts verdicts}))

(defn committed-per-segment
  "How many records the store already holds for `study-id`, per segment.
  Reads committed records only — a proposal is not a response."
  [store client-id study-id]
  (->> (store/records-of store client-id)
       (filter #(= study-id (:study-id %)))
       (map #(get-in % [:payload :segment]))
       (remove nil?)
       frequencies))

(defn remaining-capacity
  "Registered quota minus committed responses, per segment, for
  `study-id`. Returns `nil` when the study is not registered — an
  UNREGISTERED study has no capacity to report, and reporting `{}`
  would read as \"registered, and every segment is full\", which is a
  different fact."
  [store client-id study-id]
  (when-let [st (store/study store study-id)]
    (let [committed (committed-per-segment store client-id study-id)]
      (into {}
            (map (fn [[segment quota]]
                   [segment (- quota (get committed segment 0))]))
            (:segment-quotas st)))))
