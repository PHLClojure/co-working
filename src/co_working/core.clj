(ns co-working.core
  (:require
   [pallet.core :as core]
   [pallet.crate.automated-admin-user :as automated-admin-user]
   [pallet.phase :as phase]
   [pallet.session :as session]
   [pallet.action.directory :as directory]
   [pallet.action.user :as user]
   [pallet.action.remote-file :as remote-file]
   [pallet.resource.service :as service]
   [pallet.crate.java :as java]
   [pallet.crate.git :as git]
   [pallet.resource.package :as package]
   [pallet.action.exec-script :as exec-script]
   [pallet.action.package :as package-action]
   [pallet.configure :as configure]
   [pallet.compute :as compute])
  (:use [pallet.thread-expr]
        [clojure.pprint]))

(defn show-nodes
  "A better node list"
  [srvc]
  (map #(vector (compute/id %)
                (compute/primary-ip %)
                (compute/group-name %))
       (compute/nodes srvc)))

(defn local-pallet-dir
  "Get the .pallet dir of the user currently running pallet"
  []
  (.getAbsolutePath
   (doto (if-let [pallet-home (System/getenv "PALLET_HOME")]
           (java.io.File. pallet-home)
           (java.io.File. (System/getProperty "user.home") ".pallet"))
     .mkdirs)))

(defn- sane-package-manager
  [request]
  (-> request
      (package/package-manager :universe)
      (package/package-manager :multiverse)
      (package/package-manager :update)))

(defn standard-prereqs
  "General prerequesite packages and configurations"
  [request]
  (-> request
      (package-action/package-manager :update)
      (package-action/package-manager :upgrade)
      (package/packages :aptitude
                        ["curl" "vim-nox" "ntp" "ntpdate" "htop" "gnu-standards" "flex" "bison" "gdb" "gettext"
                         "build-essential" "perl-doc" "unzip" "rlwrap" "git" "subversion" "unrar" "screen" "tmux"])
      (exec-script/exec-script
       (rm "/etc/localtime")
       (ln "-sf" "/usr/share/zoneinfo/US/Eastern" "/etc/localtime"))

      ;; Console-kit is not useful for a non-gui server.  Ubuntu 10.10+ installs by default
      (package-action/package "consolekit" :action :remove)))

(defn git-clone-or-pull
  "clone or pull a private git repo
    uri: the remote location, form: user@url:repo.git
    repo: the path where a checkout should be cloned or updated"
  [request checkout uri]
  (let [administrative-user (:username (session/admin-user request))
        admin-home-dir (str "/home/" administrative-user)]
    (-> request
        (exec-script/exec-script
         (if (directory? ~checkout)
           (do (cd ~checkout)
               (sudo -u ~administrative-user git pull))
           (do (sudo -u ~administrative-user
                     git clone ~uri ~checkout)))))))

(defn clojure-development
  "tools needed to run clojure applications"
  [request]
  (let [administrative-user (:username (session/admin-user request))
        admin-home-dir (str "/home/" administrative-user)]
    (-> request
        (exec-script/exec-script
         (if-not (which lein)
           (do
             (wget -q -O "/usr/local/bin/lein" "https://github.com/technomancy/leiningen/raw/stable/bin/lein")
             (chmod 755 ~"/usr/local/bin/lein")
             (sudo -u ~administrative-user (lein "self-install"))))
         (if-not (directory? (str ~admin-home-dir "/.vim"))
           (do
             (cd ~admin-home-dir)
             (sudo -u ~administrative-user git clone "https://github.com/daveray/vimclojure-easy.git" ".vim")
             (sudo -u ~administrative-user ln "-s" ".vim/vimrc.vim" ".vimrc")
             (sudo -u ~administrative-user make "-C" ".vim/lib/vimclojure-nailgun-client")
             (sudo -u ~administrative-user lein plugin install "org.clojars.ibdknox/lein-nailgun '1.1.1'")))))))

(defn co-worker-single-node
  [request]
  (-> request
      (package/packages :aptitude ["emacs"])
   
   ))
;; ## Set Admin User with Assumptions(tm)
;;
;; * We assume that the user running pallet commands will have a ~/.pallet directory
;; * ~/.pallet dir contains ssh keys for the specified admin user in the format: admin-user-name_rsa.pub
(defn set-admin-user
  "Use LSF conventions to assume locations of keys for admin-user"
  [a-user]
  (let [l-p-dir (local-pallet-dir)]
    (core/admin-user a-user
                     :private-key-path (str l-p-dir "/" a-user "_rsa")
                     :public-key-path (str l-p-dir "/" a-user "_rsa.pub"))))

                                        ;(def ^:dynamic *admin-user* (set-admin-user "hadmin"))
(def ^:dynamic *admin-user* (set-admin-user "hadmin"))

(def co-worker-default-node
  (core/node-spec
   ;:image {:image-id "us-east-1/ami-4dad7424"} ;; 64 bit 11.10 (Oneiric) EBS
   :image {:os-family :ubuntu :os-version-matches "11.10"}
   ;:hardware {:min-ram 1024}
   ;:hardware {:hardware-id "m1.large"}
   ;:location {:location-id "us-east-1"}
   :network {:inbound-ports [22 80]} ;; includes 9160, default cassandra client port
   ))

(def with-base-server
  (core/server-spec
   :phases {:bootstrap (phase/phase-fn (automated-admin-user/automated-admin-user))
            :settings (phase/phase-fn
                       (java/java-settings {:vendor :openjdk})
                       (java/java-settings {:vendor :oracle :version "7"
                                       :components #{:jdk}
                                       :instance-id :oracle-7}))
            :configure (phase/phase-fn
                        (sane-package-manager)
                                       (standard-prereqs)
;                                       (java/install-java :package :sun :bin :jdk)
                                       ;(clojure-development)
                                       )
            :java (phase/phase-fn
                   (java/install-java)
;                   (java/install-java :instance-id :oracle-7)
                   )
            :clojure (phase/phase-fn (clojure-development))
            }))

 
(def co-worker-cs
  (core/group-spec
   "co-worker-cs" :extends [with-base-server]
   :node-spec co-worker-default-node))

(def rack-srvc (compute/compute-service-from-config-file :hrack))


;(def cap (core/converge {co-worker-cs 1} :compute rack-srvc))
;(def cap (core/lift co-worker-cs :compute rack-srvc :phase :java))
;(def cap (core/lift co-worker-cs :compute rack-srvc :phase :clojure))
;(show-nodes rack-srvc)

#_
(defn -main
  "I don't do a whole lot."
  [& args]
  (println "Hello, World!"))
