#!/usr/bin/env bb

(ns pod.polylith
  (:import [java.io PushbackInputStream])
  (:require [bencode.core :as bencode]
            [clojure.java.io :as io]
            ;[clojure.spec.alpha]
            [clojure.string :as str]
            ;[clojure.walk :as walk]
            [cognitect.transit :as transit]))

(def stdin  System/in #_(PushbackInputStream. System/in))
(def stdout System/out)

(def lookup {})

(def describe-map {})

(def debug? false)

(defn read-transit [^String v]
  (transit/read
   (transit/reader
    (java.io.ByteArrayInputStream. (.getBytes v "utf-8"))
    :json)))

(def throwable-key (str `throwable))

(def throwable-write-handler
  (transit/write-handler throwable-key Throwable->map))

(def class-key (str `class))

(def class-write-handler
  (transit/write-handler class-key (fn [^Class x] (.getName x))))

(def transit-writers-map
  {java.lang.Throwable throwable-write-handler
   java.lang.Class class-write-handler})

(def whm (transit/write-handler-map transit-writers-map))

(defn write-transit [v]
  (let [baos (java.io.ByteArrayOutputStream.)]
    (try (transit/write (transit/writer baos :json {:handlers whm}) v)
         (catch Exception e
           (binding [*out* *err*]
             (prn "ERROR: can't serialize to transit:" v))
           (throw e)))
    (.toString baos "utf-8")))

(defn debug [& strs]
  (when debug?
    (binding [*out* (io/writer System/err)]
      (apply prn strs))))



(defn write
  ([v] (write stdout v))
  ([stream v]
   (debug :writing v)
   (bencode/write-bencode stream v)
   (flush)))

(defn -main [& _args]
  (loop []
    (let [message (try (read stdin)
                       (catch java.io.EOFException _
                         ::EOF))]
      (when-not (identical? ::EOF message)
        (let [op (get message "op")
              op (read-string op)
              op (keyword op)
              id (some-> (get message "id")
                         read-string)
              id (or id "unknown")]
          (case op
            :describe (do (write stdout describe-map)
                          (recur))
            :invoke (do (try
                          (let [var (-> (get message "var")
                                        read-string
                                        symbol)
                                args (get message "args")
                                args (read-string args)
                                args (read-transit args)]
                            (if-let [f (lookup var)]
                              (let [out-str (java.io.StringWriter.)
                                    value (binding [*out* out-str]
                                            (let [v (apply f args)]
                                              (write-transit v)))
                                    out-str (str out-str)
                                    reply (cond-> {"value" value
                                                   "id" id
                                                   "status" ["done"]}
                                            (not (str/blank? out-str))
                                            (assoc "out" out-str))]
                                (write stdout reply))
                              (throw (ex-info (str "Var not found: " var) {}))))
                          (catch Throwable e
                            (debug e)
                            (let [reply {"ex-message" (ex-message e)
                                         "ex-data" (write-transit
                                                    (assoc (ex-data e)
                                                           :type (str (class e))))
                                         "id" id
                                         "status" ["done" "error"]}]
                              (write stdout reply))))
                        (recur))
            :shutdown (System/exit 0)
            (do
              (let [reply {"ex-message" "Unknown op"
                           "ex-data" (pr-str {:op op})
                           "id" id
                           "status" ["done" "error"]}]
                (write stdout reply))
              (recur))))))))

#?(:bb (-main *command-line-args*))
