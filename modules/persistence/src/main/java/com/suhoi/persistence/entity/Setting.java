package com.suhoi.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "settings")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Setting {

    @Id
    @Column(name = "key", nullable = false)
    private String key;

    // Храним как текст JSON, колонка jsonb. Просто кладём/читаем строку.
    @Column(name = "value_json", nullable = false, columnDefinition = "jsonb")
    private String valueJson;
}
