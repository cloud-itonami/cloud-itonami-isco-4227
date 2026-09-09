(ns marketresearch.operation
  "The REGISTRY of operations the ISCO-08 4227 community survey & market
  research interviewers actor is allowed to perform, and the shape each
  one must have (itonami actor pattern, ADR-2607011000 / CLAUDE.md
  Actors section).

  Why this namespace exists — measured, not argued. Before it, every
  HARD study/segment/quota/consent rule in `marketresearch.governor`
  was guarded on `(= :approve-response op)`, and the only other op the
  governor named was `:approve-quota-reopening`. So an operation nobody
  ever defined skipped every check and reached `:ok? true`: running
  `{:op :exfiltrate-respondent-pii :effect :propose :confidence 0.99}`
  through the graph COMMITTED a record. The governor's default for an
  unrecognised op was allow.

  The same shape hid a second one. The quota ceiling only fired when
  `(number? segment-count-after)`, so a proposal carrying the STRING
  \"9999\" against a quota of 100 was not over quota — it was
  unmeasured, and unmeasured returned the same `:ok? true` as
  measured-and-fine.

  Both are the one failure this fleet keeps re-finding: a check that
  COULD NOT RUN returning the value of a check that ran and found
  nothing. So the registry is closed and `problems` fails closed — an
  unregistered op is itself a problem, never an empty problem list.

  A registered op declares the fields a well-formed proposal must
  carry and the type each must have. Types are keyword tags rather
  than predicate fns so the registry stays inspectable data.

  `:consent-obtained` is required as an explicit boolean on
  `:approve-response` even for a study that does not require consent.
  `nil` means the advisor did not say, and \"did not say\" must not be
  readable as `false` — the two have to stay distinguishable at the
  boundary, or a missing field silently becomes a negative answer."
  (:require [clojure.string :as str]))

(def registry
  "op -> {:doc str :required {field type-tag}}. Closed: an op that is
  not a key here cannot be proposed."
  {:approve-response
   {:doc "Record one respondent's completed interview against a registered study segment."
    :required {:study-id :string
               :segment :string
               :segment-count-after :number
               :consent-obtained :boolean}}
   :approve-quota-reopening
   {:doc "Reopen a segment quota that has been closed. Always escalated to a human."
    :required {:study-id :string}}})

(def ^:private type-pred
  {:string  string?
   :number  number?
   :boolean boolean?})

(defn registered?
  "Is `op` a key of `registry`?"
  [op]
  (contains? registry op))

(defn spec
  "The registry entry for `op`, or nil."
  [op]
  (get registry op))

(defn ops
  "Every registered op, sorted, for operator-facing messages."
  []
  (vec (sort (keys registry))))

(defn problems
  "Every reason `proposal` is not a well-formed instance of a
  REGISTERED operation. `[]` means well-formed.

  Fails closed: an unregistered `:op` returns a
  `:kind :unregistered-operation` problem rather than `[]`, so \"no
  operation matched\" can never be read as \"no problems found\".
  Field problems are sorted by field name so the same bad proposal
  always explains itself the same way."
  [proposal]
  (let [op (:op proposal)
        s  (spec op)]
    (if (nil? s)
      [{:kind :unregistered-operation :field :op :got op}]
      (->> (:required s)
           (keep (fn [[field tag]]
                   (let [v    (get proposal field)
                         pred (type-pred tag)]
                     (cond
                       (nil? v)        {:kind :missing-field :field field :expected tag}
                       (nil? pred)     {:kind :unknown-type-tag :field field :expected tag}
                       (not (pred v))  {:kind :wrong-type :field field :expected tag :got v}))))
           (sort-by (comp name :field))
           vec))))

(defn explain
  "One-line operator-facing rendering of `problems` output."
  [probs]
  (str/join "; "
            (map (fn [{:keys [kind field expected got]}]
                   (case kind
                     :unregistered-operation
                     (str "未登録の操作: " (pr-str got)
                          "（登録済み: " (str/join ", " (map pr-str (ops))) "）")
                     :missing-field
                     (str field " が無い（" (name expected) " が必須）")
                     :wrong-type
                     (str field " の型が違う: " (pr-str got)
                          " は " (name expected) " ではない")
                     :unknown-type-tag
                     (str field " の型タグ " (pr-str expected) " は未知")))
                 probs)))
