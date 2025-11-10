package org.securin.recipe;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Formula;


@Entity
@Table(name = "recipes")
@Data
public class Recipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cuisine;

    @Column(length = 255, nullable = false)
    private String title;

    private Float rating;
    private Integer prepTime;
    private Integer cookTime;
    private Integer totalTime;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "jsonb")
    private String nutrients;

    private String serves;
    @Formula("(NULLIF(regexp_replace(nutrients->>'calories','[^0-9]','','g'),'')::int)")
    private Integer caloriesInt;
}