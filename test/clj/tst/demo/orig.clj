(ns tst.demo.orig
  (:use tupelo.core tupelo.test)
  (:require
    [neo4j-clj.core :as neolib])
  (:import
    [java.net URI]))

(neolib/defquery create-user ; creates a global Var function, taking a session or tx as 1st arg
  "CREATE (u:User $User)
   return u as newb")

(neolib/defquery get-all-users ; creates a global Var function, taking a session or tx as 1st arg
  "MATCH (u:User)
   RETURN u as UZZER")

(defn delete-all-nodes! ; works, but could overflow jvm heap for large db's
  [conn-map]
  (with-open [session (neolib/get-session conn-map)]
    (unlazy (neolib/execute session "match (n) detach delete n;"))))

(defn neo4j-version
  [conn-map]
  (with-open [session (neolib/get-session conn-map)]
    (vec
      (neolib/execute session
        "call dbms.components() yield name, versions, edition
         unwind versions as version
         return name, version, edition ;"))))

(dotest
  (let [; a neo4j connection map with the driver under `:db`.  Sample:
        ;     {:url        #object[java.net.URI 0x1d4d84fb "neo4j+s://4ca9bb9b.databases.neo4j.io"],
        ;      :user       "neo4j",
        ;      :password   "g6o2KIftFE6EIYMUCIY9a6DW0oVcwihh7m0Z5DP-jcY",
        ;      :driver     #object[org.neo4j.driver.internal.InternalDriver 0x59e97f75 "org.neo4j.driver.internal.InternalDriver@59e97f75"],
        ;      :destroy-fn #object[neo4j_clj.core$connect$fn__19085 0x1d696b35 "neo4j_clj.core$connect$fn__19085@1d696b35"]}
        conn-map (neolib/connect (URI. "bolt://localhost:7687") ; uri
                   "neo4j" "secret") ; user/pass
        ]
    (is (map? conn-map))

    ; deleted users in DB from previous run
    (delete-all-nodes! conn-map)

    (is= (neo4j-version conn-map)
      [{:name "Neo4j Kernel", :version "4.3.3", :edition "enterprise"}])

    ; Using a session
    (with-open [session (neolib/get-session conn-map)]

      ; tests consume all output within the session lifetime
      (is= [{:newb {:first-name "Luke" :last-name "Skywalker"}}]
        (create-user session {:User {:first-name "Luke" :last-name "Skywalker"}}))
      (is= [{:newb {:first-name "Leia" :last-name "Organa"}}]
        (create-user session {:User {:first-name "Leia" :last-name "Organa"}}))
      (is= [{:newb {:first-name "Anakin" :last-name "Skywalker"}}]
        (create-user session {:User {:first-name "Anakin" :last-name "Skywalker"}})))

    ; Using a transaction
    (let [result (neolib/with-transaction conn-map tx
                   (unlazy ; or vec/doall to realize output within tx life
                     (get-all-users tx)))]
      (is-set= result
        [{:UZZER {:first-name "Luke" :last-name "Skywalker"}}
         {:UZZER {:first-name "Leia" :last-name "Organa"}}
         {:UZZER {:first-name "Anakin" :last-name "Skywalker"}}]))

    (is= [] (delete-all-nodes! conn-map))))
