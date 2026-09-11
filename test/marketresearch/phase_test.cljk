(ns marketresearch.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [marketresearch.phase :as phase]
            [marketresearch.actor :as actor]
            [marketresearch.store :as store]))

(defn- graph-nodes []
  (-> (actor/build-graph {:store (store/mem-store)})
      :graph :nodes keys))

(deftest declaration-matches-the-compiled-graph
  (testing "both directions: an undeclared node and an unbuilt phase both fail"
    (let [c (phase/conformance (graph-nodes))]
      (is (:ok? c) (str "undeclared=" (:undeclared c) " missing=" (:missing c)))
      (is (= 0 (:problem-count c))))))

(deftest an-undeclared-node-is-detected
  (testing "the check discriminates — it is not simply always ok"
    (let [c (phase/conformance (conj (vec (graph-nodes)) :exfiltrate))]
      (is (not (:ok? c)))
      (is (= #{:exfiltrate} (:undeclared c))))))

(deftest an-unbuilt-phase-is-detected
  (let [c (phase/conformance (remove #{:hold} (graph-nodes)))]
    (is (not (:ok? c)))
    (is (= #{:hold} (:missing c)))))

(deftest an-empty-graph-cannot-read-as-conformant
  (testing "input absence must not answer like input agreement"
    (let [c (phase/conformance [])]
      (is (not (:ok? c)))
      (is (= (set phase/order) (:missing c))))))

(deftest every-declared-edge-is-legal-and-terminals-are-closed
  (is (phase/legal-transition? :intake :advise))
  (is (phase/legal-transition? :decide :hold))
  (is (not (phase/legal-transition? :intake :commit))
      "intake must not reach commit without passing the governor")
  (is (not (phase/legal-transition? :advise :commit))
      "the advisor must never route straight to a write")
  (doseq [t phase/terminal]
    (is (empty? (get phase/transitions t)) (str t " must be terminal"))))

(deftest the-governor-is-on-every-path-to-commit
  (testing "the unconditional invariant, read off the declaration:
            no phase reaches :commit without :govern upstream"
    (let [reaches-commit (fn [from]
                           (loop [frontier [from] seen #{}]
                             (if (empty? frontier)
                               false
                               (let [p (first frontier)]
                                 (cond
                                   (= :commit p) true
                                   (contains? seen p) (recur (rest frontier) seen)
                                   :else (recur (concat (rest frontier)
                                                        (get phase/transitions p #{}))
                                                (conj seen p)))))))]
      ;; :intake and :advise can only reach :commit by going through
      ;; :govern -> :decide; removing :govern's outgoing edge must cut them off.
      (is (reaches-commit :intake))
      (with-redefs [phase/transitions (assoc phase/transitions :govern #{})]
        (is (not (reaches-commit :intake))
            "if :intake still reaches :commit with :govern cut, the graph bypasses the governor")))))

(deftest disposition-routing-covers-every-decide-branch
  (is (= (set (vals phase/disposition->phase))
         (get phase/transitions :decide))
      "every branch :decide can route to must be a declared transition"))

(deftest phase?-rejects-a-non-phase
  (is (phase/phase? :govern))
  (is (not (phase/phase? :exfiltrate))))
