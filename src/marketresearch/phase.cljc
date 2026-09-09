(ns marketresearch.phase
  "The declared phases of one ISCO-08 4227 actor run, and which
  transitions between them are legal (itonami actor pattern,
  ADR-2607011000 / CLAUDE.md Actors section).

  `marketresearch.actor` drew this as an ASCII diagram in its
  docstring, which means the diagram and the compiled graph were two
  copies of one fact with nothing holding them together. A phase added
  to the graph but not to the diagram is exactly the shape CLAUDE.md
  names as `live に見える dead code` — reachable, undocumented, and
  silent about it.

  So the graph is built FROM `order` and the transitions are asserted
  against `transitions`. The declaration is load-bearing: adding a node
  to the graph without declaring it here fails `phase-test`, and
  declaring a phase the graph never builds fails it too. Neither
  direction can pass quietly.

      :intake -> :advise -> :govern -> :decide -+-> :commit           (:ok? true)
                                                +-> :request-approval (:escalate? true, interrupt-before)
                                                +-> :hold             (:hard? true)"
  (:require [clojure.set :as set]))

(def order
  "Every phase of a run, in the order the graph builds them."
  [:intake :advise :govern :decide :request-approval :commit :hold])

(def entry-phase :intake)

(def terminal
  "Phases a run may finish in."
  #{:commit :hold})

(def transitions
  "phase -> the set of phases it may hand control to. `:decide` is the
  only branch point, and its three targets are the three dispositions."
  {:intake           #{:advise}
   :advise           #{:govern}
   :govern           #{:decide}
   :decide           #{:commit :request-approval :hold}
   :request-approval #{:commit}
   :commit           #{}
   :hold             #{}})

(def disposition->phase
  "The verdict disposition each `:decide` branch routes to."
  {:commit :commit :request-approval :request-approval :hold :hold})

(defn phase?
  [p]
  (boolean (some #{p} order)))

(defn legal-transition?
  "May control pass from `from` to `to`?"
  [from to]
  (contains? (get transitions from #{}) to))

(defn undeclared
  "Phases present in `node-keys` that this namespace never declared.
  Fails closed on an empty graph: `(undeclared [])` is `#{}`, but
  `missing` then reports every declared phase, so an empty graph cannot
  read as conformant from the pair."
  [node-keys]
  (set/difference (set node-keys) (set order)))

(defn missing
  "Declared phases that `node-keys` does not build."
  [node-keys]
  (set/difference (set order) (set node-keys)))

(defn conformance
  "Compare a compiled graph's node keys against this declaration.
  Returns counts as well as sets — a boolean could not distinguish one
  stray node from a graph that shares nothing with the declaration."
  [node-keys]
  (let [u (undeclared node-keys) m (missing node-keys)]
    {:undeclared u
     :missing m
     :problem-count (+ (count u) (count m))
     :ok? (and (empty? u) (empty? m))}))
