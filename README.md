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

## Authentication

This application only supports WebAuthn as the authentication method.
There are two options to allow registration of the first authenticator for a user:

* Set the `:allow-register-page-access` value to `true` in the config file
* Set an environment variable called `ALLOW_REG_ACCESS` to a non-empty value

This allows you to register an authenticator by navigating to the
`<app url>/register` page. After registering the authenticator
change the config value back to `false` if you used the first option mentioned
above.
Further authenticators can be registered on the same page when being logged in
to the application.

## Configuration

Users are directly added the to the `users` table.

A sample configuration can be found in the `resources/config.edn_sample` file.
The configuration file used in development is `resources/dev/config.edn` and the
one for production in `resources/prod/config.edn`. After copying the
sample file edit either or both file(s) as needed.
Some settings can be overridden with environment variables or by reading from a
file. Accepted environment variables are described below.

* __APP_PORT__: The port which the application will be accessible through.
The default port is `8080`.
* __POSTGRESQL_DB_HOST__: Hostname of the database server.
* __POSTGRESQL_DB_PORT__: The port on which the database is listening.
* __POSTGRESQL_DB_NAME__: Name of the database.
* __POSTGRESQL_DB_USERNAME__: Username of the database user.

The database password cannot be set through an environment variable due to
security reasons. Instead it can be read from a file whose name is set in the
`POSTGRESQL_DB_PASSWORD_FILE` environment variable. This file must only contain
the database password on one line.

_NOTE_! The first variable is not defined in `config.edn`.

## Running
### Locally

To start the application locally run `clojure -M:run`. If the `target/classes`
directory does not exist then you need to run the `clojure -T:build build-java`
command to create the required Java .class file.

### Docker / podman

This application can be also be run in a Docker or podman container. To build the
container call `make build` from root directory of the application.
The container will be called `ktra-indexer`. The .jar file to run in in the
container can be executed with the `java -jar <name>.jar` command.

## License

See the MIT license in the LICENSE file.

Copyright © 2015-2022 Tero Paloheimo
