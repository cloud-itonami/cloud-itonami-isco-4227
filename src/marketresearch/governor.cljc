(ns marketresearch.governor
  "MarketResearchInterviewersGovernor — the independent safety/
  traceability layer for the ISCO-08 4227 community survey & market
  research interviewers actor (itonami actor pattern,
  ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-4311's bookkeeping.governor. Fieldwork twist: a
  response may only be recorded against a REGISTERED segment (no
  fabricated segment), the running segment count must not exceed the
  registered quota (quota is a number, not a suggestion), and if the
  study registers consent as required, a response without obtained
  consent is not data — it's a violation.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. study basis          — a response approval must cite a
                           REGISTERED study belonging to this client.
    4. segment basis        — the proposed segment must be a
                           REGISTERED key in the study's
                           :segment-quotas map (no fabricated
                           segment).
    5. quota ceiling        — the proposed segment-count-after must
                           not exceed the registered quota for that
                           segment.
    6. consent gate         — if the study's registered
                           :consent-required? is true, the proposed
                           consent-obtained must be true (unconsented
                           recording is a violation, not data).
  ESCALATION invariants (:escalate? true, human sign-off):
    7. :op :approve-quota-reopening (reopening a closed segment quota).
    8. low confidence (< `confidence-floor`)."
  (:require [marketresearch.store :as store]))

(def confidence-floor 0.6)

(defn- hard-violations [{:keys [request proposal]} client-record st]
  (let [{:keys [op segment segment-count-after consent-obtained]} proposal
        approve? (= :approve-response op)
        quotas (:segment-quotas st)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and approve? (nil? st))
      (conj {:rule :unknown-study :detail "未登録 study への回答承認は不可"})

      (and approve? st (not= (:client-id st) (:client-id request)))
      (conj {:rule :study-wrong-client :detail "study が別 client のもの"})

      (and approve? st segment (not (contains? quotas segment)))
      (conj {:rule :unknown-segment :detail (str "未登録セグメント: " segment "（セグメントの捏造禁止）")})

      (and approve? st segment (contains? quotas segment) (number? segment-count-after)
           (> segment-count-after (get quotas segment)))
      (conj {:rule :quota-exceeded
             :detail (str "セグメント " segment " の回答数 " segment-count-after
                          " > 登録済みクォータ " (get quotas segment)
                          "（クォータは数値であって目安ではない）")})

      (and approve? st (:consent-required? st) (not (true? consent-obtained)))
      (conj {:rule :consent-not-obtained
             :detail "同意未取得の回答記録は許可されない（同意なき記録はデータではなく違反）"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `marketresearch.store/Store`. Pure — never
  mutates the store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        st (some->> (:study-id proposal) (store/study store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record st)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (= :approve-quota-reopening (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
