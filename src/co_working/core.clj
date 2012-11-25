(ns co-working.core
  (:require
   [co-working.crate.git :as git]
   [pallet.session :as session]
   [pallet.compute   :as compute])
  (:use [pallet.thread-expr]
        [pallet.crate.automated-admin-user :only [automated-admin-user]]
        [pallet.action.directory   :only [directory]]
        [pallet.action.remote-file :only [remote-file]]
        [pallet.action.file        :only [file symbolic-link]]
        [pallet.action.exec-script :only [exec-script exec-checked-script]]
        [pallet.action.package     :only [packages package-manager package]]
        [pallet.crate.java         :only [java-settings install-java]]
        [pallet.core               :only [group-spec server-spec node-spec admin-user converge lift]]
        [pallet.phase              :only [phase-fn]]
        [pallet.action.user        :only [user]]
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
      (package-manager :universe)
      (package-manager :multiverse)
      (package-manager :update)))

(defn standard-prereqs
  "General prerequesite packages and configurations"
  [request]
  (-> request
      (package-manager :update)
      (package-manager :upgrade)
      (packages :aptitude
                ["curl" "vim-nox" "ntp" "ntpdate" "htop" "gnu-standards" "flex"
                 "bison" "gdb" "gettext" "build-essential" "perl-doc" "unzip"
                 "rlwrap" "subversion" "unrar" "screen" "tmux"])
      (file "/etc/localtime" :action :delete :force true)
      (symbolic-link "/usr/share/zoneinfo/US/Eastern" "/etc/localtime"
                     :action :create
                     :force true)))


(defn clojure-development
  "tools needed to run clojure applications"
  [request]
  (let [administrative-user (:username (session/admin-user request))
        admin-home-dir (str "/home/" administrative-user)]
    (-> request
        (exec-script
         (if-not (which lein)
           (do
             (wget -q -O "/usr/local/bin/lein" "https://github.com/technomancy/leiningen/raw/stable/bin/lein")
             (chmod 755 ~"/usr/local/bin/lein")
             (sudo -u ~administrative-user (lein "self-install"))))))))

(defn co-working
  [request]
  (let [administrative-user (:username (session/admin-user request))
        admin-home-dir (str "/home/" administrative-user)]
    (-> request
        (packages :aptitude ["tmux"])
        (git/install-git)
        (git/clone-or-pull "/usr/local/share/wemux" "git://github.com/zolrath/wemux.git")
        (symbolic-link "/usr/local/share/wemux/wemux" "/usr/local/bin/wemux"
                     :action :create
                     :force true)
        (remote-file "/usr/local/etc/wemux.conf" :force true :action :create
                     :remote-file "/usr/local/share/wemux/wemux.conf.example")       
        (exec-script (if-not (wemux list)
                       (sudo -u ~administrative-user wemux new "-d")))
        (file "/tmp/wemux-wemux" :mode 1777))))

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
               (user user-name :shell "/bin/bash" :create-home true :home user-home-dir)
               (directory user-ssh-dir :owner user-name :group user-name :mode "755")
               (remote-file (str user-ssh-dir "/authorized_keys")
                            :local-file (str "resources/keyfiles/" keyfile)
                            :owner user-name :group user-name :mode "600")
               (exec-script
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
    (admin-user a-user
                     :private-key-path (str l-p-dir "/" a-user "_rsa")
                     :public-key-path (str l-p-dir "/" a-user "_rsa.pub"))))

;; ## If it is needed, this will set the administrative user as 'padmin'
;; - public/private ssh keys will be looked up in the ~/.pallet directory 
;; - default behavior with this turned off is to use the user/ssh keys of the user running the converge
;(def ^:dynamic *admin-user* (set-admin-user "padmin"))

(def co-worker-default-node
  (node-spec
   :hardware {:min-cores 1 :min-ram 512}
   :image {:os-family :debian :os-64-bit true}
   :network {:inbound-ports [22 80]}))

(def with-base-server
  (server-spec
   :phases {:bootstrap (phase-fn (automated-admin-user))
            :settings (phase-fn
                       (java-settings {:vendor :openjdk})
                       (java-settings {:vendor :oraclepp :version "7"
                                       :components #{:jdk}
                                       :instance-id :oracle-7}))
            :configure (phase-fn
                        (sane-package-manager)
                        ;; this will might your life better, but don't do on a slow internet connection
;                        (standard-prereqs)
                        (co-working)
                        (install-java)
                        (clojure-development)
;                        (update-users)
                        )
            :update-users (phase-fn (update-users))
            :clojure (phase-fn (clojure-development))
            :co-working (phase-fn (co-working))}))
 
(def co-worker-cs
  (group-spec
   "co-worker-cs" :extends [with-base-server]
   :node-spec co-worker-default-node))

;; Define compute
;(def aws-srvc (compute/compute-service-from-config-file :aws))
;(def vmfest (compute/compute-service-from-config-file :vmfest))

;; Examples of use
;;
;; Create a server
;(def cap (converge {co-worker-cs 1} :compute vmfest))
;(show-nodes vmfest)

;; Destroy all running servers
;(def cap (converge {co-worker-cs 0} :compute aws-srvc))
;(def cap (converge {co-worker-cs 0} :compute vmfest))

;; Install java on all running servers
;(def cap (lift co-worker-cs :compute vmfest :phase :java))

;; Install clojure on all running servers
;(def cap (lift co-worker-cs :compute vmfest :phase :clojure))

;; Output all a list of all runninger servers
;(pprint (show-nodes vmfest))

;; Update users on all running servers with the list in resources/users_keys.properties
;(def cap (lift co-worker-cs :compute vmfest :phase :update-users))
;(def cap (lift co-worker-cs :compute vmfest :phase :configure))
;(def cap (lift co-worker-cs :compute vmfest :phase :co-working))