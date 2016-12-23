;   Copyright (c) Alan Thompson. All rights reserved.
;   The use and distribution terms for this software are covered by the Eclipse Public License 1.0
;   (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at
;   the root of this distribution.  By using this software in any fashion, you are agreeing to be
;   bound by the terms of this license.  You must not remove this notice, or any other, from this
;   software.
(ns tupelo.core
  "Tupelo - Making Clojure even sweeter"
  (:require 
    [clojure.core :as clj]
    [clojure.core.match :as ccm]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [clojure.set :as set]
    [cheshire.core :as cc]
    [schema.core :as sk]
    [tupelo.types :as types]
    [tupelo.schema :as ts]
  )
  (:import [java.io BufferedReader StringReader])
)

   ;[clojure.spec :as sp]
   ;[clojure.spec.gen :as sp.gen]

; Prismatic Schema type definitions
(sk/set-fn-validation! true)  ; #todo add to Schema docs
; #todo add to project.clj (esp for tupelo-app template, user/dev profile)

(defn print-versions []
  (let [version-str   (format "   Clojure %s    Java %s"  
                        (clojure-version)
                        (System/getProperty "java.version")) ]
    (newline)
    (println "-------------------------------------")
    (println version-str)
    (println "-------------------------------------")
  ))

(def ^:no-doc spy-indent-level (atom 0))
(defn ^:no-doc spy-indent-spaces []
  (str/join (repeat (* 2 @spy-indent-level) \space)))

(defn ^:no-doc spy-indent-reset
  "Reset the spy indent level to zero."
  []
  (reset! spy-indent-level 0))

(defn ^:no-doc spy-indent-inc
  "Increase the spy indent level by one."
  []
  (swap! spy-indent-level inc))

(defn ^:no-doc spy-indent-dec
  "Decrease the spy indent level by one."
  []
  (swap! spy-indent-level dec))

(defn spy
  "A form of (println ...) to ease debugging display of either intermediate values in threading
   forms or function return values. There are three variants.  Usage:

    (spy :msg <msg-string>)
        This variant is intended for use in either thread-first (->) or thread-last (->>)
        forms.  The keyword :msg is used to identify the message string and works equally
        well for both the -> and ->> operators. Spy prints both <msg-string>  and the
        threading value to stdout, then returns the value for further propogation in the
        threading form. For example, both of the following:
            (->   2
                  (+ 3)
                  (spy :msg \"sum\" )
                  (* 4))
            (->>  2
                  (+ 3)
                  (spy :msg \"sum\" )
                  (* 4))
        will print 'sum => 5' to stdout.

    (spy <msg-string> <value>)
        This variant is intended for simpler use cases such as function return values.
        Function return value expressions often invoke other functions and cannot be
        easily displayed since (println ...) swallows the return value and returns nil
        itself.  Spy will output both <msg-string> and the value, then return the value
        for use by further processing.  For example, the following:
            (println (* 2
                       (spy \"sum\" (+ 3 4))))
      will print:
            sum => 7
            14
      to stdout.

    (spy <value>)
        This variant is intended for use in very simple situations and is the same as the
        2-argument arity where <msg-string> defaults to 'spy'.  For example (spy (+ 2 3))
        prints 'spy => 5' to stdout.  "
  ([arg1 arg2 arg3]
   (cond (= :msg arg1) (do (println (str (spy-indent-spaces) arg2 " => " (pr-str arg3))) arg3) ; ->>  case
         (= :msg arg2) (do (println (str (spy-indent-spaces) arg3 " => " (pr-str arg1))) arg1) ; ->   case
         :else (throw (IllegalArgumentException. (str
                                                   "spy: either first or 2nd arg must be :msg \n   args:"
                                                   (pr-str [arg1 arg2 arg3]))))))

  ([msg value]                                              ; 2-arg arity assumes value is last arg
   (spy :msg msg value))

  ([value]                                                  ; 1-arg arity uses a generic "spy" message
   (spy :msg "spy" value)))

; #todo need to write (spy-let ...) => (let [ x 1  _ (spyx x)
;                                             y 2  _ (spyx y) ]   ...)

; #todo allos spyx to have labels like (spyx :dbg-120 (+ 1 2)):  ":dbg-120 (+ 1 2) => 3"
(defmacro spyx
  "An expression (println ...) for use in threading forms (& elsewhere). Evaluates the supplied
   expression, printing both the expression and its value to stdout, then returns the value."
  [expr]
  `(let [spy-val# ~expr]
     (println (str (spy-indent-spaces) '~expr " => " (pr-str spy-val#)))
     spy-val#))

(defmacro spyxx
  "An expression (println ...) for use in threading forms (& elsewhere). Evaluates the supplied
   expression, printing both the expression, its type, and its value to stdout, then returns the value."
  [expr]
  `(let [spy-val#    ~expr
         class-name# (-> spy-val# class .getName)]
     (println (str (spy-indent-spaces) '~expr " => " class-name# "->" (pr-str spy-val#)))
     spy-val#))

(defmacro with-spy-indent
  "Increments indentation level of all spy, spyx, or spyxx expressions within the body."
  [& body]
  `(do
     (spy-indent-inc)
     (let [result# (do ~@body)]
       (spy-indent-dec)
       result#)))

; #todo need (dbg :awt122 (some-fn 1 2 3)) -> (spy :msg :awt122 (some-fn 1 2 3))

; original
#_(s/defn truthy? :- sk/Bool
    "Returns true if arg is logical true (neither nil nor false); otherwise returns false."
    [arg :- sk/Any]
    (if arg true false))

(defn truthy?
  "Returns true if arg is logical true (neither nil nor false); otherwise returns false."
  [arg]
  (if arg true false))

; #todo how to test the :ret part?
; (sp/fdef truthy?
;   :args (sp/cat :arg any?)
;   :ret  boolean?)

(sk/defn falsey? :- sk/Bool
  "Returns true if arg is logical false (either nil or false); otherwise returns false. Equivalent
   to (not (truthy? arg))."
  [arg :- sk/Any]
  (if arg false true))

(defn validate
  "(validate tstfn tstval)
  Used to validate intermediate results. Returns tstval if the result of
  (tstfn tstval) is truthy.  Otherwise, throws IllegalStateException."
  [tstfn tstval]
  (let [tst-result (tstfn tstval)]
    (when-not (truthy? tst-result)
      (throw (IllegalStateException. (str "validation failure, tst-result=" tst-result))))
    tstval))

; #todo -> README
(sk/defn has-some? :- sk/Bool ; #todo rename to has-any?   Add warning re new clj/any?
  "For any predicate pred & collection coll, returns true if (pred x) is logical true for at least one x in
   coll; otherwise returns false.  Like clojure.core/some, but returns only true or false."
  [pred :-  sk/Any
   coll :- [sk/Any] ]
  (truthy? (some pred coll)))
    ; NOTE: was `any?` prior to new `clojure.core/any?` added in clojure 1.9.0-alpha10

; #todo -> README
(sk/defn has-none? :- sk/Bool
  "For any predicate pred & collection coll, returns false if (pred x) is logical true for at least one x in
   coll; otherwise returns true.  Equivalent to clojure.core/not-any?, but inverse of has-some?."
  [pred :-  sk/Any
   coll :- [sk/Any] ]
  (falsey? (some pred coll)))

; #todo -> README
(sk/defn contains-elem? :- sk/Bool
  "For any collection coll & element tgt, returns true if coll contains at least one
  instance of tgt; otherwise returns false. Note that, for maps, each element is a
  vector (i.e MapEntry) of the form [key value]."
  [coll :- sk/Any
   tgt  :- sk/Any ]
  (has-some? #{tgt} (seq coll)))

; #todo -> README
(sk/defn contains-key? :- sk/Bool
  "For any predicate pred & a map or set coll, returns true if (pred x) is logical true for at least one x in
   coll; otherwise returns false.  Like clojure.core/some, but returns only true or false."
  [map-or-set :- (sk/pred #(or (map? %) (set? %)))
   elem :- sk/Any ]
  (contains? map-or-set elem))

; #todo -> README
(sk/defn contains-val? :- sk/Bool
  "For any predicate pred & collection coll, returns true if (pred x) is logical true for at least one x in
   coll; otherwise returns false.  Like clojure.core/some, but returns only true or false."
  [map :- ts/Map
   elem :- sk/Any ]
  (has-some? #{elem} (vals map)))

(sk/defn not-nil? :- sk/Bool
  "Returns true if arg is not nil; false otherwise. Equivalent to (not (nil? arg)),
   or the poorly-named clojure.core/some? "
  [arg :- sk/Any]
  (not (nil? arg)))

(sk/defn not-empty? :- sk/Bool
  "For any collection coll, returns true if coll contains any items; otherwise returns false.
   Equivalent to (not (empty? coll))."
  ; [coll :- [s/Any]]  ; #todo extend Prismatic Schema to accept this for strings
  [coll]
  (not (empty? coll)))

; #todo:  mapz, forz, filterz, ...?

(defmacro forv
  "Like clojure.core/for but returns results in a vector.  Equivalent to (into [] (for ...)). Not
   lazy."
  [& body]
  `(vec (for ~@body)))

(defn glue
  "Glues together like collections:

     (glue [1 2] [3 4] [5 6])                -> [1 2 3 4 5 6]
     (glue {:a 1} {:b 2} {:c 3})             -> {:a 1 :c 3 :b 2}
     (glue #{1 2} #{3 4} #{6 5})             -> #{1 2 6 5 3 4}
     (glue \"I\" \" like \" \\a \" nap!\" )  -> \"I like a nap!\"

   If you want to convert to a sorted set or map, just put an empty one first:

     (glue (sorted-map) {:a 1} {:b 2} {:c 3})      -> {:a 1 :b 2 :c 3}
     (glue (sorted-set) #{1 2} #{3 4} #{6 5})      -> #{1 2 3 4 5 6}

   If there are duplicate keys when using glue for maps or sets, then \"the last one wins\":

     (glue {:band :VanHalen :singer :Dave}  {:singer :Sammy}) "
  [& colls]
  (let [string-or-char? #(or (string? %) (char? %))]
    (cond
      (every? sequential? colls)        (reduce into [] colls) ; coerce to vector result
      (every? map? colls)               (reduce into    colls) ; first item determines type of result
      (every? set? colls)               (reduce into    colls) ; first item determines type of result
      (every? string-or-char? colls)    (apply str colls)
      :else (throw (IllegalArgumentException.
                     (str "colls must be all same type; found types=" (mapv type colls)))))))
; #todo look at using (ex-info ...)

(sk/defn append :- ts/List
  "Given a sequential object (vector or list), add one or more elements to the end."
  [listy :- ts/List
   & elems :- [sk/Any]]
  (when-not (sequential? listy)
    (throw (IllegalArgumentException. (str "Sequential collection required, found=" listy))))
  (when (empty? elems)
    (throw (IllegalArgumentException. (str "Nothing to append! elems=" elems))))
  (vec (concat listy elems)))

(sk/defn prepend :- ts/List
  "Given a sequential object (vector or list), add one or more elements to the beginning"
  [& args]
  (let [elems (butlast args)
        listy (last args)]
    (when-not (sequential? listy)
      (throw (IllegalArgumentException. (str "Sequential collection required, found=" listy))))
    (when (empty? elems)
      (throw (IllegalArgumentException. (str "Nothing to prepend! elems=" elems))))
    (vec (concat elems listy))))

; #todo force to vector result
(sk/defn drop-at :- ts/List
  "Removes an element from a collection at the specified index."
  [coll :- ts/List
   index :- sk/Int]
  (when (neg? index)
    (throw (IllegalArgumentException. (str "Index cannot be negative: " index))))
  (when (<= (count coll) index)
    (throw (IllegalArgumentException. (str "Index cannot exceed collection length: "
                                        " (count coll)=" (count coll) " index=" index))))
  (glue (take index coll)
    (drop (inc index) coll)))

; #todo force to vector result
(sk/defn insert-at :- ts/List
  "Inserts an element into a collection at the specified index."
  [coll :- ts/List
   index :- sk/Int
   elem :- sk/Any]
  (when (neg? index)
    (throw (IllegalArgumentException. (str "Index cannot be negative: " index))))
  (when (< (count coll) index)
    (throw (IllegalArgumentException. (str "Index cannot exceed collection length: "
                                        " (count coll)=" (count coll) " index=" index))))
  (glue (take index coll) [elem]
    (drop index coll)))

; #todo force to vector result
; #todo if was vector, could just use (assoc the-vec idx new-val)
(sk/defn replace-at :- ts/List
  "Replaces an element in a collection at the specified index."
  [coll :- ts/List
   index :- sk/Int
   elem :- sk/Any]
  (when (neg? index)
    (throw (IllegalArgumentException. (str "Index cannot be negative: " index))))
  (when (<= (count coll) index)
    (throw (IllegalArgumentException. (str "Index cannot exceed collection length: "
                                        " (count coll)=" (count coll) " index=" index))))
  (glue (take index coll) 
        [elem]
        (drop (inc index) coll)))

; As of Clojure 1.9.0-alpha5, seqable? is native to clojure
(sk/defn ^{:deprecated "1.9.0-alpha5"} seqable? :- sk/Bool ; from clojure.contrib.core/seqable
    "Returns true if (seq x) will succeed, false otherwise."
    [x :- sk/Any]
    (or (seq? x)
      (instance? clojure.lang.Seqable x)
      (nil? x)
      (instance? Iterable x)
      (-> x .getClass .isArray)
      (string? x)
      (instance? java.util.Map x)))

; #todo rename to "get-in-safe" ???
(sk/defn fetch-in :- sk/Any
  "A fail-fast version of clojure.core/get-in. When invoked as (fetch-in the-map keys-vec),
   returns the value associated with keys-vec as for (clojure.core/get-in the-map keys-vec).
   Throws an Exception if the path keys-vec is not present in the-map."
  [the-map :- ts/KeyMap
   keys-vec :- [sk/Keyword]]
  (let [result (get-in the-map keys-vec ::not-found)]
    (if (= result ::not-found)
      (throw (IllegalArgumentException.
               (str "Key seq not present in map:" \newline
                 "  map : " the-map \newline
                 "  keys: " keys-vec \newline)))
      result)))

; #todo make inverse named "get-safe" ???

(sk/defn grab :- sk/Any
  "A fail-fast version of keyword/map lookup.  When invoked as (grab :the-key the-map),
   returns the value associated with :the-key as for (clojure.core/get the-map :the-key).
   Throws an Exception if :the-key is not present in the-map."
  [the-key :- sk/Keyword
   the-map :- ts/KeyMap]
  (fetch-in the-map [the-key]))

(sk/defn dissoc-in :- sk/Any
  "A sane version of dissoc-in that will not delete intermediate keys.
   When invoked as (dissoc-in the-map [:k1 :k2 :k3... :kZ]), acts like
   (clojure.core/update-in the-map [:k1 :k2 :k3...] dissoc :kZ). That is, only
   the map entry containing the last key :kZ is removed, and all map entries
   higher than kZ in the hierarchy are unaffected."
  [the-map :- ts/KeyMap
   keys-vec :- [sk/Keyword]]
  (let [num-keys     (count keys-vec)
        key-to-clear (last keys-vec)
        parent-keys  (butlast keys-vec)]
    (cond
      (zero? num-keys) the-map
      (= 1 num-keys) (dissoc the-map key-to-clear)
      :else (update-in the-map parent-keys dissoc key-to-clear))))

; #todo:  add in clear-nil-entries to recursively delete all k-v pairs where val is nil or empty?

; #todo:  create safe-map ns with non-nil/non-dup versions of assoc-in, update-in, dissoc-in (&
; singles). Basically like compiler-like guarentees against misspellings, duplicate entries, missing
; entries.

(sk/defn only :- sk/Any
  "(only seqable-arg)
  Ensures that a sequence is of length=1, and returns the only value present.
  Throws an exception if the length of the sequence is not one.
  Note that, for a length-1 sequence S, (first S), (last S) and (only S) are equivalent."
  [seqable-arg :- sk/Any]
  (when-not (seqable? seqable-arg)
    (throw (IllegalArgumentException. (str "only: arg not seqable:" seqable-arg))))
  (let [seq-len (count seqable-arg)]
    (when-not (= 1 seq-len)
      (throw (IllegalArgumentException. (str "only: length != 1; length=" seq-len)))))
  (first seqable-arg))

(sk/defn third :- sk/Any
  "Returns the third item in a collection, or nil if fewer than three items are present. "
  [seqable-arg :- sk/Any]
  (when-not (seqable? seqable-arg)
    (throw (IllegalArgumentException. (str "only: arg not seqable:" seqable-arg))))
  (first (next (next seqable-arg))))

(defn keyvals
  "For any map m, returns the (alternating) keys & values of m as a vector, suitable for reconstructing m via
   (apply hash-map (keyvals m)). (keyvals {:a 1 :b 2} => [:a 1 :b 2] "
  [m]
  {:pre  [(map? m)]
   :post [(vector? %)]}
  (reduce #(conj %1 (first %2) (second %2))
    [] (seq m)))

; #awt #todo: Test failure of (safe-> 3 (* 2) (+ 1))
; #awt #todo: add tests
; #awt #todo: finish safe->>
(defmacro safe->
  "When expr is not nil, threads it into the first form (via ->), and when that result is not nil,
   through the next etc.  If result is nil, throw IllegalArgumentException"
  [expr & forms]
  (let [g     (gensym)
        pstep (fn [step] `(if (nil? ~g)
                            (throw (IllegalArgumentException. (str "Nil value passed to form '" ~step \')))
                            ; #awt #todo: remove unneeded test above ^^^
                            #_(do (println "g=" ~g) (spyxx (-> ~g ~step)))
                            ;; (def mm {:a {:b 2}})
                            ;; user=> (safe-> mm (:aa) (:b))
                            ;; g= {:a {:b 2}}
                            ;; NullPointerException   user/eval1850 (form-init5653535826905962071.clj:1)
                            ;; user=> (safe-> mm :aa :b)   ; works
                            (let [result# (-> ~g ~step)]
                              (if (nil? result#)
                                (throw (IllegalArgumentException. (str "Nil value returned from form '" ~step \')))
                                result#))))
        ]
    `(let [~g ~expr
           ~@(interleave (repeat g) (map pstep forms))]
       ~g)))

; #todo make an it?-> (fff ppp it? qqq) to halt thread if encounter nil result (then returns nil)
; #todo make an it!-> (fff ppp it! qqq) to throw if encounter nil (replace safe->) (maybe val->)

(defmacro it->
  "A threading macro like as-> that always uses the symbol 'it' as the placeholder for the next threaded value:
      (it-> 1
            (inc it)
            (+ it 3)
            (/ 10 it))
      ;=> 2
   "
  [expr & forms]
  `(let [~'it ~expr
         ~@(interleave (repeat 'it) forms)
         ]
     ~'it))

(defmacro with-exception-default
  "Evaluates body & returns its result.  In the event of an exception, default-val is returned
   instead of the exception."
  [default-val & body]
  `(try
     ~@body
     (catch Exception e# ~default-val)))

; (defn round [dblVal :incr   (/ 1 3)]            ; #todo add
;   (let [factor (Math/pow 10 *digits*)]
;     (it-> dblVal
;           (* it factor)
;           (Math/round it)
;           (/ it factor))))
; (defn round [dblVal :exp -2]
;   (round dblVal :incr (Math/pow 10 *digits*)))
; (defn round [dblVal :digits 2]
;   (round dblVal :exp (- *digits*)))

(defn rel=
  "Returns true if 2 double-precision numbers are relatively equal, else false.  Relative equality
   is specified as either (1) the N most significant digits are equal, or (2) the absolute
   difference is less than a tolerance value.  Input values are coerced to double before comparison.
   Example:

     (rel= 123450000 123456789   :digits 4   )  ; true
     (rel= 1         1.001       :tol    0.01)  ; true
   "
  [val1 val2 & {:as opts}]
  {:pre  [(number? val1) (number? val2)]
   :post [(contains? #{true false} %)]}
  (let [{:keys [digits tol]} opts]
    (when-not (or digits tol)
      (throw (IllegalArgumentException.
               (str "Must specify either :digits or :tol" \newline
                 "opts: " opts))))
    (when tol
      (when-not (number? tol)
        (throw (IllegalArgumentException.
                 (str ":tol must be a number" \newline
                   "opts: " opts))))
      (when-not (pos? tol)
        (throw (IllegalArgumentException.
                 (str ":tol must be positive" \newline
                   "opts: " opts)))))
    (when digits
      (when-not (integer? digits)
        (throw (IllegalArgumentException.
                 (str ":digits must be an integer" \newline
                   "opts: " opts))))
      (when-not (pos? digits)
        (throw (IllegalArgumentException.
                 (str ":digits must positive" \newline
                   "opts: " opts)))))
    ; At this point, there were no invalid args and at least one of either :tol and/or
    ; :digits was specified.  So, return the answer.
    (let [val1      (double val1)
          val2      (double val2)
          delta-abs (Math/abs (- val1 val2))
          or-result (truthy?
                      (or (zero? delta-abs)
                        (and tol
                          (let [tol-result (< delta-abs tol)]
                            tol-result))
                        (and digits
                          (let [abs1          (Math/abs val1)
                                abs2          (Math/abs val2)
                                max-abs       (Math/max abs1 abs2)
                                delta-rel-abs (/ delta-abs max-abs)
                                rel-tol       (Math/pow 10 (- digits))
                                dig-result    (< delta-rel-abs rel-tol)]
                            dig-result))))
          ]
      or-result)))

(defn rng
  "An eager version clojure.core/range that always returns its result in a vector."
  [& args]
  (vec (apply range args)))

; #todo need docs & tests
; #todo:  add (thru a b)     -> [a..b] (inclusive)
;             (thru 1 3)     -> [ 1  2  3]
;             (thru \a \c)   -> [\a \b \c]
;             (thru :a :c)   -> [:a :b :c]
;             (thru 'a 'c)   -> ['a 'b 'c]
;             (thru 1   2   0.1)     -> [1.0  1.1  1.2 ... 2.0]
;             (thru 0.1 0.3 0.1)     -> [0.1  0.2  0.3]
;                  (thru start stop step) uses integer steps and
;                  (rel= curr stop :tol step) as ending criteria
;       (thru :cc 1 5)   -> [1 2 3 4 5]
;       (thru :oc 1 5)   -> [  2 3 4 5]
;       (thru :co 1 5)   -> [1 2 3 4  ]  ; like (range ...)
;       (thru :oo 1 5)   -> [  2 3 4  ]
(defn thru
  "Returns a sequence of integers. Like clojure.core/rng, but is inclusive of the right boundary value. "
  ([end]
   (rng (inc end)))
  ([start end]
   (rng start (inc end)))
  ([start end step]
   (let [delta          (- (double end) (double start))
         nsteps-dbl     (/ (double delta) (double step))
         nsteps-int     (Math/round nsteps-dbl)
         rounding-error (Math/abs (- nsteps-dbl nsteps-int)) ]
     (when (< 0.00001 rounding-error)
       (throw (IllegalArgumentException. (str
                                           "thru: non-integer number of steps \n   args:"
                                           (pr-str [start end step])))))
     (it-> (inc nsteps-int)
           (rng it)
           (map #(* step %) it)
           (map #(+ start %) it)
           (vec it))))
)

(defn keep-if
  "Returns a vector of items in coll for which (pred item) is true (alias for clojure.core/filter)"
  [pred coll]
  (cond
    (sequential? coll) (vec (clojure.core/filter pred coll))
    (map? coll) (reduce-kv (fn [cum-map k v]
                             (if (pred k v)
                               (assoc cum-map k v)
                               cum-map))
                  {}
                  coll)
    (set? coll) (reduce (fn [cum-set elem]
                          (if (pred elem)
                            (conj cum-set elem)
                            cum-set))
                  #{}
                  (seq coll))
    :else (throw (IllegalArgumentException.
                   (str "keep-if: coll must be sequential, map, or set, class=" (class coll))))))

(defn drop-if
  "Returns a vector of items in coll for which (pred item) is false (alias for clojure.core/remove)"
  [pred coll]
  (keep-if (complement pred) coll))

(defn strcat
  "Recursively concatenate all arguments into a single string result."
  [& args]
  (let [
        ; We need to use flatten twice since the inner one doesn't change a string into a
        ; sequence of chars, nor does it affect byte-array, et al.  We eventually get
        ; seq-of-scalars which can look like [ \a \b 77 78 \66 \z ]
        seq-of-scalars (flatten
                         (for [it (keep-if not-nil? (flatten [args])) ]
                           ; Note that "sequential?" returns false for sets, strings, and the various
                           ; array types.
                           (if (or (sequential? it)
                                 (set? it)
                                 (string? it)
                                 (types/byte-array? it)
                                 (types/char-array? it)
                                 (types/int-array? it)
                                 (types/long-array? it)
                                 (types/short-array? it)
                                 (types/object-array? it))
                             (seq it)
                             it)))
        ; Coerce any integer values into character equivalents (e.g. 65 -> \A), then combine
        ; into a single string.
        result         (apply str
                         (map char
                           (keep-if not-nil? seq-of-scalars)))
        ]
    result))

; #todo add test & README
(defn pp-str
  "Returns a string that is the result of clojure.pprint/pprint"
  [arg]
  (with-out-str (pprint/pprint arg)))

; #todo rename to pp or pprint ?
; #todo add test & README
(defn pretty                                                ; #todo experimental
  "Shortcut to clojure.pprint/pprint"
  [& args]
  (apply pprint/pprint args))

; #todo add test & README
; #todo rename json->edn  ???
(defn json->clj [arg]                                       ; #todo experimental
  "Shortcut to cheshire.core/parse-string"
  (cc/parse-string arg true))                               ; true => keywordize-keys

; #todo add test & README
(defn clj->json [arg]                                       ; #todo experimental
  "Shortcut to cheshire.core/generate-string"
  (cc/generate-string arg))

(defn clip-str      ; #todo -> tupelo.string?
  "Converts all args to single string and clips any characters beyond nchars."
  [nchars & args]
  (it-> (apply str args)
    (take nchars it)
    (apply str it)))

; #todo need test & README
(sk/defn submap? :- Boolean
  "Returns true if the map entries (key-value pairs) of one map are a subset of the entries of
   another map.  Similar to clojure.set/subset?"
  [inner-map :- {sk/Any sk/Any}                           ; #todo
   outer-map :- {sk/Any sk/Any}]                          ; #todo
  (let [inner-set (set inner-map)
        outer-set (set outer-map)]
    (set/subset? inner-set outer-set)))

;                                               "1234.4567.89ab.cdef"  also valid for read
; #todo need conversion from Long -> hex string="1234-4567-89ab-cdef" (& inverse)
; #todo need rand-id/randid/rid/rid-str (rand id) -> 64 bit hex string="1234-4567-89ab-cdef"
; i[12] = Random.nextInt(); bytes += i[12].toHexString()

(defn- wild-match-1
  [pattern value]
  (with-spy-indent
    ; (spy :msg "pattern" pattern) (spy :msg "value  " value) (flush)       ; for debug
    (let [result (truthy?
                   (cond
                     (= pattern :*) true
                     (= pattern value) true
                     (map? pattern) (wild-match-1 (seq (glue (sorted-map) pattern))
                                      (seq (glue (sorted-map) value)))
                     (coll? value) (and (= (count pattern) (count value))
                                     (every? truthy? (mapv wild-match-1 pattern value)))
                     :default false))
          ]
      ; (spy :msg "result " result) (flush)      ; for debug
      result)))

(defn wild-match?
  "Returns true if a pattern is matched by one or more values.  The special keyword :* (colon-star)
   in the pattern serves as a wildcard value.  Note that a wildcald can match either a primitive or a
   composite value: Usage:

     (wild-match? pattern & values)

   samples:

     (wild-match?  {:a :* :b 2}
                   {:a 1  :b 2})         ;=> true

     (wild-match?  [1 :* 3]
                   [1 2  3]
                   [1 9  3] ))           ;=> true

     (wild-match?  {:a :*       :b 2}
                   {:a [1 2 3]  :b 2})   ;=> true "
  [pattern & values]
  (every? truthy?
    (forv [value values]
      (if (map? pattern)
        (wild-match-1 (glue (sorted-map) pattern)
          (glue (sorted-map) value))
        (wild-match-1 pattern value)))))

(defmacro matches?
  "A shortcut to clojure.core.match/match to aid in testing.  Returns true if the data value
   matches the pattern value.  Underscores serve as wildcard values. Usage:

     (matches? pattern & values)

   sample:

     (matches?  [1 _ 3] [1 2 3] )         ;=> true
     (matches?  {:a _ :b _       :c 3}
                {:a 1 :b [1 2 3] :c 3}
                {:a 2 :b 99      :c 3}
                {:a 3 :b nil     :c 3} )  ;=> true

   Note that a wildcald can match either a primitive or a composite value."
  [pattern & values]
  `(and ~@(forv [value values]
            `(ccm/match ~value
               ~pattern true
               :else false))))

; #todo: add (throwed? ...) for testing exceptions

; #todo readme
(sk/defn starts-with? :- sk/Bool
  "Returns true when the initial elements of coll match those of tgt"
  [coll tgt]    ; #todo schema
  (let [tgt-vec (vec tgt)]
    (if (< (count coll) (count tgt-vec))
      false
      (let [coll-vals (take (count tgt-vec) coll)]
        (= coll-vals tgt-vec)))))

; #todo readme
(defn split-when    ; #todo schema
  "Splits a collection based on a predicate with a collection argument.
  Finds the first index N such that (pred (drop N coll)) is true. Returns a length-2 vector
  of [ (take N coll) (drop N coll) ]. If pred is never satisified, [ coll [] ] is returned."
  [pred coll]
  (loop [left  []
         right (vec coll)]
    (if (or (pred right) (empty? right))
      [left right]
      (recur  (append left (first right))
        (rest right)))))

; #todo readme
(defn split-match    ; #todo schema
  "Splits a collection src by matching with a sub-sequence tgt of length L.
  Finds the first index N such that (= tgt (->> coll (drop N) (take L))) is true.
  Returns a length-2 vector of [ (take N coll) (drop N coll) ].
  If no match is found, [ coll [] ] is returned."
  [coll tgt]
  (split-when
    (fn [partial-coll] (starts-with? partial-coll (vec tgt)))
    coll))

;-----------------------------------------------------------------------------
; testing macros

; #todo add tests
(defmacro isnt      ; #todo document in readme
  "Use (isnt ...) instead of (is (not ...)) for clojure.test"
  [& body]
  `(clojure.test/is (not ~@body)))

; #todo add tests
(defmacro is=  ; #todo document in readme
  "Use (is= ...) instead of (is (= ...)) for clojure.test"
  [& body]
  `(clojure.test/is (= ~@body)))

; #todo add tests
(defmacro isnt=  ; #todo document in readme
  "Use (isnt= ...) instead of (is (not= ...)) for clojure.test"
  [& body]
  `(clojure.test/is (= (not ~@body))))

(defn throws?-impl
  [& forms]
  (if (= clojure.lang.Symbol (class (first forms)))
    ; symbol 1st arg => expected Throwable provided
    (do
    ; (println "symbol found")
      `(clojure.test/is
         (try
           ~@(rest forms)
           false        ; fail if no exception thrown
           (catch ~(first forms) t1#
             true) ; if catch expected type, test succeeds
           (catch Throwable t2#
             false))) ; if thrown type is unexpected, test fails
    )
    (do ; expected Throwable not provided
    ; (println "symbol not found")
      `(clojure.test/is
         (try
           ~@forms
           false        ; fail if no exception thrown
           (catch Throwable t3#
             true))) ; if anything is thrown, test succeeds
    )))

(defmacro throws?  ; #todo document in readme
  "Use (throws? ...) instead of (is (thrown? ...)) for clojure.test. Usage:

     (throws? (/ 1 0))                      ; catches any Throwable
     (throws? ArithmeticException (/ 1 0))  ; catches specified Throwable (or subclass)
  "
  [& forms]
  (apply throws?-impl forms))


;---------------------------------------------------------------------------------------------------
; Java version testing functions & macros

(defn java-version-matches?
  [version-str]
  {:pre [ (string? version-str) ] }
  (let [idx-val (str/index-of (System/getProperty "java.version") version-str) ]
    (and (not-nil? idx-val) (zero? idx-val))))

(defn java-version-min?
  [version-str]
  {:pre [ (string? version-str) ] }
  (neg? (compare version-str (System/getProperty "java.version"))))

(defn is-java-1-7? []  (java-version-matches? "1.7"))
(defn is-java-1-8? []  (java-version-matches? "1.8"))

(defn is-java-1-7-plus? [] (java-version-min? "1.7"))
(defn is-java-1-8-plus? [] (java-version-min? "1.8"))

(defmacro min-java-1-7
  [& forms]
  (if (is-java-1-7-plus?)
    `(do ~@forms)
    `(do (throw (RuntimeException. "Unimplemented prior to Java 1.7: ")))))

(defmacro min-java-1-8
  [& forms]
  (if (is-java-1-8-plus?)
    `(do ~@forms)
    `(do (throw (RuntimeException. "Unimplemented prior to Java 1.8: ")))))


(defn refer-tupelo  ; #todo document in readme
  "Refer a number of commonly used tupelo.core functions into the current namespace so they can
   be used without namespace qualification."
  []
  (refer 'tupelo.core :only
   '[ spy spyx spyxx with-spy-indent truthy? falsey?
      not-nil? not-empty? has-some? has-none? contains-key? contains-val? contains-elem?
      forv glue append prepend grab dissoc-in fetch-in only third it-> safe-> keep-if drop-if
      keyvals strcat pp-str pretty json->clj clj->json clip-str rng thru rel= drop-at insert-at replace-at
      starts-with? split-when split-match wild-match?
      isnt is= isnt= throws?
      java-version-matches? java-version-min?
      is-java-1-7? is-java-1-8? is-java-1-7-plus? is-java-1-8-plus? min-java-1-7 min-java-1-8
      with-exception-default ] ))

;---------------------------------------------------------------------------------------------------
; DEPRECATED functions

; duplicate of str/split-lines
(defn ^:deprecated ^:no-doc str->lines
  "***** DEPRECATED:  duplicate of str/split-lines *****

  Returns a lazy seq of lines from a string"
  [string-arg]
  (line-seq (BufferedReader. (StringReader. string-arg))))

(sk/defn ^:deprecated ^:no-doc conjv :- [sk/Any]
  "***** DEPRECATED:  replaced by tupelo.core/append *****

   Given base-coll and and one or more values, converts base-coll to a vector and then appends the values.
   The result is always returned as a vector."
  ; From Stuart Sierra post 2014-2-10
  ([base-coll :- [sk/Any]
    value :- sk/Any]
    (conj (vec base-coll) value))
  ([base-coll :- [sk/Any]
    value :- sk/Any
    & values :- [sk/Any]]
    (apply conj (vec base-coll) value values)))

;---------------------------------------------------------------------------------------------------
; Another benefit of test-all:  don't need "-test" suffix like in lein test:
; ~/tupelo > lein test :only tupelo.core
; lein test user
; Ran 0 tests containing 0 assertions.     ***** Nearly silent failure *****
; 0 failures, 0 errors.
;
; ~/tupelo > lein test :only tst.tupelo.core
; lein test tst.tupelo.core
; Ran 8 tests containing 44 assertions.     ***** Runs correctly *****
; 0 failures, 0 errors.
;
; ~/tupelo > lein test :only tst.tupelo.core/convj-test
; lein test tst.tupelo.core
; Ran 1 tests containing 3 assertions.
; 0 failures, 0 errors.
;
; #todo:  add run-tests with syntax like lein test :only
;   (run-tests 'tst.tupelo.core)              ; whole namespace
;   (run-tests 'tst.tupelo.core/convj-test)   ; one function only
;
; #todo make it handle either tst.orig.namespace or orig.namespace-test
; #todo make it a macro to accept unquoted namespace values

(defn ^:deprecated ^:no-doc test-all
  "Convenience fn to reload a namespace & the corresponding test namespace from disk and
  execute tests in the REPL.  Assumes canonical project test file organization with
  parallel src/... & test/tst/... directories, where a 'tst.' prefix is added to all src
  namespaces to generate the cooresponding test namespace.  Example:

    (test-all 'tupelo.core 'tupelo.csv)

  This will reload tupelo.core, tst.tupelo.core, tupelo.csv, tst.tupelo.csv and
  then execute clojure.test/run-tests on both of the test namespaces."
  [& ns-list]
  (let [test-ns-list (for [curr-ns ns-list]
                       (let [curr-ns-test (symbol (str "tst." curr-ns))]
                         (println (str "testing " curr-ns " & " curr-ns-test))
                         (require curr-ns curr-ns-test :reload)
                         curr-ns-test))
        ]
    (println "-----------------------------------------------------------------------------")
    (apply clojure.test/run-tests test-ns-list)
    (println "-----------------------------------------------------------------------------")
    (newline)
    ))

