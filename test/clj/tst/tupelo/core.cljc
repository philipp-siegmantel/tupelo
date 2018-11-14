;   Copyright (c) Alan Thompson. All rights reserved.
;   The use and distribution terms for this software are covered by the Eclipse Public License 1.0
;   (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at
;   the root of this distribution.  By using this software in any fashion, you are agreeing to be
;   bound by the terms of this license.  You must not remove this notice, or any other, from this
;   software.
(ns tst.tupelo.core
  #?@(:clj [
  (:use tupelo.dev tupelo.test )
            (:require
              [clojure.string :as str]
              [tupelo.core :as t] ; #todo finish migration to (:use tupelo.core)
              [tupelo.core :as i]
              [tupelo.string :as ts]
              [tupelo.string :as tstr]) ]) )

; (s/instrument-all)
; (s/instrument #'tupelo.core/truthy?)  ; instrument just one var

;-----------------------------------------------------------------------------
; Java version stuff

#?(:clj (do

(defn fn-any [] 42)
(defn fn7 [] (t/if-java-1-7-plus
               7
               (throw (ex-info "Unimplemented prior to Java 1.7: " nil))))
(defn fn8 [] (t/if-java-1-8-plus
               8
               (throw (ex-info "Unimplemented prior to Java 1.8: " nil))))

(dotest
  (when (is-java-1-7?)
    (throws? (fn8)))

  (when (is-java-1-8-plus?)
    (is= 8 (fn8)))

  (is= 7 (fn7))
  (is= 42 (fn-any))

  (with-redefs [java-version (constantly "1.7")]
    (is   (t/java-version-min? "1.7"))
    (isnt (t/java-version-min? "1.7.0"))
    (isnt (t/java-version-min? "1.7.0-b1234"))
    (isnt (t/java-version-min? "1.8"))

    (is   (t/java-version-matches? "1.7"))
    (isnt (t/java-version-matches? "1.7.0"))
    (isnt (t/java-version-matches? "1.7.0-b1234"))
    (isnt (t/java-version-matches? "1.8"))
    )
  (with-redefs [java-version (constantly "1.7.0")]
    (is   (t/java-version-min? "1.7"))
    (is   (t/java-version-min? "1.7.0"))
    (isnt (t/java-version-min? "1.7.0-b1234"))
    (isnt (t/java-version-min? "1.8"))

    (is   (t/java-version-matches? "1.7"))
    (is   (t/java-version-matches? "1.7.0"))
    (isnt (t/java-version-matches? "1.7.0-b1234"))
    (isnt (t/java-version-matches? "1.8"))
    )
  (with-redefs [java-version (constantly "1.7.0-b1234")]
    (is   (t/java-version-min? "1.7"))
    (is   (t/java-version-min? "1.7.0"))
    (is   (t/java-version-min? "1.7.0-b1234"))
    (isnt (t/java-version-min? "1.8"))

    (is   (t/java-version-matches? "1.7"))
    (is   (t/java-version-matches? "1.7.0"))
    (is   (t/java-version-matches? "1.7.0-b1234"))
    (isnt (t/java-version-matches? "1.8"))
    )

  (with-redefs [java-version (constantly "1.7") ]
    (when false
      (println "testing java 1.7")
      (t/spyx (t/is-java-1-7?))
      (t/spyx (t/is-java-1-8?))
      (t/spyx (t/is-java-1-7-plus?))
      (t/spyx (t/is-java-1-8-plus?)))

    (is   (t/is-java-1-7?))
    (is   (t/is-java-1-7-plus?))
    (isnt (t/is-java-1-8?))
    (isnt (t/is-java-1-8-plus?)) )

  (with-redefs [java-version (constantly "1.8") ]
    (when false
      (println "testing java 1.8")
      (t/spyx (t/is-java-1-7?))
      (t/spyx (t/is-java-1-8?))
      (t/spyx (t/is-java-1-7-plus?))
      (t/spyx (t/is-java-1-8-plus?)))

    (isnt (t/is-java-1-7?))
    (is   (t/is-java-1-7-plus?))
    (is   (t/is-java-1-8?))
    (is   (t/is-java-1-8-plus?)) ) )

;-----------------------------------------------------------------------------
; Clojure version stuff

(dotest
  (binding [*clojure-version* {:major 1 :minor 7}]
    (is   (i/is-clojure-1-7-plus?))
    (isnt (i/is-clojure-1-8-plus?))
    (isnt (i/is-clojure-1-9-plus?))
    (is   (i/is-pre-clojure-1-8?))
    (is   (i/is-pre-clojure-1-9?)))
  (binding [*clojure-version* {:major 1 :minor 8}]
    (is   (i/is-clojure-1-7-plus?))
    (is   (i/is-clojure-1-8-plus?))
    (isnt (i/is-clojure-1-9-plus?))
    (isnt (i/is-pre-clojure-1-8?))
    (is   (i/is-pre-clojure-1-9?)))
  (binding [*clojure-version* {:major 1 :minor 9}]
    (is   (i/is-clojure-1-7-plus?))
    (is   (i/is-clojure-1-8-plus?))
    (is   (i/is-clojure-1-9-plus?))
    (isnt (i/is-pre-clojure-1-8?))
    (isnt (i/is-pre-clojure-1-9?)))
)

(dotest
  (let [val1 (into (sorted-map) {:a 1 :b 2})]
    (is= "val1 => <#clojure.lang.PersistentTreeMap {:a 1, :b 2}>"
      (ts/collapse-whitespace (with-out-str (t/spyxx val1))))
    (is= "(+ 2 3) => <#java.lang.Long 5>"
      (ts/collapse-whitespace (with-out-str (t/spyxx (+ 2 3)))))
    (is= "(mapv inc (range 3)) => <#clojure.lang.PersistentVector [1 2 3]>"
      (ts/collapse-whitespace (with-out-str (t/spyxx (mapv inc (range 3)))))) ))

;(sp/def ::vector (sp/coll-of clj/any :kind vector?))
;(dotest
;  (is   (sp/valid? ::vector [1 2 3]))
;  (isnt (sp/valid? ::vector '(1 2 3)))
;  (isnt (sp/valid? ::vector {:a 1}))
; ;(spyx (sp/exercise ::vector))
;)

#_(tst/defspec ^:slow t-keep-if-drop-if 999
  (prop/for-all [vv (gen/vector gen/int) ]
    (let [even-1      (keep-if   even?  vv)
          even-2      (drop-if   odd?   vv)
          even-filt   (filter    even?  vv)

          odd-1       (keep-if   odd?   vv)
          odd-2       (drop-if   even?  vv)
          odd-rem     (remove    even?  vv) ]
      (and  (= even-1 even-2 even-filt)
            (=  odd-1  odd-2  odd-rem)))))

#_(tst/defspec ^:slow t-keep-if-drop-if-set 999
  (prop/for-all [ss (gen/set gen/int) ]
    (let [even-1      (keep-if   even?  ss)
          even-2      (drop-if   odd?   ss)
          even-filt   (into #{} (filter even? (seq ss)))

          odd-1       (keep-if   odd?   ss)
          odd-2       (drop-if   even?  ss)
          odd-rem     (into #{} (remove even? (seq ss))) ]
      (and  (= even-1 even-2 even-filt)
            (=  odd-1  odd-2  odd-rem)))))

#_(tst/defspec ^:slow t-keep-if-drop-if-map-key 99  ; seems to hang if (< 99 limit)
  (prop/for-all [mm (gen/map gen/int gen/keyword {:max-elements 99} ) ]
    (let [even-1      (keep-if  (fn [k v] (even? k))  mm)
          even-2      (drop-if  (fn [k v] (odd?  k))  mm)
          even-filt   (into {} (filter #(even? (key %)) (seq mm)))

          odd-1      (keep-if  (fn [k v] (odd?  k))  mm)
          odd-2      (drop-if  (fn [k v] (even? k))  mm)
          odd-rem    (into {} (remove #(even? (key %)) (seq mm)))
    ]
      (and  (= even-1 even-2 even-filt)
            (=  odd-1  odd-2  odd-rem)))))

#_(tst/defspec ^:slow t-keep-if-drop-if-map-value 99  ; seems to hang if (< 99 limit)
  (prop/for-all [mm (gen/map gen/keyword gen/int {:max-elements 99} ) ]
    (let [even-1      (keep-if  (fn [k v] (even? v))  mm)
          even-2      (drop-if  (fn [k v] (odd?  v))  mm)
          even-filt   (into {} (filter #(even? (val %)) (seq mm)))

          odd-1      (keep-if  (fn [k v] (odd?  v))  mm)
          odd-2      (drop-if  (fn [k v] (even? v))  mm)
          odd-rem    (into {} (remove #(even? (val %)) (seq mm)))
    ]
      (and  (= even-1 even-2 even-filt)
            (=  odd-1  odd-2  odd-rem)))))

; #todo ***** toptop *****

; #todo add different lengths a/b
; #todo add missing entries a/b
(dotest
  (is      (matches?  []    [] ))
  (is      (matches?  [1]   [1] ))
  (isnt    (matches?  [1]   [2] ))
  ;        (matches?  [1]   [1 2] )))  ***** error *****
  (is      (matches?  [_]   [1] ))
  (is      (matches?  [_]   [nil] ))
  (is      (matches?  [_]   [1] [2] [3]))
  (is      (matches?  [1 2] [1 2] ))
  (is      (matches?  [_ 2] [1 2] ))
  (is      (matches?  [1 _] [1 2] ))
  (is      (matches?  [1 _] [1 2] [1 3] [1 nil] ))
  (is      (matches?  [1 _ 3] [1 2 3] [1 nil 3] ))

  (is      (matches?  {:a 1} {:a 1} ))
  (isnt    (matches?  {:a 1} {:a 2} ))
  (isnt    (matches?  {:a 1} {:b 1} ))
  (is      (matches?  {:a _} {:a 1} {:a 2} {:a 3} ))
  ;        (matches?  { _ 1} {:a 1} )   ***** error *****

  (is      (matches?  {:a _ :b _       :c 3}
                      {:a 1 :b [1 2 3] :c 3} ))
  (isnt    (matches?  {:a _ :b _       :c 4}
                      {:a 1 :b [1 2 3] :c 3} ))
  (isnt    (matches?  {:a _ :b _       :c 3}
                      {:a 1 :b [1 2 3] :c 4} ))
  (isnt    (matches?  {:a 9 :b _       :c 3}
                      {:a 1 :b [1 2 3] :c 3} ))

  (is      (matches?  {:a _ :b _       :c 3}
                      {:a 1 :b [1 2 3] :c 3}
                      {:a 2 :b 99      :c 3}
                      {:a 3 :b nil     :c 3} ))
  (isnt    (matches?  {:a _ :b _       :c 3}
                      {:a 1 :b [1 2 3] :c 9}
                      {:a 2 :b 99      :c 3}
                      {:a 3 :b nil     :c 3} ))
  (isnt    (matches?  {:a _ :b _       :c 3}
                        {:a 1 :b [1 2 3] :c 3}
                        {:a 2 :b 99      :c 3}
                        {:a 3 :b nil     :c 9} ))
)

; #todo add different lengths a/b
; #todo add missing entries a/b
(dotest
  (testing "vectors"
    (is   (wild-match? [1]  [1] ))
    (is   (wild-match? [1]  [1] [1] ))
    (is   (wild-match? [:*] [1] [1] ))
    (is   (wild-match? [:*] [1] [9] ))

    (is   (wild-match? [1] [1] ))
    (is   (wild-match? [1] [1] [1] ))

    (isnt (wild-match? [1] [ ] ))
    (isnt (wild-match? [ ] [1] ))
    (isnt (wild-match? [1] [ ] [ ] ))
    (isnt (wild-match? [ ] [1] [ ] ))
    (isnt (wild-match? [ ] [ ] [1] ))
    (isnt (wild-match? [1] [1] [ ] ))
    (isnt (wild-match? [1] [ ] [1] ))

    (is   (wild-match? [1 2  3]
                       [1 2  3] ))
    (is   (wild-match? [1 :* 3]
                       [1 2  3] ))
    (is   (wild-match? [1 :* 3]
                       [1 2  3]
                       [1 9  3] ))
    (isnt (wild-match? [1 2  3]
                       [1 2  9] ))
    (isnt (wild-match? [1 2   ]
                       [1 2  9] ))
    (isnt (wild-match? [1 2  3]
                       [1 2   ] ))

    (is   (wild-match? [1  [2 3]]
                       [1  [2 3]] ))
    (is   (wild-match? [:* [2 3]]
                       [1  [2 3]] ))
    (is   (wild-match? [:* [2 3]]
                       [1  [2 3]]
                       [9  [2 3]] ))
    (is   (wild-match? [1  [2 :*]]
                       [1  [2 33]]
                       [1  [2 99]] ))
    (is   (wild-match? [1  :*]
                       [1   2]
                       [1  [2 3]] ))
    (isnt (wild-match? [1  [2 3]]
                       [1  [2 9]] ))
  )
  (testing "maps"
    (is (wild-match? {:a 1 } {:a 1} ))
    (is (wild-match? {:a :*} {:a 1} ))
    (is (wild-match? {:a :*} {:a 1 } {:a 1 } ))
    (is (wild-match? {:a :*} {:a 1 } {:a 9 } ))
    (is (wild-match? {:a :*} {:a :*} {:a 9 } ))
    (is (wild-match? {:a :*} {:a :*} {:a :*} ))

    (isnt (wild-match? {:a 1 } {:a 9} ))
    (isnt (wild-match? {:a 1 } {:a 1 :b 2} ))
    (isnt (wild-match? {:a :*} {:b 1} ))
    (isnt (wild-match? {:a :*} {:a 1} {:b 1} ))
    (isnt (wild-match? {:a :*} {:a 1 :b 2} ))

    (let [vv {:a 1  :b {:c 3}}
          tt {:a 1  :b {:c 3}}
          w2 {:a :* :b {:c 3}}
          w5 {:a 1  :b {:c :*}}
          zz {:a 2  :b {:c 3}}
    ]
      (is   (wild-match? tt vv))
      (is   (wild-match? w2 vv))
      (is   (wild-match? w5 vv))
      (isnt (wild-match? zz vv)))
  )
  (testing "vecs & maps 1"
    (let [vv [:a 1  :b {:c  3} ]
          tt [:a 1  :b {:c  3} ]
          w1 [:* 1  :b {:c  3} ]
          w2 [:a :* :b {:c  3} ]
          w3 [:a 1  :* {:c  3} ]
          w5 [:a 1  :b {:c :*} ]
          zz [:a 2  :b {:c  3} ]
    ]
      (is (wild-match? tt vv))
      (is (wild-match? w1 vv))
      (is (wild-match? w2 vv))
      (is (wild-match? w3 vv))
      (is (wild-match? w5 vv))
      (isnt (wild-match? zz vv)))
  )
  (testing "vecs & maps 2"
    (let [vv {:a 1  :b [:c  3] }
          tt {:a 1  :b [:c  3] }
          w2 {:a :* :b [:c  3] }
          w4 {:a 1  :b [:*  3] }
          w5 {:a 1  :b [:c :*] }
          z1 {:a 2  :b [:c  3] }
          z2 {:a 1  :b [:c  9] }
    ]
      (is (wild-match? tt vv))
      (is (wild-match? w2 vv))
      (is (wild-match? w4 vv))
      (is (wild-match? w5 vv))
      (isnt (wild-match? z1 vv))
      (isnt (wild-match? z2 vv)))
  )
  (testing "sets"
    (is   (wild-match? #{1} #{1} ))
    (isnt (wild-match? #{1} #{9} ))
    (isnt (wild-match? #{1} #{:a :b} ))
    (is   (wild-match? #{1  #{:a :b}}
                       #{1  #{:a :b} }))
    (isnt (wild-match? #{1  #{:a :c}}
                       #{1  #{:a :x} }))
  ))

(dotest
  (isnt (wild-match? #{1 2} #{1 2 3 4}))
  (isnt (wild-match? {:pattern #{1 2}
                      :values  [#{1 2 3 4}]}))
  (is (wild-match? {:subset-ok true
                    :pattern   #{1 2}
                    :values    [#{1 2 3 4}]}))

  (isnt (wild-match? {:a 1} {:a 1 :b 2}))
  (isnt (wild-match? {:pattern {:a 1}
                      :values  [{:a 1 :b 2}]}))
  (is (wild-match? {:submap-ok true
                    :pattern   {:a 1}
                    :values    [{:a 1 :b 2}]}))

  (isnt (wild-match? '(1 2) '(1 2 3 4)))
  (isnt (wild-match? {:pattern '(1 2)
                      :values  ['(1 2 3 4)]}))
  (is (wild-match? {:subvec-ok true
                    :pattern   '(1 2)
                    :values    ['(1 2 3 4)]}))

  (isnt (wild-match? [1 2] [1 2 3 4]))
  (isnt (wild-match? {:pattern [1 2]
                      :values  [[1 2 3 4]]}))
  (is (wild-match? {:subvec-ok true
                    :pattern   [1 2]
                    :values    [[1 2 3 4]]}))

  (isnt (wild-submatch? #{1 :*}    #{1 2 3 4}))
  (is (wild-submatch?   #{1 2}     #{1 2 3 4}))
  (is (wild-submatch?    {:a :*}    {:a 1 :b 2}))
  (is (wild-submatch?   '(1 :* 3)  '(1 2 3 4)))
  (is (wild-submatch?    [1 :* 3]   [1 2 3 4]))

  (is (submatch? #{1 2} #{1 2 3 4}))
  (is (submatch? {:a 1} {:a 1 :b 2}))
  (is (submatch? '(1 2) '(1 2 3 4)))
  (is (submatch? [1 2 3] [1 2 3 4]))
  (isnt (submatch? [1 :* 3] [1 2 3 4]))
  (isnt (submatch? {:a :*} {:a 1 :b 2}))
  (isnt (submatch? #{1 :*} #{1 2 3 4}))

  (let [sample-rec (->SampleRec 1 2)]
    (isnt= sample-rec {:a 1 :b 2})
    (is (wild-submatch? sample-rec {:a 1 :b 2}))
    (is (wild-submatch? {:a 1 :b 2} sample-rec))
    (is (submatch? sample-rec {:a 1 :b 2}))
    (is (submatch? {:a 1 :b 2} sample-rec))))

(dotest
  (is (t/set-match? #{1 2 3} #{1 2 3}))
  (is (t/set-match? #{:* 2 3} #{1 2 3}))
  (is (t/set-match? #{1 :* 3} #{1 2 3}))
  (is (t/set-match? #{1 2 :*} #{1 2 3}))

  (is (t/set-match? #{1 2 3 4 5} #{1 2 3 4 5}))
  (is (t/set-match? #{:* 2 3 4 5} #{1 2 3 4 5}))
  (is (t/set-match? #{1 :* 3 4 5} #{1 2 3 4 5}))
  (is (t/set-match? #{1 2 :* 4 5} #{1 2 3 4 5}))
  (is (t/set-match? #{1 2 3 :* 5} #{1 2 3 4 5}))
  (is (t/set-match? #{1 2 3 4 :*} #{1 2 3 4 5}))

  (is   (wild-item? :*))
  (isnt (wild-item? :a))

  (is   (wild-item? :*))
  (isnt (wild-item? :a))
  (isnt (wild-item? 5))
  (isnt (wild-item? "hello"))

  (is   (wild-item? [:* 2 3]))
  (is   (wild-item? [1 [:* 3]]))
  (is   (wild-item? [1 [2 [:*]]]))
  (isnt (wild-item? [1 2 3]))
  (isnt (wild-item? [1 [2 3]]))
  (isnt (wild-item? [1 [2 [3]]]))

  (is   (wild-item? #{:* 2 3}))
  (is   (wild-item? #{1 #{:* 3}}))
  (is   (wild-item? #{1 #{2 #{:*}}}))
  (isnt (wild-item? #{1 2 3}))
  (isnt (wild-item? #{1 #{2 3}}))
  (isnt (wild-item? #{1 #{2 #{3}}}))

  (is   (wild-item? {:* 1 :b 2 :c 3}))
  (is   (wild-item? {:a {:* 2 :c 3}}))
  (is   (wild-item? {:a {:b {:* 3}}}))
  (is   (wild-item? {:a :* :b 2 :c 3}))
  (is   (wild-item? {:a {:b :* :c 3}}))
  (is   (wild-item? {:a {:b {:c :*}}}))
  (isnt (wild-item? {:a 1 :b 2 :c 3}))
  (isnt (wild-item? {:a {:b 2 :c 3}}))
  (isnt (wild-item? {:a {:b {:c 3}}}))

  (is (t/set-match? #{#{1 2 3} #{4 5 :*}} #{#{1 2 3} #{4 5 6}}))
  (is (t/set-match? #{#{1 2 3} #{4 :* 6}} #{#{1 2 3} #{4 5  6}}))
  (is (t/set-match? #{#{1 2 3} #{:* 5 6}} #{#{1 2 3} #{4 5 6}}))
  (is (t/set-match? #{#{:* 2 3} #{4 5 6}} #{#{1 2 3} #{4 5 6}}))
  (is (t/set-match? #{#{1 :* 3} #{4 5 6}} #{#{1 2 3} #{4 5 6}}))
  (is (t/set-match? #{#{1 2 :*} #{4 5 6}} #{#{1 2 3} #{4 5 6}}))

  (is (t/set-match? #{#{1 :* 3} #{4 5 :*}} #{#{1 2 3} #{4 5 6}}))
  (is (t/set-match? #{#{1 2 :*} #{4 :* 6}} #{#{1 2 3} #{4 5  6}}))
  (is (t/set-match? #{#{:* 2 3} #{:* 5 6}} #{#{1 2 3} #{4 5 6}}))
  (is (t/set-match? #{#{:* 2 3} #{:* 5 6}} #{#{1 2 3} #{4 5 6}}))
  (is (t/set-match? #{#{1 :* 3} #{:* 5 6}} #{#{1 2 3} #{4 5 6}}))
  (is (t/set-match? #{#{1 2 :*} #{:* 5 6}} #{#{1 2 3} #{4 5 6}}))
)

(dotest
  (throws? (/ 1 0))
  (throws? Exception (/ 1 0))             ; catches specified Throwable (or subclass) - JVM only
  (throws? ArithmeticException (/ 1 0))   ; catches specified Throwable (or subclass) - JVM only
)

(dotest
  (is   (macro? 'and))
  (is   (macro? '->))
  (isnt (macro? '+))
  (isnt (macro? 'if)))


; #todo move to tst.tupelo.core.deprecated
;---------------------------------------------------------------------------------------------------
; Deprecated functions

; As of Clojure 1.9.0-alpha5, seqable? is native to clojure
(dotest
  ; ^{:deprecated "1.9.0-alpha5" }
  (t/when-not-clojure-1-9-plus
    (is   (seqable?   "abc"))
    (is   (seqable?   {1 2 3 4} ))
    (is   (seqable?  #{1 2 3} ))
    (is   (seqable?  '(1 2 3) ))
    (is   (seqable?   [1 2 3] ))
    (is   (seqable?   (byte-array [1 2] )))
    (isnt (seqable?  1 ))
    (isnt (seqable? \a ))))

(dotest
  ; ^:deprecated ^:no-doc
  (let [s1    "  hello there
                 again
                 and again!   "
        r1     ["hello there"
                "again"
                "and again!"]
  ]
    (is= r1 (map str/trim (t/str->lines s1)))
    (is= r1 (map str/trim (str/split-lines s1)))))

))
