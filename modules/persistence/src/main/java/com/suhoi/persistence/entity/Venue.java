package com.suhoi.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "venues")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venue {

    @Id
    @Column(name = "venue", nullable = false)
    private String venue;

    @Column(name = "name", nullable = false)
    private String name;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
