(ns marketresearch.store
  "SSoT for the ISCO-08 4227 community survey & market research
  interviewers actor (itonami actor pattern, ADR-2607011000 /
  CLAUDE.md Actors section). Modeled on cloud-itonami-isco-4311's
  bookkeeping.store.

  Domain:

    client — a registered organization (:client-id, :name)
    study  — a registered survey study {:study-id :client-id :name
             :segment-quotas {segment-str number}
             :consent-required? bool}. `:segment-quotas` is the
             registered per-segment sample ceiling a proposed
             response's running segment count must not exceed (quota
             is a number, not a suggestion); `:consent-required?`
             gates whether recording a response requires informed
             consent to have been obtained (recorded data without
             required consent is not data, it's a violation).
    record — a committed operating record (approved response) —
             written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (study [s study-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-study! [s st])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (study [_ study-id] (get-in @a [:studies study-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-study! [s st]
    (swap! a assoc-in [:studies (:study-id st)] st) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :studies {} :records [] :ledger []}
                                   seed)))))
