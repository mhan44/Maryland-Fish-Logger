import sqlite3
from pathlib import Path
from contextlib import closing

#Database directory
PROJECT_DIR = Path(__file__).resolve().parent.parent
DATABASE_PATH = PROJECT_DIR / "data/catches.db"


def init_db():
    DATABASE_PATH.parent.mkdir(parents=True, exist_ok=True)

    with closing(sqlite3.connect(DATABASE_PATH)) as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS catches (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                species TEXT,
                predicted_species TEXT,
                confidence REAL,
                length_inches REAL,
                weight_lbs REAL,
                date_caught TEXT,
                location TEXT,
                lure TEXT,
                notes TEXT,
                image_url TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.commit()


def save_catch(catch):
    with closing(sqlite3.connect(DATABASE_PATH)) as conn:
        cursor = conn.execute("""
            INSERT INTO catches (
                species, predicted_species, confidence,
                length_inches, weight_lbs, date_caught,
                location, lure, notes, image_url
            )
            VALUES (
                :species, :predicted_species, :confidence,
                :length_inches, :weight_lbs, :date_caught,
                :location, :lure, :notes, :image_url
            )
        """, catch)

        conn.commit()
        return cursor.lastrowid


def get_catches():
    with closing(sqlite3.connect(DATABASE_PATH)) as conn:
        conn.row_factory = sqlite3.Row

        rows = conn.execute(
            "SELECT * FROM catches ORDER BY id DESC"
        ).fetchall()

        return [dict(row) for row in rows]
