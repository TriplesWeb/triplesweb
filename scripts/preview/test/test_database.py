"""Opt-in integration test; all writes target a newly created local container."""
import contextlib
import os
from pathlib import Path
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import pymysql
from data import LocalDatabase, docker, dump_source


@unittest.skipUnless(os.environ.get('PREVIEW_DB_TESTS') == '1', 'set PREVIEW_DB_TESTS=1 for disposable MariaDB tests')
class DatabaseTests(unittest.TestCase):
    def test_read_only_snapshot_binary_generated_columns_views_and_rollback(self):
        with tempfile.TemporaryDirectory(prefix='pr58-db-') as directory:
            db = LocalDatabase(directory, port=23307)
            try:
                db.start()
                with db.connect() as connection, connection.cursor() as cursor:
                    cursor.execute('CREATE DATABASE origin_fixture CHARACTER SET utf8mb4')
                    cursor.execute('CREATE TABLE origin_fixture.sample (id INT PRIMARY KEY, title TEXT, image LONGBLOB, amount INT, calculated INT GENERATED ALWAYS AS (amount*2) STORED)')
                    cursor.execute('INSERT INTO origin_fixture.sample (id,title,image,amount) VALUES (%s,%s,%s,%s)',
                                   (1, "한글 ' quote \\ and /uploads/images/한.png", bytes([0, 255, 128, 39]), 3))
                    cursor.execute('CREATE VIEW origin_fixture.sample_view AS SELECT id,title FROM origin_fixture.sample')
                source = pymysql.connect(host='127.0.0.1', port=db.port, user='root', password=db.secret.read_text(),
                                         database='origin_fixture', charset='utf8mb4')
                with contextlib.closing(source), source.cursor() as cursor:
                    cursor.execute('SET SESSION TRANSACTION READ ONLY')
                    cursor.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
                    with self.assertRaises(pymysql.Error):
                        cursor.execute('UPDATE sample SET amount=100')
                    manifest = dump_source(source, Path(directory) / 'source.sql')
                self.assertIn('images/한.png', manifest['references'])
                db.restore(Path(directory) / 'source.sql')
                with db.connect() as connection, connection.cursor() as cursor:
                    cursor.execute('SELECT image,amount,calculated FROM sample')
                    self.assertEqual(cursor.fetchone(), (bytes([0, 255, 128, 39]), 3, 6))
                    cursor.execute('SELECT COUNT(*) FROM sample_view')
                    self.assertEqual(cursor.fetchone()[0], 1)
                db.backup(Path(directory) / 'backup.sql')
                with db.connect() as connection, connection.cursor() as cursor:
                    cursor.execute('UPDATE sample SET amount=9')
                db.restore(Path(directory) / 'backup.sql')
                with db.connect() as connection, connection.cursor() as cursor:
                    cursor.execute('SELECT amount FROM sample')
                    self.assertEqual(cursor.fetchone()[0], 3)
                    cursor.execute('SELECT amount FROM origin_fixture.sample')
                    self.assertEqual(cursor.fetchone()[0], 3)
            finally:
                docker('rm', '-f', db.container, stdout=__import__('subprocess').DEVNULL)
                docker('volume', 'rm', db.volume, stdout=__import__('subprocess').DEVNULL)


if __name__ == '__main__':
    unittest.main()
