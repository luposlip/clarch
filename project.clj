(defproject com.luposlip/clarch "0.3.2"
  :description "Clojure Archiving library"
  :url "https://github.com/luposlip/clarch"
  :license {:name "Apache License, Version 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.apache.commons/commons-compress "1.27.1"]]
  :repl-options {:init-ns clarch.core})
