DROP TABLE IF EXISTS recipes;
CREATE TABLE recipes (
    id BIGSERIAL PRIMARY KEY,
    cuisine VARCHAR(255),
    title VARCHAR(255) NOT NULL,
    rating REAL,
    prep_time INTEGER,
    cook_time INTEGER,
    total_time INTEGER,
    description TEXT,
    nutrients JSONB,
    serves VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_recipes_rating ON recipes (rating DESC);
CREATE INDEX IF NOT EXISTS idx_recipes_cuisine ON recipes (cuisine);
CREATE INDEX IF NOT EXISTS idx_recipes_title_lower ON recipes (lower(title));
CREATE INDEX IF NOT EXISTS idx_recipes_nutrients_gin ON recipes USING GIN (nutrients);