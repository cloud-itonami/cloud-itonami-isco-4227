(ns marketresearch.ledger
  "The append-only audit trail for the ISCO-08 4227 community survey &
  market research interviewers actor (itonami actor pattern,
  ADR-2607011000 / CLAUDE.md Actors section).

  Before this namespace the store kept the trail as a bare vector of
  whatever map the graph handed it, so nothing distinguished \"three
  dispositions were recorded\" from \"three dispositions were recorded
  and the middle one was edited afterwards\". An audit trail that
  cannot answer that is a log, not a ledger.

  Every entry carries a gap-free `:seq`, the previous entry's `:hash`
  as `:prev`, and its own `:hash` over (seq, prev, fact). `verify`
  recomputes the whole chain and reports every break it finds, so
  reordering, a gap, a truncation from the middle, and an in-place edit
  are each detectable rather than invisible.

  ⚠ `digest` is NOT cryptographic. It detects accidental corruption,
  reordering, gaps and edits made by something that did not set out to
  forge a chain. It does not defend against a motivated forger, who can
  recompute the tail freely. Anything needing a real commitment must
  chain to a cryptographic digest — this workspace already has sha2
  (`org-nist-sha2`) and a durable content-addressed plane
  (`kotobase.net`, ADR-2608159100) for exactly that. This is a local
  integrity check sized to the problem it actually solves.

  `verify` returns a COUNT of problems, not a boolean: a boolean cannot
  tell one edited entry from a wholly corrupt chain."
  (:require [clojure.string :as str]))

(def genesis-prev
  "The `:prev` of the first entry. A real value rather than nil so a
  chain missing its head is a break, not an absence."
  "genesis")

(def ^:private modulus
  ;; Prime below 2^26. Every intermediate below stays under 2^33, so the
  ;; arithmetic is exact in Clojure (long) AND ClojureScript (double,
  ;; exact only below 2^53) — the same fact must hash the same on both
  ;; hosts or a chain written on one host fails to verify on the other.
  67108859)

(def ^:private base 131)

(defn digest
  "Deterministic non-cryptographic digest of `s`, as a decimal string.
  See the namespace docstring — this is corruption detection, not a
  cryptographic commitment."
  [s]
  (let [s (str s)]
    (loop [i 0 h 7]
      (if (>= i (count s))
        (str h)
        (recur (inc i)
               (mod (+ (* h base) #?(:clj  (int (.charAt ^String s i))
                                     :cljs (.charCodeAt s i)))
                    modulus))))))

(defn- hash-of [seq-no prev fact]
  (digest (pr-str [seq-no prev fact])))

(defn entry
  "Build the entry that appends `fact` after `prev-hash` at `seq-no`."
  [seq-no prev-hash fact]
  {:seq  seq-no
   :prev prev-hash
   :fact fact
   :hash (hash-of seq-no prev-hash fact)})

(defn append
  "Append `fact` to `entries` (a vector, possibly empty). Pure — returns
  the new vector."
  [entries fact]
  (let [entries (vec entries)
        prev    (if-let [last-e (peek entries)] (:hash last-e) genesis-prev)]
    (conj entries (entry (count entries) prev fact))))

(defn facts
  "Just the recorded facts, in order — for readers that want the trail's
  contents and not its integrity."
  [entries]
  (mapv :fact entries))

(defn verify
  "Recompute the chain. Returns
  `{:count n :problems [...] :problem-count n :ok? bool}`.

  `:problem-count` is the point: a boolean cannot distinguish one
  edited entry from a wholly corrupt chain, and this fleet has been
  bitten by self-checks that could only say \"something failed\"."
  [entries]
  (let [entries (vec entries)
        probs
        (into []
              (mapcat
               (fn [i]
                 (let [{:keys [seq prev fact hash] :as e} (nth entries i)
                       expected-prev (if (zero? i) genesis-prev (:hash (nth entries (dec i))))]
                   (cond-> []
                     (not= i seq)
                     (conj {:kind :seq-out-of-order :at i :expected i :got seq})

                     (not= expected-prev prev)
                     (conj {:kind :prev-link-broken :at i :expected expected-prev :got prev})

                     (not= hash (hash-of seq prev fact))
                     (conj {:kind :hash-mismatch :at i :expected (hash-of seq prev fact) :got hash})

                     (not (contains? e :fact))
                     (conj {:kind :fact-missing :at i})))))
              (range (count entries)))]
    {:count         (count entries)
     :problems      probs
     :problem-count (count probs)
     :ok?           (zero? (count probs))}))

(defn explain
  "One-line operator-facing rendering of `verify` output."
  [{:keys [count problem-count problems]}]
  (if (zero? problem-count)
    (str "ledger clean: " count " entr" (if (= 1 count) "y" "ies"))
    (str "ledger BROKEN: " problem-count " problem(s) over " count " entries — "
         (str/join "; " (map (fn [{:keys [kind at]}] (str (name kind) "@" at)) problems)))))
