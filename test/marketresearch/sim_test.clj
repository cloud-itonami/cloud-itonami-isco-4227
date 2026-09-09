(ns marketresearch.sim-test
  (:require [clojure.test :refer [deftest is testing]]
            [marketresearch.sim :as sim]
            [marketresearch.store :as store]
            [marketresearch.actor :as actor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-study! st {:study-id "ST-1" :client-id "client-1"
                               :segment-quotas {"18-24" 100 "25-34" 150}
                               :consent-required? true})
    st))

(def ^:private req {:client-id "client-1"})

(defn- resp [& {:as o}]
  (merge {:op :approve-response :effect :propose :study-id "ST-1"
          :segment "18-24" :segment-count-after 50 :consent-obtained true
          :confidence 0.9 :stake :low}
         o))

(deftest dry-run-counts-each-disposition
  (let [st (fresh-store)
        out (sim/dry-run st req [(resp)                                   ; commit
                                 (resp :segment-count-after 500)          ; over quota -> hold
                                 (resp :consent-obtained false)           ; no consent -> hold
                                 (resp :confidence 0.3)                   ; low conf -> escalate
                                 {:op :nope :effect :propose}])]          ; unregistered -> hold
    (is (= 5 (:total out)))
    (is (= 1 (:ok out)))
    (is (= 3 (:hold out)))
    (is (= 1 (:escalate out)))))

(deftest dry-run-writes-nothing
  (testing "a simulator that quietly commits is worse than no simulator"
    (let [st (fresh-store)
          records-before (vec (store/records-of st "client-1"))
          ledger-before (vec (store/ledger-entries st))]
      (sim/dry-run st req [(resp) (resp :consent-obtained false) (resp :confidence 0.3)])
      (is (= records-before (vec (store/records-of st "client-1"))))
      (is (= ledger-before (vec (store/ledger-entries st)))
          "not one ledger entry may appear from a dry run"))))

(deftest dry-run-agrees-with-a-real-run
  (testing "the preview reads the same verdict keys the actor's :decide does,
            so preview and run cannot disagree"
    (doseq [[label proposal-req expected]
            [["commit" {:op :approve-response :study-id "ST-1" :segment "18-24"
                        :segment-count-after 5 :consent-obtained true :stake :low} :commit]
             ["hold"   {:op :approve-response :study-id "ST-1" :segment "18-24"
                        :segment-count-after 5 :consent-obtained false :stake :low} :hold]]]
      (let [sim-store (fresh-store)
            run-store (fresh-store)
            request (assoc proposal-req :client-id "client-1")
            ;; the advisor mirrors the request into the proposal, so the
            ;; simulated proposal is the one the graph will govern
            previewed (-> (sim/dry-run sim-store request
                                       [(merge {:effect :propose :confidence 0.95}
                                               (select-keys request [:op :study-id :segment
                                                                     :segment-count-after
                                                                     :consent-obtained]))])
                          :verdicts first :disposition)
            actual (-> (actor/run-request! (actor/build-graph {:store run-store})
                                           request {} (str "t-" label))
                       :state :disposition)]
        (is (= expected previewed) (str label ": preview"))
        (is (= expected actual) (str label ": real run"))
        (is (= previewed actual) (str label ": preview and run must agree"))))))

(deftest remaining-capacity-subtracts-committed-responses
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})]
    (is (= {"18-24" 100 "25-34" 150} (sim/remaining-capacity st "client-1" "ST-1")))
    (actor/run-request! graph {:client-id "client-1" :op :approve-response
                              :study-id "ST-1" :segment "18-24"
                              :segment-count-after 1 :consent-obtained true
                              :stake :low} {} "t-1")
    (is (= {"18-24" 99 "25-34" 150} (sim/remaining-capacity st "client-1" "ST-1"))
        "one committed response consumes exactly one slot, in its own segment")))

(deftest remaining-capacity-is-nil-for-an-unregistered-study
  (testing "nil, not {} — an unregistered study has no capacity to report,
            and {} would read as registered-and-everything-full"
    (let [st (fresh-store)]
      (is (nil? (sim/remaining-capacity st "client-1" "ST-ghost")))
      (is (some? (sim/remaining-capacity st "client-1" "ST-1"))))))

(deftest dry-run-of-an-empty-batch-is-empty-not-ok
  (testing "input absence must be visible: totals are 0, not a pass"
    (let [out (sim/dry-run (fresh-store) req [])]
      (is (= 0 (:total out)))
      (is (= 0 (:ok out)))
      (is (empty? (:verdicts out))))))
