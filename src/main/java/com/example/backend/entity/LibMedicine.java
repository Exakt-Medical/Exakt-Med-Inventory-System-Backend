package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "lib_medicine")
@Getter
@Setter
public class LibMedicine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drug_code", length = 455, nullable = false)
    private String drugCode;

    @Column(name = "drug_description", length = 455, nullable = false)
    private String drugDescription;

    @Column(name = "gen_code", length = 45)
    private String genCode;

    @Column(name = "salt_code", length = 45)
    private String saltCode;

    @Column(name = "form_code", length = 45)
    private String formCode;

    @Column(name = "strength_code", length = 45)
    private String strengthCode;

    @Column(name = "unit_code", length = 45)
    private String unitCode;

    @Column(name = "package_code", length = 45)
    private String packageCode;

}
