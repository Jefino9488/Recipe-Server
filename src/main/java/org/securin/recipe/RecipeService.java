package org.securin.recipe;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RecipeService {

    private final RecipeRepository repo;
    private final EntityManager em;

    public RecipeService(RecipeRepository repo, EntityManager em) {
        this.repo = repo;
        this.em = em;
    }

    public Page<Recipe> findAllPaged(Pageable pageable) {
        return repo.findAll(pageable);
    }

    public Page<Recipe> search(
            Optional<String> title,
            Optional<String> cuisine,
            Optional<String> ratingRaw,
            Optional<String> totalTimeRaw,
            Optional<String> caloriesRaw,
            Pageable pageable) {

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (title.isPresent()) {
            where.append(" AND lower(title) LIKE ? ");
            params.add("%" + title.get().toLowerCase() + "%");
        }

        if (cuisine.isPresent()) {
            where.append(" AND lower(cuisine) = ? ");
            params.add(cuisine.get().toLowerCase());
        }

        if (ratingRaw.isPresent()) {
            String[] opVal = parseOpVal(ratingRaw.get());
            where.append(" AND rating ").append(opVal[0]).append(" ? ");
            params.add(Float.parseFloat(opVal[1]));
        }

        if (totalTimeRaw.isPresent()) {
            String[] opVal = parseOpVal(totalTimeRaw.get());
            where.append(" AND total_time ").append(opVal[0]).append(" ? ");
            params.add(Integer.parseInt(opVal[1]));
        }

        if (caloriesRaw.isPresent()) {
            String[] opVal = parseOpVal(caloriesRaw.get());
            where.append(" AND (NULLIF(regexp_replace(nutrients->>'calories','[^0-9]','','g'),'')::int) ")
                    .append(opVal[0]).append(" ? ");
            params.add(Integer.parseInt(opVal[1]));
        }

        String countSql = "SELECT count(*) FROM recipes " + where;
        Query countQuery = em.createNativeQuery(countSql);
        for (int i = 0; i < params.size(); i++) {
            countQuery.setParameter(i + 1, params.get(i));
        }
        Number total = (Number) countQuery.getSingleResult();

        // Fetch paginated results
        String selectSql = "SELECT * FROM recipes " + where +
                " ORDER BY rating DESC NULLS LAST LIMIT ? OFFSET ?";
        Query selectQuery = em.createNativeQuery(selectSql, Recipe.class);
        int idx = 1;
        for (Object param : params) {
            selectQuery.setParameter(idx++, param);
        }
        selectQuery.setParameter(idx++, pageable.getPageSize());
        selectQuery.setParameter(idx, (int) pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<Recipe> results = selectQuery.getResultList();

        return new PageImpl<>(results, pageable, total.longValue());
    }

    private String[] parseOpVal(String raw) {
        raw = raw.trim();
        if (raw.startsWith(">=") || raw.startsWith("<=")) {
            return new String[]{raw.substring(0, 2), raw.substring(2).trim()};
        } else if (raw.startsWith(">") || raw.startsWith("<") || raw.startsWith("=")) {
            return new String[]{raw.substring(0, 1), raw.substring(1).trim()};
        } else {
            return new String[]{"=", raw}; // default to equality
        }
    }
}