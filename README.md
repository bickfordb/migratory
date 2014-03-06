# migratory

Clojure migration library

## Usage

```clojure
(ns my.project.migrations
  (:use migratory))

(defmigrations migrations)

(up add-account-table
  (do-sql "create account (id serial primary key, email text)"))

(up add-gender-to-account
  (do-sql "alter table account add column gender text not null default 'male'"))

; main method
(defn -main
 [& args]
 (migrate-up! :migrations migrations
              :conn-spec {... jdbc connection spec ...}))

```

## License

Copyright 2014 Brandon Bickford

Distributed under the GNU GPL V3 or later

