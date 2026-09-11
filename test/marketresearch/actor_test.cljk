(ns marketresearch.actor-test
  (:require [clojure.test :refer [deftest is testing]]
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

(deftest commits-an-in-quota-consented-response
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-response :stake :low
                 :study-id "ST-1" :segment "18-24" :segment-count-after 50
                 :consent-obtained true}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-unconsented-response
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-response :stake :low
                 :study-id "ST-1" :segment "18-24" :segment-count-after 50
                 :consent-obtained false}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-reopens-quota-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-quota-reopening :stake :high
                 :study-id "ST-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
