(defproject co-working "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.cloudhoist/pallet "0.7.2"]
                 [org.cloudhoist/automated-admin-user "0.5.0"]
                 [org.cloudhoist/java "0.7.2-SNAPSHOT"]
                 [org.cloudhoist/git "0.7.0-beta.1"]
                 [org.cloudhoist/pallet-jclouds "1.4.2"]
                 [org.cloudhoist/pallet-vmfest "0.2.0"]
                 ;; To get started we include all jclouds compute providers.
                 ;; You may wish to replace this with the specific jclouds
                 ;; providers you use, to reduce dependency sizes.
                 [org.jclouds/jclouds-allblobstore "1.4.2"]
                 [org.jclouds/jclouds-allcompute "1.4.2"]
                 [org.jclouds.driver/jclouds-slf4j "1.4.2"
                  :exclusions [org.slf4j/slf4j-api]]
                 [org.jclouds.driver/jclouds-jsch "1.4.2"]
                 [com.jcraft/jsch "0.1.48"]
                 [ch.qos.logback/logback-classic "1.0.0"]
                 [org.clojure/tools.cli "0.2.1"]]
  :dev-dependencies [[lein-marginalia "0.7.0"]
                     [org.cloudhoist/pallet-lein "0.5.2"]]
  :profiles {:dev
             {:dependencies [[org.cloudhoist/pallet "0.7.2" :classifier "tests"]]
              :plugins [[org.cloudhoist/pallet-lein "0.5.2"]]}}
  :local-repo-classpath true
  :repositories {"sonatype" "https://oss.sonatype.org/content/repositories/releases"
                 "sonatype-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"}
  :repl-options {:init (do (require 'gis-try.repl)
                           (gis-try.repl/force-slf4j))})
