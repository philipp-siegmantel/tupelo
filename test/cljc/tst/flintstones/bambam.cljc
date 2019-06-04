(ns tst.flintstones.bambam
  (:require
    #?(:clj  [clojure.test :refer [deftest testing is]]
       :cljs [cljs.test :refer-macros [deftest testing is]])

    ; ; #todo #bug copy  :include-macros true everywhere!!!
    #?(:clj  [tupelo.test      :as tt ; :refer [define-fixture dotest isnt is= isnt= is-set= is-nonblank= throws?]
              ]
       :cljs [tupelo.test-cljs ; :refer [define-fixture dotest isnt is= isnt= is-set= is-nonblank= throws?]
              :as tt :include-macros true ])

    #?(:clj  [tupelo.core :as t :refer [spy spyx spyxx]]
       :cljs [tupelo.core :as t :refer [spy spyx spyxx]] :include-macros true )

    #?(:clj  [flintstones.bambam :as bam]
       :cljs [flintstones.bambam :as bam :include-macros true])
     ))

#?(:cljs (enable-console-print!))

;(define-fixture :once
;  {:enter (fn [ctx]
;             (println "*** TEST ONCE *** - bambam enter ctx=" ctx)
;            )
;   :leave (fn [ctx]
;             (println "*** TEST ONCE *** - bambam leave ctx=" ctx)
;            )})

(defn tosser [] (throw (ex-info "It threw!" {:a 1})))

(tt/dotest         ; deftest t2         ;
  (println "tst.flintstones.bambam - test 2 - enter")
  (is (= 5 (bam/add2 2 3))) ; this works
  (is (= 3 (bam/logr-bambam
             (inc 0)
             (inc 1)
             (inc 2))))
  (println "tst.flintstones.bambam - test 2 - leave")
  )

(tt/dotest         ; deftest t1         ;
  (println "tst.flintstones.bambam - test 1 - enter")
  (is (= 2 (+ 1 1)))

  (tt/throws? (tosser)) ; #todo fix this!
  (println "tst.flintstones.bambam - test 1 - leave")
  )

