(ns marketresearch.operation-test
  (:require [clojure.test :refer [deftest is testing]]
            [marketresearch.operation :as operation]
            [marketresearch.governor :as governor]
            [marketresearch.actor :as actor]
            [marketresearch.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-study! st {:study-id "ST-1" :client-id "client-1"
                               :name "brand-tracker-2026"
                               :segment-quotas {"18-24" 100}
                               :consent-required? true})
    st))

(def ^:private req {:client-id "client-1"})

(defn- resp [& {:as overrides}]
  (merge {:op :approve-response :effect :propose :study-id "ST-1"
          :segment "18-24" :segment-count-after 50 :consent-obtained true
          :confidence 0.9 :stake :low}
         overrides))

;; ── the registry itself ──────────────────────────────────────────────

(deftest registry-is-closed
  (testing "only the two declared ops are registered"
    (is (= [:approve-quota-reopening :approve-response] (operation/ops)))
    (is (operation/registered? :approve-response))
    (is (not (operation/registered? :exfiltrate-respondent-pii)))))

(deftest problems-fails-closed-on-unregistered-op
  (testing "an unregistered op is a PROBLEM, never an empty problem list"
    (let [probs (operation/problems {:op :exfiltrate-respondent-pii :effect :propose})]
      (is (seq probs) "an empty list here would read as well-formed")
      (is (= :unregistered-operation (:kind (first probs)))))))

(deftest problems-empty-for-a-well-formed-proposal
  (testing "the control: a good proposal has no problems"
    (is (= [] (operation/problems (resp))))))

(deftest problems-names-every-missing-field
  (let [probs (operation/problems {:op :approve-response :effect :propose})]
    (is (= 4 (count probs)))
    (is (= [:consent-obtained :segment :segment-count-after :study-id]
           (mapv :field probs))
        "sorted by field name, so the same bad proposal explains itself the same way")
    (is (every? #(= :missing-field (:kind %)) probs))))

(deftest problems-catches-a-wrong-type
  (testing "the string \"9999\" is not a number, and that must be said"
    (let [probs (operation/problems (resp :segment-count-after "9999"))]
      (is (= 1 (count probs)))
      (is (= {:kind :wrong-type :field :segment-count-after
              :expected :number :got "9999"}
             (first probs))))))

(deftest explain-is-non-empty-for-every-problem-kind
  (doseq [p [{:op :nope} {:op :approve-response :effect :propose}
             (resp :segment-count-after "9999")]]
    (let [text (operation/explain (operation/problems p))]
      (is (seq text) (str "explain returned nothing for " (pr-str p))))))

;; ── the two defects this component was written to close ──────────────
;; Measured 2026-09-10 against the tree at a06b9ae: both returned
;; :ok? true, i.e. a check that could not run answered like a check
;; that ran and found nothing.

(deftest governor-refuses-an-unregistered-operation
  (testing "before operation/registry this reached :ok? true"
    (let [st (fresh-store)
          v (governor/check req {} {:op :exfiltrate-respondent-pii
                                    :effect :propose :confidence 0.99} st)]
      (is (not (:ok? v)))
      (is (:hard? v))
      (is (some #(= :unregistered-operation (:rule %)) (:violations v))))))

(deftest actor-does-not-commit-an-unregistered-operation
  (testing "end to end: the graph committed a record for this op before"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          result (actor/run-request! graph
                                     {:client-id "client-1"
                                      :op :exfiltrate-respondent-pii :stake :low}
                                     {} "thread-unregistered")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "client-1"))
          "an operation nobody defined must not produce a record"))))

(deftest governor-refuses-a-non-numeric-segment-count
  (testing "the quota ceiling only fired on numbers, so a string bypassed it"
    (let [st (fresh-store)
          v (governor/check req {} (resp :segment-count-after "9999" :confidence 0.99) st)]
      (is (not (:ok? v)))
      (is (:hard? v))
      (is (some #(= :malformed-operation (:rule %)) (:violations v))))))

(deftest governor-refuses-a-nil-consent-answer
  (testing "nil means the advisor did not say, and that is not false"
    (let [st (fresh-store)
          v (governor/check req {} (dissoc (resp) :consent-obtained) st)]
      (is (:hard? v))
      (is (some #(= :malformed-operation (:rule %)) (:violations v))))))

(deftest a-good-proposal-still-commits
  (testing "the new rules refuse the malformed WITHOUT refusing the valid —
            a governor that holds everything discriminates nothing"
    (let [st (fresh-store)
          v (governor/check req {} (resp) st)]
      (is (:ok? v))
      (is (empty? (:violations v))))))
