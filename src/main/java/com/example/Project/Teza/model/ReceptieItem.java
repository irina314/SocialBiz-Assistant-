package com.example.Project.Teza.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "receptie_items")
public class ReceptieItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receptie_id", nullable = false)
    private Receptie receptie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produs_id", nullable = false)
    private Produs produs;

    private Integer cantitate;
    private Double pretAchizitie;   // pretul de cumparare per unitate

    public double getTotal() {
        return cantitate * pretAchizitie;
    }
}