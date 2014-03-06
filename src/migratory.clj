(ns migratory
  (:use lg)
  (:require [clojure.java.jdbc :as jdbc]))

(defn migration-set
  "Create a new migration set"
  []
  (atom []))

(def last-migration-set (atom (migration-set)))

(defmacro def-migrations
  "Create a new migration set"
  [migration-name]
  `(let [mset# (migration-set)]
     (def ~migration-name mset#)
     (reset! last-migration-set mset#)))

(defn add-migration!
  [migration-set
   migration-type
   migration-name
   migration-fn]
  (swap! migration-set conj {:type migration-type
                             :name migration-name
                             :f migration-fn}))

(defmacro up
  [name-sym & body]
  `(add-migration! @migratory/last-migration-set :up (name '~name-sym) (fn [] ~@body)))

(def default-migrate-opts
  {:table "migrations"
   :conn-spec {}})

(defn do-sql
  [& sqls]
  (doseq [sql sqls]
    (debug "run sql: %s" sql)
    (jdbc/do-prepared sql)))

(defn- dotx
  [& body]
  `(do
     (do-sql "BEGIN")
     (do ~@body)
     (do-sql "COMMIT")))

(defn- migration-missing?
  [migration-table migration-name]
  (jdbc/with-query-results results
                 [(str "select * from \""
                       migration-table
                       "\" where \"name\" = '" migration-name "'")]
                 (= (count results) 0)))

(defn- migration-table-missing?
  [table-name]
  (jdbc/with-query-results results
                           [(format "select table_name from information_schema.tables
                                   where table_name = '%s'" table-name)]
                           (= (count results) 0)))

(defn- create-migration-table!
  [migration-table]
  (when (migration-table-missing? migration-table)
    (jdbc/do-prepared
      (format "CREATE TABLE \"%s\" (
              \"id\" SERIAL PRIMARY KEY,
              \"name\" TEXT NOT NULL,
              \"created_at\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              UNIQUE (\"name\"))" migration-table))))

(defn- mark-migration!
  [table-name migration-name]
  (do-sql (format "insert into \"%s\" (\"name\") values ('%s')" table-name migration-name)))

(defn- run-migration-if-missing!
  [migration-table
   {migration-name :name
    migration-fn :f}]
  (dotx
    (when (migration-missing? migration-table migration-name)
      (info "running migration: %s" migration-name)
      (migration-fn)
      (mark-migration! migration-table migration-name)
      (info "ran migration: %s" migration-name))))

(defn migrate-up!
  [& opts]
    (let [opts (apply hash-map opts)
          default-opts {:migrations @last-migration-set
                        :conn-spec nil
                        :table "migrations"}
          opts (merge default-opts opts)
          {conn-spec :conn-spec
           migration-set :migrations
           table :table} opts]
      (jdbc/with-connection
        conn-spec
        (create-migration-table! table)
        (doseq [migration @migration-set
                :when (= (:type migration) :up)]
          (run-migration-if-missing! table migration)))))

