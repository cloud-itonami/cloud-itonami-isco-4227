(ns marketresearch.ledger-test
  (:require [clojure.test :refer [deftest is testing]]
            [marketresearch.ledger :as ledger]
            [marketresearch.actor :as actor]
            [marketresearch.store :as store]))

(defn- chain-of [& facts]
  (reduce ledger/append [] facts))

(deftest append-chains-seq-and-prev
  (let [c (chain-of {:a 1} {:a 2} {:a 3})]
    (is (= [0 1 2] (mapv :seq c)))
    (is (= ledger/genesis-prev (:prev (first c))))
    (is (= (:hash (nth c 0)) (:prev (nth c 1))))
    (is (= (:hash (nth c 1)) (:prev (nth c 2))))))

(deftest verify-accepts-an-untouched-chain
  (let [v (ledger/verify (chain-of {:a 1} {:a 2} {:a 3}))]
    (is (:ok? v))
    (is (= 0 (:problem-count v)))
    (is (= 3 (:count v)))))

(deftest verify-accepts-an-empty-chain-and-says-so
  (testing "empty is clean, and :count distinguishes it from a full one —
            a bare true could not"
    (let [v (ledger/verify [])]
      (is (:ok? v))
      (is (= 0 (:count v))))))

(deftest verify-catches-an-in-place-edit
  (testing "the failure a bare vector of facts could not detect at all"
    (let [c (chain-of {:disposition :hold} {:disposition :hold} {:disposition :hold})
          tampered (assoc-in c [1 :fact :disposition] :commit)
          v (ledger/verify tampered)]
      (is (not (:ok? v)))
      (is (= 1 (:problem-count v)) "exactly the edited entry, not the whole tail")
      (is (= :hash-mismatch (:kind (first (:problems v)))))
      (is (= 1 (:at (first (:problems v))))))))

(deftest verify-catches-a-gap
  (let [c (chain-of {:a 1} {:a 2} {:a 3})
        gapped (vec (concat [(nth c 0)] [(nth c 2)]))
        v (ledger/verify gapped)]
    (is (not (:ok? v)))
    (is (pos? (:problem-count v)))
    (is (some #(= :seq-out-of-order (:kind %)) (:problems v)))))

(deftest verify-catches-reordering
  (let [c (chain-of {:a 1} {:a 2} {:a 3})
        swapped [(nth c 0) (nth c 2) (nth c 1)]
        v (ledger/verify swapped)]
    (is (not (:ok? v)))
    (is (pos? (:problem-count v)))))

(deftest problem-count-scales-with-the-damage
  (testing "one edit and a wholly corrupt chain must not report the same —
            this is why verify counts instead of returning a boolean"
    (let [c (chain-of {:a 1} {:a 2} {:a 3} {:a 4})
          one (ledger/verify (assoc-in c [1 :fact :a] :x))
          many (ledger/verify (mapv #(assoc % :hash "0" :prev "0") c))]
      (is (= 1 (:problem-count one)))
      (is (> (:problem-count many) (:problem-count one))))))

(deftest digest-is-stable-and-sensitive
  (is (= (ledger/digest "brand-tracker-2026") (ledger/digest "brand-tracker-2026")))
  (is (not= (ledger/digest "18-24") (ledger/digest "25-34"))))

(deftest explain-distinguishes-clean-from-broken
  (let [clean (ledger/explain (ledger/verify (chain-of {:a 1})))
        broken (ledger/explain (ledger/verify [(assoc (ledger/entry 0 "genesis" {:a 1}) :hash "0")]))]
    (is (re-find #"clean" clean))
    (is (re-find #"BROKEN" broken))))

;; ── the store actually uses it ───────────────────────────────────────

(deftest store-ledger-is-chained-and-verifiable
  (let [st (store/mem-store)]
    (store/append-ledger! st {:disposition :hold})
    (store/append-ledger! st {:disposition :commit})
    (is (= 2 (count (store/ledger-entries st))))
    (is (:ok? (store/ledger-integrity st)))
    (is (= [{:disposition :hold} {:disposition :commit}] (store/ledger st))
        "the facts view stays what existing readers expect")))

(deftest a-real-actor-run-leaves-a-verifiable-trail
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-study! st {:study-id "ST-1" :client-id "client-1"
                               :segment-quotas {"18-24" 100}
                               :consent-required? true})
    (let [graph (actor/build-graph {:store st})]
      (actor/run-request! graph {:client-id "client-1" :op :approve-response
                                 :study-id "ST-1" :segment "18-24"
                                 :segment-count-after 5 :consent-obtained true
                                 :stake :low} {} "t-commit")
      (actor/run-request! graph {:client-id "client-1" :op :approve-response
                                 :study-id "ST-1" :segment "18-24"
                                 :segment-count-after 5 :consent-obtained false
                                 :stake :low} {} "t-hold")
      (let [v (store/ledger-integrity st)]
        (is (= 2 (:count v)) "one commit and one hold were both recorded")
        (is (:ok? v))
        (is (= [:commit :hold] (mapv :disposition (store/ledger st))))))))
