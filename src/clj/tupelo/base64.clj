;   Copyright (c) Alan Thompson. All rights reserved. 
;   The use and distribution terms for this software are covered by the Eclipse Public
;   License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the
;   file epl-v10.html at the root of this distribution.  By using this software in any
;   fashion, you are agreeing to be bound by the terms of this license.
;   You must not remove this notice, or any other, from this software.
(ns tupelo.base64
  "Convert to/from traditional base64 encoding."
  (:use tupelo.core)
  (:require
    [tupelo.types :as types]
    [schema.core :as s]
  ))

(def base64-chars
  "A set of chars used for traditional base64 encoding (incl. padding char)"
  (set (glue
         (chars-thru \a \z)
         (chars-thru \A \Z)
         (chars-thru \0 \9)
         [\+ \/ \=])))

(defn base64-encoder []
  (if-java-1-8-plus
    (java.util.Base64/getEncoder)
    (throw (RuntimeException. "Unimplemented prior to Java 1.8: "))))

(defn base64-decoder []
  (if-java-1-8-plus
    (java.util.Base64/getDecoder)
    (throw (RuntimeException. "Unimplemented prior to Java 1.8: "))))

(defn byte-array-encode-native
  "Encodes a byte array into base64, returning a new byte array."
  [byte-arr]
  (assert (types/byte-array? byte-arr))
  (.encode (base64-encoder) byte-arr))

(defn byte-array-decode-native
  "Decodes a byte array from base64, returning a new byte array."
  [byte-arr]
  (assert (types/byte-array? byte-arr))
  (.decode (base64-decoder)  byte-arr))

(s/defn byte-array->code-str :- s/Str
  "Encodes a byte array into base64, returning a String."
  [data-bytes]
  (assert (types/byte-array? data-bytes))
  (.encodeToString (base64-encoder) data-bytes))

(s/defn code-str->byte-array
  "Decodes a base64 encoded String, returning a byte array"
  [code-str :- s/Str]
  (.decode (base64-decoder) code-str))

(s/defn bytes->code-str :- s/Str ; #todo need test
  "Encodes a vector of byte values into base64, returning a String."
  [byte-vec :- [s/Int]]
  (byte-array->code-str (byte-array byte-vec)))

(s/defn code-str->bytes :- [s/Int] ; #todo need test
  "Decodes a base64 encoded String, returning a vector of byte values"
  [code-str :- s/Str]
  (vec (code-str->byte-array code-str)))

(s/defn str->code-str :- s/Str
  "Encodes a String into base64, returning a String."
  [data-str :- s/Str]
  (-> data-str types/str->bytes byte-array->code-str))

(s/defn code-str->str :- s/Str
  "Decodes a base64 encoded String, returning a String."
  [code-str :- s/Str]
  (-> code-str code-str->byte-array types/bytes->str))
