# ktra-indexer

KTRA indexer is a simple Web application for listing and searching
artists and tracks from DJ Kutski's Keeping The Rave Alive (KTRA)
podcast. The application features episodes listing as well as both
track and artist search.

## Prerequisites

To run this application you will need [Leiningen][] 2.0.0 or
above installed. Additionally, a RDBMS, in this case PostgreSQL
is needed. Database definitions can be found in `db-def.sql` and
the database with the required tables must exist before the application
can be started.

This application is configured by editing the `resources/config.edn`
file. Users and their Yubikey ID(s) are directly added the to the
`users` and `yubikeys` tables respectively.

[leiningen]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein run

or

    lein trampoline run

PaaS hosting through Red Hat's OpenShift platform is supported by default.
Other PaaS platforms are currently not supported.

## License

See the MIT licsense in the LICENSE file.

Copyright © 2016 Tero Paloheimo
