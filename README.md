# recipieServer

Lightweight Spring Boot REST service that exposes a recipes API and supports paginated listing and flexible searching. The project includes a small JPA model, a search service using JPA Specifications, a database schema file, and a Python helper to import recipes into PostgreSQL.

---

## Table of contents

- [Project overview](#project-overview)
- [Requirements](#requirements)
- [Database schema](#database-schema)
- [Configuration](#configuration)
- [Build & run](#build--run)
- [Running tests](#running-tests)
- [Importing recipes](#importing-recipes)
- [API](#api)
    - [List recipes](#list-recipes)
    - [Search recipes](#search-recipes)
- [Notes & troubleshooting](#notes--troubleshooting)
- [Security](#security)

---

## Project overview

This repository contains:

- A Spring Boot application (Java 21) exposing endpoints under `/api/recipes`
- JPA entity `Recipe` mapped to a `recipes` table
- `recipes.sql` — schema and useful indexes
- `import_recipes.py` — Python helper script to import recipe JSON files into the DB
- SpringDoc (OpenAPI/Swagger) included for API docs

---

## Requirements

- Java 21 (or compatible JDK)
- Maven (the project includes the Maven wrapper `mvnw` / `mvnw.cmd`)
- PostgreSQL (tested with recent versions)
- Python 3.x if you want to use the import script
    - Python dependency: `psycopg2` or `psycopg2-binary` to run the import script

---

## Database schema

The schema is in `recipes.sql`. Main table:

- `recipes`:
    - `id BIGSERIAL PRIMARY KEY`
    - `cuisine VARCHAR(255)`
    - `title VARCHAR(255) NOT NULL`
    - `rating REAL`
    - `prep_time INTEGER`
    - `cook_time INTEGER`
    - `total_time INTEGER`
    - `description TEXT`
    - `nutrients JSONB`
    - `serves VARCHAR(255)`
    - `created_at / updated_at TIMESTAMPTZ`

Indexes included:
- `idx_recipes_rating` (rating DESC)
- `idx_recipes_cuisine`
- `idx_recipes_title_lower` (lower(title))
- `idx_recipes_nutrients_gin` (GIN index on nutrients JSONB)

Note: The `Recipe` entity uses an `@Formula` to extract an integer calories value from `nutrients->>'calories'` for numeric filtering (see code: `caloriesInt`).

---

## Configuration

Default configuration is in `src/main/resources/application.properties`:

- spring.datasource.url=jdbc:postgresql://localhost:5432/recipes_db
- spring.datasource.username=recipe
- spring.datasource.password=Jefino@1537
- spring.jpa.hibernate.ddl-auto=validate
- server.port=8081

You should override these values for your environment (e.g., with environment variables or an external application.properties). When using the Maven wrapper you can pass profiles or system properties if needed.

Example: to override DB URL at runtime:

```
java -jar target/recipieServer-0.0.1-SNAPSHOT.jar \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/recipes_db \
  --spring.datasource.username=recipe \
  --spring.datasource.password=your_password
```

---

## Build & run

Using the Maven wrapper (recommended):

Unix / macOS:
- Build: `./mvnw clean package`
- Run: `./mvnw spring-boot:run` (or run the generated jar in `target/`)

Windows (cmd/powershell):
- Build: `mvnw.cmd clean package`
- Run: `mvnw.cmd spring-boot:run`

The app listens on port 8081 by default (see `application.properties`).

---

## Running tests

Run unit tests / integration tests with Maven:

```
./mvnw test
```

---

## Importing recipes

The repository includes `import_recipes.py` — a utility to import recipe JSON into PostgreSQL.

Install required Python package first:

```
pip install psycopg2-binary
```

Usage:

```
python import_recipes.py <recipes.json> "<DB_URL>"
```

Example DB URL (Postgres URI format):

```
postgresql://username:password@host:5432/recipes_db
```

If your password contains special characters like `@`, wrap/quote the full URL and/or URL-encode the password. Example:

```
python import_recipes.py recipes.json "postgresql://recipe:pass%40word@localhost:5432/recipes_db"
```

Large files are inserted in batches (500 records per commit). The script does simple normalization and supports input that is either a list of recipe objects or an object with numeric string keys.

---

## API

Base path: `/api/recipes`

OpenAPI/Swagger UI:
- Swagger UI is available (SpringDoc) — open `http://localhost:8081/swagger-ui.html` or `http://localhost:8081/swagger-ui/index.html`
- Raw OpenAPI JSON: `http://localhost:8081/v3/api-docs`

All endpoints return paginated `Page<Recipe>` responses.

Recipe model key fields:
- id, cuisine, title, rating, prepTime, cookTime, totalTime, description, nutrients (JSON as string), serves, caloriesInt (derived)

### List recipes

GET /api/recipes

Query parameters:
- page (default 1) — 1-based page number
- limit (default 10) — page size

Example:

```
curl "http://localhost:8081/api/recipes?page=1&limit=20"
```

Results are sorted by `rating` descending by default.

### Search recipes

GET /api/recipes/search

Supported query parameters (all optional):
- title — substring search, case-insensitive (partial match)
- cuisine — exact match (case-insensitive)
- rating — numeric filter; supports operators `>`, `>=`, `<`, `<=`, `=`. If no operator provided, exact match is used. Example: `rating=>=4.5`
- total_time — numeric filter on `totalTime` (supports same operators)
- calories — numeric filter on derived `caloriesInt` (supports same operators)
- page, limit — pagination

Examples:

- Search by partial title:
  ```
  curl "http://localhost:8081/api/recipes/search?title=chocolate"
  ```

- Search by cuisine:
  ```
  curl "http://localhost:8081/api/recipes/search?cuisine=italian"
  ```

- Search rating >= 4:
  ```
  curl "http://localhost:8081/api/recipes/search?rating=>=4"
  ```

- Combined filters (rating and calories):
  ```
  curl "http://localhost:8081/api/recipes/search?rating=>=4&calories=<500"
  ```

Notes about operators:
- The service parses operators at the start of the numeric parameter value. Examples:
    - `>=500`, `> 30`, `<100`
    - If no operator is present, equals (`=`) is assumed.

Behavior:
- `title` uses `LIKE %...%` (case-insensitive)
- `cuisine` uses case-insensitive equality (the service lowercases both sides)
- Numeric filters operate on numeric fields (rating: float, total time: integer, calories: integer)

---

## Notes & troubleshooting

- If Spring Boot fails on startup with `hibernate.hbm2ddl` validation errors, ensure your DB schema matches `recipes.sql` (column names/types). The project uses `spring.jpa.hibernate.ddl-auto=validate` which requires the schema to exist and match the mappings.
- The `nutrients` column is JSONB. The application stores the JSON as a String on the entity but the DB column is jsonb. The `caloriesInt` field is computed using an SQL `@Formula` (`regexp_replace(nutrients->>'calories','[^0-9]','','g')::int`), so ensure your JSON `nutrients` objects include a `calories` key in a recognizable format if you want numeric filtering by calories to work.
- If `caloriesInt` computation fails for some rows (e.g., missing or non-numeric calories string), those rows may return NULL for `caloriesInt`.
- When importing large datasets, monitor DB resources (disk, connection limits).

---

## Security

- Do not commit database credentials to VCS. The included `application.properties` contains example credentials — change them before deployment.
- When using the Python import script, be careful with credentials in shell command history. Prefer environment variables or a secure secrets store.
- If exposing the API publicly, add proper authentication/authorization, rate limiting, and input validation.

---

## License & contact

This README does not include a formal license. Add a LICENSE file if you plan to publish this project.

For questions about the code or help running the project, provide details about your platform (OS, Java version, PostgreSQL version) and any error messages.