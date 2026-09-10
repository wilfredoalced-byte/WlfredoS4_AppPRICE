"""Persistencia del servidor. Motor de base de datos: SQLite."""
import os
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parent
for line in (ROOT / '.env').read_text(encoding='utf-8').splitlines() if (ROOT / '.env').exists() else []:
    if '=' in line and not line.lstrip().startswith('#'):
        key, value = line.split('=', 1)
        os.environ.setdefault(key.strip(), value.strip())


class Database:
    def __init__(self):
        self.connection = sqlite3.connect(os.getenv('SQLITE_PATH', str(ROOT / 'price.sqlite3')), timeout=20)
        self.connection.row_factory = sqlite3.Row
        self.connection.execute('PRAGMA foreign_keys=ON')
        self.connection.execute('PRAGMA journal_mode=WAL')

    def execute(self, sql, args=()):
        cursor = self.connection.cursor()
        cursor.execute(sql, args)
        return cursor

    def one(self, sql, args=()):
        row = self.execute(sql, args).fetchone()
        return dict(row) if row is not None else None

    def all(self, sql, args=()):
        return [dict(row) for row in self.execute(sql, args).fetchall()]

    def __enter__(self):
        return self

    def __exit__(self, kind, error, traceback):
        self.connection.rollback() if error else self.connection.commit()
        self.connection.close()


def initialize():
    schema = ROOT.parent / 'base_datos' / 'servidor_sqlite.sql'
    with Database() as db:
        for statement in schema.read_text(encoding='utf-8').split(';'):
            if statement.strip():
                db.execute(statement)
