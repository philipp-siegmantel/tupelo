;   Copyright (c) Alan Thompson. All rights reserved.  
;   The use and distribution terms for this software are covered by the Eclipse Public
;   License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the
;   file epl-v10.html at the root of this distribution.  By using this software in any
;   fashion, you are agreeing to be bound by the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:doc "Cooljure - Cool stuff you wish was in Clojure.  
            Utils for reading CSV (comma-separated-value) formatted files."
      :author "Alan Thompson"}
  cooljure.csv
  (:require [clojure.string             :as str]
            [clojure.java.io            :as io]
            [clojure-csv.core           :as csv]
            [cooljure.misc              :as cool-misc] 
            [cooljure.core              :refer :all] ))

(defn- get-labels-and-data-lines
  [opts parsed-lines]
  (if (:labels opts)  ; if user supplied col label keywords
    { :labels-kw    (:labels opts)    ; use them
      :data-lines   parsed-lines }  ; all lines are data
  ;else, convert first row of strings -> col label keywords
    { :labels-kw    (mapv cool-misc/str->kw (first parsed-lines))
      :data-lines   (rest parsed-lines) } ))  ; rest of lines are data
; AWTAWT TODO: add default label-fn (comp trim safe-char )

; AWTAWT TODO: add default data-fn (trim...)
; AWTAWT TODO: change to allow line-seq, FILE, etc
(defn parse-csv->row-maps
 "[csv-lines & {:as opts} ] 
  Returns a sequence of maps constructed from csv-lines.  The first line
  is assumed to be column label strings, which are (safely) converted into keywords.
  String data from each subsequent line is paired with the corresponding column keyword to
  construct a map for that line.  Default delimiter is the comma character (i.e. \\,) but 
  may be changed using the syntax such as: 
  
    (parse-csv->row-maps csv-lines :delimiter \\| )

  to select the pipe character (i.e. \\|) as the delimiter.  "
  ; AWTAWT TODO: update docs re. col-labels (keywords)
  [csv-lines & {:as opts} ] 
  { :pre  [ (string? csv-lines) ]
    :post [ (map? (first %)) ] }
  (let [opts-def        (merge {:delimiter \,} opts)
        parsed-lines    (apply csv/parse-csv csv-lines (keyvals opts-def))
        {:keys [labels-kw data-lines]}  
                        (get-labels-and-data-lines opts parsed-lines)
        row-maps        (for [data-line data-lines]
                          (zipmap labels-kw data-line) )
  ] row-maps ))

; AWTAWT TODO: clean up, enforce identical columns each row
(defn row-maps->col-vecs    ; move to cooljure.data ?
  "<TEMP> Converts a sequence of row-maps into a map of column-vectors"
  [row-maps]
  { :pre  [ (map? (first row-maps)) ]
    :post [ (map? %) ] }
  (let [labels-kw   (keys (first row-maps))
        col-vecs    (into {}  (for [label-kw labels-kw]
                                { label-kw (mapv label-kw row-maps) } ))
  ] col-vecs ))

; AWTAWT TODO: clean up, enforce identical columns length
(defn col-vecs->row-maps    ; move to cooljure.data ?
  "<TEMP> Converts a map of column-vectors into a sequence of row-maps"
  [col-vecs]
  { :pre  [ (map? col-vecs) ]
    :post [ (map? (first %)) ] }
  (let [col-kws     (keys col-vecs)
        col-vals    (vals col-vecs)
        row-vals    (apply map vector col-vals)
        row-maps    (map #(zipmap col-kws %) row-vals)
  ] row-maps ))

; AWTAWT TODO: change to allow line-seq, FILE, etc
(defn parse-csv->col-vecs
 "[csv-lines & {:as opts} ] 
  Returns a map constructed from the columns of csv-lines.  The first line is
  assumed to be column label strings, which are (safely) converted into keywords. The
  returned map has one entry for each column label keyword. The corresponding value for
  each keyword is a vector of string data taken from each subsequent line in the file.
  Default delimiter is the comma character (i.e. \\,) but may be changed using the syntax
  such as: 
  
    (parse-cvs->col-vecs my-file.psv :delimiter \\| )

  to select the pipe character (i.e. \\|) as the delimiter.  "
  ; AWTAWT TODO: update docs re. col-labels (keywords)
  [csv-lines & {:as opts} ] 
  { :pre  [ (string? csv-lines) ]
    :post [ (map? %) ] }
  (let [opts-def    (merge {:delimiter \,} opts)
        row-maps    (apply parse-csv->row-maps csv-lines (keyvals opts-def))
        col-vecs    (row-maps->col-vecs row-maps)
  ] col-vecs ))

