(ns marketresearch.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [marketresearch.store :as store]
            [marketresearch.governor :as governor]))

(defn- fresh-store [consent-required?]
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-study! st {:study-id "ST-1" :client-id "client-1"
                               :name "brand-tracker-2026"
                               :segment-quotas {"18-24" 100 "25-34" 150}
                               :consent-required? consent-required?})
    st))

(defn- resp [segment count consent]
  {:op :approve-response :effect :propose :study-id "ST-1"
   :segment segment :segment-count-after count :consent-obtained consent
   :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-quota-and-consented
  (let [st (fresh-store true)
        v (governor/check req {} (resp "18-24" 50 true) st)]
    (is (:ok? v))))

(deftest ok-at-exact-quota
  (testing "count exactly at the quota is within margin"
    (let [st (fresh-store false)
          v (governor/check req {} (resp "18-24" 100 false) st)]
      (is (:ok? v)))))

(deftest hard-on-quota-exceeded
  (testing "quota is a number, not a suggestion"
    (let [st (fresh-store false)
          v (governor/check req {} (assoc (resp "18-24" 150 false) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :quota-exceeded (:rule %)) (:violations v))))))

(deftest hard-on-unknown-segment
  (let [st (fresh-store false)
        v (governor/check req {} (resp "65+" 5 false) st)]
    (is (:hard? v))
    (is (some #(= :unknown-segment (:rule %)) (:violations v)))))

(deftest hard-on-consent-not-obtained
  (testing "unconsented recording is a violation, not data"
    (let [st (fresh-store true)
          v (governor/check req {} (assoc (resp "18-24" 50 false) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :consent-not-obtained (:rule %)) (:violations v))))))

(deftest ok-consent-false-when-not-required
  (testing "the consent gate only fires when the study requires it"
    (let [st (fresh-store false)
          v (governor/check req {} (resp "18-24" 50 false) st)]
      (is (:ok? v)))))

(deftest hard-on-unknown-study
  (let [st (fresh-store false)
        v (governor/check req {} (assoc (resp "18-24" 50 false) :study-id "ST-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-study (:rule %)) (:violations v)))))

(deftest hard-on-foreign-study
  (let [st (fresh-store false)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (resp "18-24" 50 false) st)]
      (is (:hard? v))
      (is (some #(= :study-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store false)
        v (governor/check {:client-id "nobody"} {} (resp "18-24" 50 false) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store false)
        v (governor/check req {} (assoc (resp "18-24" 50 false) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest escalates-quota-reopening
  (let [st (fresh-store false)
        v (governor/check req {} {:op :approve-quota-reopening :effect :propose
                                  :study-id "ST-1" :confidence 0.9 :stake :high} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store false)
        v (governor/check req {} (assoc (resp "18-24" 50 false) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
