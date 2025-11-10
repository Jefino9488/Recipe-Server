package org.securin.recipe;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService svc;

    public RecipeController(RecipeService svc) { this.svc = svc; }

    @GetMapping
    public Page<Recipe> list(@RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "10") int limit) {
        if (page < 1) page = 1;
        PageRequest pr = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "rating"));
        return svc.findAllPaged(pr);
    }

    @GetMapping("/search")
    public Page<Recipe> search(@RequestParam(required = false) String title,
                               @RequestParam(required = false) String cuisine,
                               @RequestParam(required = false) String rating,
                               @RequestParam(required = false) String total_time,
                               @RequestParam(required = false) String calories,
                               @RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "10") int limit) {

        PageRequest pr = PageRequest.of(Math.max(0, page - 1), limit, Sort.by(Sort.Direction.DESC, "rating"));
        Optional<String> opTitle = Optional.ofNullable(title).filter(s -> !s.isBlank());
        Optional<String> opCuisine = Optional.ofNullable(cuisine).filter(s -> !s.isBlank());
        Optional<String> opRating = Optional.ofNullable(rating).filter(s -> !s.isBlank());
        Optional<String> opTotalTime = Optional.ofNullable(total_time).filter(s -> !s.isBlank());
        Optional<String> opCalories = Optional.ofNullable(calories).filter(s -> !s.isBlank());
        return svc.search(opTitle, opCuisine, opRating, opTotalTime, opCalories, pr);
    }
}
