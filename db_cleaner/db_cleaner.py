#!/usr/bin/env python3

"""A script for cleaning up the ktra-indexer database.

Similar artist names are checked and removed.
"""

import argparse
import sys
from collections import OrderedDict

import editdistance
import psycopg


class DbCleaner:
    """A class for performing the actual cleaning."""

    def __init__(self, db_name, db_user, db_password):
        """Class constructor."""
        self._conn = psycopg.connect(dbname=db_name, user=db_user,
                                     password=db_password)
        self._cursor = self._conn.cursor()
        self._artists = OrderedDict()

    def get_artists(self):
        """Get all artists from the database."""
        self._cursor.execute('SELECT DISTINCT ON (name) name, artist_id '
                             'FROM artists ORDER BY name')
        artists = self._cursor.fetchall()
        for artist in artists:
            self._artists[artist[0]] = artist[1]

    def check_artists(self, edit_distance):
        """Go through each artists, filters them by edit distance and asks the user
        whether to merge to a another one.
        """
        self.get_artists()

        print('Press \'q\' to quit')
        print(f'Using an edit distance of {edit_distance}')

        while self._artists:
            keys = list(self._artists.keys())

            distances = [[art, editdistance.eval(keys[0], art)] for art in keys]
            filtered = list(filter(lambda item: item[1] <= edit_distance, distances))

            if len(filtered) == 1:
                self._artists.pop(filtered[0][0])
                continue

            print(f'Artists similar to "{filtered[0][0]}":')
            print('0. Retain all')
            for index, artist in enumerate(filtered):
                print('{}. {} (edit distance {})'.format(index + 1, artist[0],
                                                         artist[1]))

            choice = input('Choose option: ')
            choice = choice.rstrip('.')
            if choice == 'q':
                print('Stopping')
                break
            choice = int(choice)
            if choice > len(filtered):
                choice = input('Please choose an option between 0 and '
                               f'{len(filtered)}: ')
                choice = int(choice.rstrip('.'))

            if choice == 0:
                for index, _ in enumerate(filtered):
                    self._artists.pop(filtered[index][0])
                continue

            new_artist_id = self._artists[filtered[choice - 1][0]]

            for index, _ in enumerate(filtered):
                if index == (choice - 1):
                    self._artists.pop(filtered[index][0])
                    continue

                try:
                    old_id = self._artists[filtered[index][0]]
                    self._cursor.execute('UPDATE tracks SET artist_id = %s '
                                         'WHERE artist_id = %s',
                                         (new_artist_id, old_id))
                    self._cursor.execute('DELETE FROM artists WHERE artist_id = %s',
                                         (old_id,))
                except psycopg.DatabaseError as dbe:
                    self._cursor.rollback()
                    print(f'Error: got a database error: {dbe.pgerror}',
                          file=sys.stderr)
                    return False

                self._artists.pop(filtered[index][0])

        self._conn.commit()
        return True


def main():
    """Main function of the module."""
    parser = argparse.ArgumentParser(description='Cleans up a ktra-indexer database by '
                                     'removing artists whose names are close to each '
                                     'other. The "closeness" is compared with edit '
                                     'distance; an edit distance over 2 is practically '
                                     'unusable.')
    parser.add_argument('db_name', type=str, help='Name of the database')
    parser.add_argument('db_user', type=str, help='Database user name')
    parser.add_argument('db_password', type=str, help='Database user password')
    parser.add_argument('--distance', type=int, help='Default edit distance '
                        '(currently 1)', default=1)

    args = parser.parse_args()

    cleaner = DbCleaner(args.db_name, args.db_user, args.db_password)
    cleaner.check_artists(args.distance)


if __name__ == '__main__':
    main()
