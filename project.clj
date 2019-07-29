(defproject clj-george "0.0.0"
  :description "Interact with Sparkasse's George"
  :url "https://github.com/ramblurr/clj-george"
  :license {:name "AGPL"
            :url  "https://www.gnu.org/licenses/agpl-3.0.en.html"}
  :dependencies [
                 [org.clojure/core.async "0.4.490"]
                 [clj-http "3.10.0"]
                 [cheshire "5.8.1"]
                 [slingshot "0.12.2"]]

  :plugins [[lein-cloverage "1.0.13"]
            [lein-shell "0.5.0"]
            [lein-ancient "0.6.15"]
            [lein-changelog "0.3.2"]
            ]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.0"]]}}
  :deploy-repositories [["releases" :clojars]]
  :aliases {"update-readme-version" ["shell" "sed" "-i" "s/\\\\[clj-george \"[0-9.]*\"\\\\]/[clj-george \"${:version}\"]/" "README.md"]}
  :release-tasks [["shell" "git" "diff" "--exit-code"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["changelog" "release"]
                  ["update-readme-version"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["deploy"]
                  ["vcs" "push"]])
