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
                        ["curl" "vim-nox" "ntp" "ntpdate" "htop" "gnu-standards" "flex"
                         "bison" "gdb" "gettext" "build-essential" "perl-doc" "unzip"
                         "rlwrap" "git" "subversion" "unrar" "screen" "tmux"])
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

(defn load-props
  [file-name]
  (with-open [^java.io.Reader reader (clojure.java.io/reader file-name)]
    (let [props (java.util.Properties.)]
      (.load props reader)
      (into {} (for [[k v] props] [(keyword k) (read-string v)])))))

(defn update-users
  "load in configuration from file and add users"
  [request]
  (let [administrative-user (:username (session/admin-user request))
        admin-home-dir (str "/home/" administrative-user)
        user-git-dir "/www/analyticplus"
        users-list (load-props (str "resources/users_keys.properties"))]
    (-> request
        (for-> [[uname keyfile] users-list
                :let [user-home-dir (str "/home/" (name uname))
                      user-name (name uname)
                      user-www-dir (str user-home-dir "/www")
                      user-ssh-dir (str user-home-dir "/.ssh")
                      git-clone-target (str user-home-dir user-git-dir)]]
               (user/user user-name :shell "/bin/bash" :create-home true :home user-home-dir)
               (directory/directory user-ssh-dir :owner user-name :group user-name :mode "755")
               (remote-file/remote-file (str user-ssh-dir "/authorized_keys")
                            :local-file (str "resources/keyfiles/" keyfile)
                            :owner user-name :group user-name :mode "600")
               (exec-script/exec-script
                (if (file-exists? (str ~admin-home-dir "/.ssh/known_hosts"))
                  (rm (str ~admin-home-dir "/.ssh/known_hosts"))))))))

;; ## Set Admin User with Assumptions(tm)
;;
;; * We assume that the user running pallet commands will have a ~/.pallet directory
;; * ~/.pallet dir contains ssh keys for the specified admin user in the format: admin-user-name_rsa.pub
(defn set-admin-user
  "Use conventions to assume locations of keys for admin-user"
  [a-user]
  (let [l-p-dir (local-pallet-dir)]
    (core/admin-user a-user
                     :private-key-path (str l-p-dir "/" a-user "_rsa")
                     :public-key-path (str l-p-dir "/" a-user "_rsa.pub"))))

;; ## If it is needed, this will set the administrative user as 'padmin'
;; - public/private ssh keys will be looked up in the ~/.pallet directory 
;; - default behavior with this turned off is to use the user/ssh keys of the user running the converge
;(def ^:dynamic *admin-user* (set-admin-user "padmin"))

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
                        (java/install-java)
                        (clojure-development))
            :java (phase/phase-fn
                   (java/install-java))
            :update-users (phase/phase-fn (update-users))
            :clojure (phase/phase-fn (clojure-development))
            }))

 
(def co-worker-cs
  (core/group-spec
   "co-worker-cs" :extends [with-base-server]
   :node-spec co-worker-default-node))

(def aws-srvc (compute/compute-service-from-config-file :aws))

;; Examples of use
;;
;; Create a server
;(def cap (core/converge {co-worker-cs 1} :compute aws-srvc))
;; Destroy all running servers
;(def cap (core/converge {co-worker-cs 0} :compute aws-srvc))
;; Install java on all running servers
;(def cap (core/lift co-worker-cs :compute aws-srvc :phase :java))
;; Install clojure on all running servers
;(def cap (core/lift co-worker-cs :compute rack-srvc :phase :clojure))
;; Output all a list of all runninger servers
;(pprint (show-nodes aws-srvc))
;; Update users on all running servers with the list in resources/users_keys.properties
;(def cap (core/lift co-worker-cs :compute aws-srvc :phase update-users))
