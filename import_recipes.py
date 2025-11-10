import json
import re
import sys
import psycopg2
from psycopg2.extras import Json
from typing import Any, Optional

def safe_float(val: Optional[str]) -> Optional[float]:
    if val is None:
        return None
    val = val.strip()
    if val.lower() == "nan":
        return None
    try:
        return float(val)
    except (ValueError, TypeError):
        return None

def safe_int(val: Optional[str]) -> Optional[int]:
    if val is None:
        return None
    val = val.strip()
    if val.lower() == "nan":
        return None
    digits = re.sub(r"[^\d\-]", "", val)
    if not digits:
        return None
    try:
        return int(digits)
    except ValueError:
        return None

def get_text(node: dict, *keys: str) -> Optional[str]:
    for k in keys:
        for key, value in node.items():
            if key.lower() == k.lower() and value is not None:
                return str(value) if not isinstance(value, (dict, list)) else None
    return None

def normalize_record(raw: dict) -> dict:
    return {
        "cuisine": get_text(raw, "cuisine"),
        "title": get_text(raw, "title", "Title") or "(no title)",
        "rating": safe_float(get_text(raw, "rating")),
        "prep_time": safe_int(get_text(raw, "prep_time", "prepTime", "prep_time_minutes")),
        "cook_time": safe_int(get_text(raw, "cook_time", "cookTime", "cook_time_minutes")),
        "total_time": safe_int(get_text(raw, "total_time", "totalTime", "total_time_minutes")),
        "description": get_text(raw, "description"),
        "nutrients": raw.get("nutrients") if isinstance(raw.get("nutrients"), dict) else None,
        "serves": get_text(raw, "serves", "serves_text"),
    }

def load_json(path: str):
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    if isinstance(data, dict):
        # Try numeric keys ("0", "1", ...) → list of values
        if all(k.isdigit() for k in data.keys()):
            return list(data.values())
        elif "title" in data or "url" in data:
            return [data]
        else:
            return list(data.values())
    elif isinstance(data, list):
        return data
    else:
        raise ValueError("Unsupported JSON top-level type")

def main(json_path: str, db_url: str):
    conn = psycopg2.connect(db_url)
    cur = conn.cursor()

    records = load_json(json_path)
    print(f"Loaded {len(records)} recipes from {json_path}")

    insert_sql = """
    INSERT INTO recipes
    (cuisine, title, rating, prep_time, cook_time, total_time, description, nutrients, serves)
    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
    """

    batch = []
    for raw in records:
        rec = normalize_record(raw)
        batch.append((
            rec["cuisine"],
            rec["title"],
            rec["rating"],
            rec["prep_time"],
            rec["cook_time"],
            rec["total_time"],
            rec["description"],
            Json(rec["nutrients"]) if rec["nutrients"] else None,
            rec["serves"],
        ))
        if len(batch) >= 500:
            cur.executemany(insert_sql, batch)
            conn.commit()
            print(f"Inserted {len(batch)} records")
            batch.clear()

    if batch:
        cur.executemany(insert_sql, batch)
        conn.commit()
        print(f"Inserted final {len(batch)} records")

    cur.close()
    conn.close()
    print("✅ Import finished.")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python import_recipes.py <recipes.json> <DB_URL>")
        print("Example: python import_recipes.py recipes.json 'postgresql://recipe:Jefino@1537@localhost:5432/recipes_db'")
        sys.exit(1)
    main(sys.argv[1], sys.argv[2])