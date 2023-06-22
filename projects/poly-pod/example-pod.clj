(require '[babashka.pods :as pods])

(def cwd (System/getProperty "user.dir"))
(def pod-path (.getPath (clojure.java.io/file (.getParentFile (clojure.java.io/file *file*)) "pod-bin")))

(pods/load-pod pod-path {:cwd cwd})
(require '[pod.polylith.pod.commands :as polylith])

(print (apply polylith/execute-command *command-line-args*))
(flush)
