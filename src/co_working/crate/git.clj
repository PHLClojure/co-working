(ns co-working.crate.git
  "Crate to install git."
  (:use [pallet.action.exec-script :only [exec-script]]
        [pallet.action.package :only [package-manager packages package]])
  (:require
   [pallet.session :as session]
   [pallet.action.package.epel :as epel]
   [pallet.thread-expr :as thread-expr]))

(defn install-git
  "Install git"
  [session]
  (->
   session
   (thread-expr/when->
    (#{:amzn-linux :centos} (session/os-family session))
    (epel/add-epel :version "5-4"))
   (package-manager :update)
   (packages
    :yum ["git" "git-email"]
    :aptitude ["git-core" "git-email"]
    :pacman ["git"])))

(defn clone-or-pull
  "clone or pull a private git repo
    uri: the remote location, form: user@url:repo.git
    repo: the path where a checkout should be cloned or updated"
  [request checkout uri & {:keys [branch remote-branch] :as options}]
  (let [administrative-user (:username (session/admin-user request))
        admin-home-dir (str "/home/" administrative-user)
        branch (if (nil? branch) "master" branch)]
    (-> request
        (exec-script
         (do
           (if (directory? ~checkout)
             (do (cd ~checkout)
                 (git pull))
             (git clone ~uri ~checkout))
           (do
             (cd ~checkout)
             (git "show-ref" "--verify" "--quiet" (str "refs/heads/" ~branch))
             (if-not (= 0 @?) ;; equivalent to if [ $? -ne 0 ]
               (git checkout "-b" ~branch ~remote-branch)
               (git checkout stable))))))))

;; (with-script-language :pallet.stevedore.bash/bash (script (defn doit [& args] (if ("/usr/bin/test" -z @user) (sudo -u ~user args) (args))) (doit ls "-l" "/home/hunter")))
;; http://palletops.com/doc/reference/script/