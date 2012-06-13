(defproject co-working "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.cloudhoist/pallet "0.7.0-SNAPSHOT"]
                            [org.cloudhoist/pallet-crates-all "0.5.0"]
                 [org.cloudhoist/java "0.7.0-SNAPSHOT"]
                 [org.cloudhoist/pallet-jclouds "1.3.0-beta.1"]
                 ;; To get started we include all jclouds compute providers.
                 ;; You may wish to replace this with the specific jclouds
                 ;; providers you use, to reduce dependency sizes.
                 [org.jclouds/jclouds-allblobstore "1.3.2"]
                 [org.jclouds/jclouds-allcompute "1.3.2"]
                 [org.jclouds.driver/jclouds-slf4j "1.3.2"]
                 [org.slf4j/slf4j-api "1.6.1"]
                 [ch.qos.logback/logback-core "1.0.0"]
                 [ch.qos.logback/logback-classic "1.0.0"]]
  :dev-dependencies [[lein-marginalia "0.7.0"]
                     [org.cloudhoist/pallet-lein "0.4.1"]]
  :repositories {"sonatype" "https://oss.sonatype.org/content/repositories/releases"
                 "sonatype-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"})
