(ns polylith.pod.commands
  (:require [polylith.clj.core.command.interface :as command]
    [polylith.clj.core.user-input.interface :as user-input]))

(defn execute-command [& args]
  (let [input (user-input/extract-params args)]
    (with-out-str
      #_(println "INPUT" input)
      
      (do (command/execute-command input)))))