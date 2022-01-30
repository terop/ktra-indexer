# ktra-indexer

[![CircleCI](https://circleci.com/gh/terop/ktra-indexer/tree/master.svg?style=svg)](https://circleci.com/gh/terop/ktra-indexer/tree/master)

KTRA indexer is a simple Web application for listing and searching
artists and tracks from DJ Kutski's Keeping The Rave Alive (KTRA)
podcast. The application features episodes listing as well as both
track and artist search.

## Prerequisites

To build and run this application locally you need a recent Clojure version.
Additionally, a PostgreSQL server instance is needed. Database definitions can
be found in `db-def.sql` and a database with the required tables must exist
before the application can be started.

## Configuration

A sample configuration can be found in the `resources/config.edn_sample` file.
Copy or rename this file to `config.edn` in the `resources` directory and edit
it to fit your configuration. Users and their Yubikey ID(s) are directly added
the to the `users` and `yubikeys` tables respectively. Some settings can be overridden
with environment variables. Accepted environment variables are described below.
* __APP_PORT__: The port which the application will be accessible through.
The default port is `8080`.
* __POSTGRESQL_DB_HOST__: Hostname of the database server.
* __POSTGRESQL_DB_PORT__: The port on which the database is listening.
* __POSTGRESQL_DB_NAME__: Name of the database.
* __POSTGRESQL_DB_USERNAME__: Username of the database user.
* __POSTGRESQL_DB_PASSWORD__: Password of the database user.

_NOTE_! The first variable is not defined in `config.edn`.

## Running
### Locally
To start the application locally run `clojure -M:run`.

### Docker / podman

This application can be also be run in a Docker or podman container. To build the
container call `make build` from root directory of the application.
The container will be called `ktra-indexer`. The .jar file to run in in the
container can be executed with the `java -jar <name>.jar` command.

## License

See the MIT license in the LICENSE file.

Copyright © 2015-2022 Tero Paloheimo
