import sqlite3
import os

db_path = os.path.join('app', 'db', 'abbreviations.db')
search_keys = ["재", "준", "고"] # Specific Korean characters to search for

try:
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    print("Searching for specific Korean characters in 'abbreviations' table:")
    found_matches = False
    for key in search_keys:
        cursor.execute("SELECT short, full FROM abbreviations WHERE short = ?", (key,))
        rows = cursor.fetchall()
        if rows:
            found_matches = True
            for row in rows:
                print(f"  Found: Short: {row[0]}, Full: {row[1]}")
        else:
            print(f"  '{key}' not found as a short abbreviation.")

    if not found_matches:
        print("No specific Korean characters found as short abbreviations.")

    print("\nFull contents of 'abbreviations' table:")
    cursor.execute("SELECT short, full FROM abbreviations")
    all_rows = cursor.fetchall()
    if all_rows:
        for row in all_rows:
            print(f"Short: {row[0]}, Full: {row[1]}")
    else:
        print("Table 'abbreviations' is empty or does not exist.")


except sqlite3.Error as e:
    print(f"SQLite error: {e}")
except Exception as e:
    print(f"An unexpected error occurred: {e}")
finally:
    if 'conn' in locals() and conn:
        conn.close()
