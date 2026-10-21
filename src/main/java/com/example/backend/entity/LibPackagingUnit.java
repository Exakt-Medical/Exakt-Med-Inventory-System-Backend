package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lib_packaging_unit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LibPackagingUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String category;

    @Column
    private Integer defaultUnits;

    public LibPackagingUnit(String code, String name, String category, Integer defaultUnits) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.defaultUnits = defaultUnits;
    }
}
