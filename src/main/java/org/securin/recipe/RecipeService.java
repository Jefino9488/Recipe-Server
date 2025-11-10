package org.securin.recipe;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Path;
import java.util.Optional;

@Service
public class RecipeService {

    private final RecipeRepository repo;

    public RecipeService(RecipeRepository repo) {
        this.repo = repo;
    }

    public Page<Recipe> findAllPaged(Pageable pageable) {
        return repo.findAll(pageable);
    }

    /**
     * Simplified search using Spring Data JPA Specifications.
     * - title: case-insensitive containment
     * - cuisine: case-insensitive exact
     * - rating / totalTime / calories: allow simple ops like ">=4.5", "<30", "10"
     *   calories uses the derived caloriesInt field (see Recipe.@Formula).
     *
     * Note: Specification.where(...) is deprecated — start with a conjunction spec.
     */
    public Page<Recipe> search(
            Optional<String> title,
            Optional<String> cuisine,
            Optional<String> ratingRaw,
            Optional<String> totalTimeRaw,
            Optional<String> caloriesRaw,
            Pageable pageable) {

        // Start with a "conjunction" specification (always true), then and() further predicates.
        Specification<Recipe> spec = (root, query, cb) -> cb.conjunction();

        if (title.isPresent() && !title.get().isBlank()) {
            String t = "%" + title.get().toLowerCase().trim() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("title")), t));
        }

        if (cuisine.isPresent() && !cuisine.get().isBlank()) {
            String c = cuisine.get().toLowerCase().trim();
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("cuisine")), c));
        }

        if (ratingRaw.isPresent() && !ratingRaw.get().isBlank()) {
            String[] opVal = parseOpVal(ratingRaw.get());
            Float v = tryParseFloat(opVal[1]);
            if (v != null) {
                spec = spec.and(numericPredicate("rating", opVal[0], v));
            } else {
                throw new IllegalArgumentException("Invalid rating value: " + opVal[1]);
            }
        }

        if (totalTimeRaw.isPresent() && !totalTimeRaw.get().isBlank()) {
            String[] opVal = parseOpVal(totalTimeRaw.get());
            Integer v = tryParseInt(opVal[1]);
            if (v != null) {
                spec = spec.and(numericPredicate("totalTime", opVal[0], v));
            } else {
                throw new IllegalArgumentException("Invalid totalTime value: " + opVal[1]);
            }
        }

        if (caloriesRaw.isPresent() && !caloriesRaw.get().isBlank()) {
            String[] opVal = parseOpVal(caloriesRaw.get());
            Integer v = tryParseInt(opVal[1]);
            if (v != null) {
                // caloriesInt is the derived integer field from the JSON using @Formula in Recipe
                spec = spec.and(numericPredicate("caloriesInt", opVal[0], v));
            } else {
                throw new IllegalArgumentException("Invalid calories value: " + opVal[1]);
            }
        }

        return repo.findAll(spec, pageable);
    }

    // Helper to build a numeric predicate (supports >, >=, <, <=, =)
    private <N extends Number & Comparable<N>> Specification<Recipe> numericPredicate(String fieldName, String op, N value) {
        return (root, query, cb) -> {
            Path<N> path = root.get(fieldName);
            switch (op) {
                case ">":
                    return cb.gt(path.as(Number.class), value);
                case ">=":
                    return cb.ge(path.as(Number.class), value);
                case "<":
                    return cb.lt(path.as(Number.class), value);
                case "<=":
                    return cb.le(path.as(Number.class), value);
                default:
                    return cb.equal(path, value);
            }
        };
    }

    private String[] parseOpVal(String raw) {
        raw = raw.trim();
        if (raw.startsWith(">=") || raw.startsWith("<=")) {
            return new String[]{raw.substring(0, 2), raw.substring(2).trim()};
        } else if (raw.startsWith(">") || raw.startsWith("<") || raw.startsWith("=")) {
            return new String[]{raw.substring(0, 1), raw.substring(1).trim()};
        } else {
            return new String[]{"=", raw};
        }
    }

    private Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Float tryParseFloat(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}