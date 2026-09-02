package com.konecta.stores_stock_service.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
public class Store {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private String ownerUserId;

    @Column(name = "trade_name", nullable = false)
    private String tradeName;

    @Column(name = "legal_name")
    private String legalName;

    private String nuit;

    private String email;

    private String phone;

    @Column(name = "address_line")
    private String addressLine;

    private String city;

    private String neighborhood;

    private Double latitude;

    private Double longitude;

    private String description;

    @Enumerated(EnumType.STRING)
    private StoreStatus status = StoreStatus.DRAFT;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "accepts_pickup")
    private boolean acceptsPickup = true;

    @Column(name = "accepts_delivery")
    private boolean acceptsDelivery = false;

    @Column(name = "default_preparation_minutes")
    private Integer defaultPreparationMinutes;

    @Column(name = "manually_closed")
    private boolean manuallyClosed = false;

    @Column(name = "manually_closed_reason")
    private String manuallyClosedReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean meetsActivationRequirements() {
        return tradeName != null && !tradeName.isBlank()
                && nuit != null && !nuit.isBlank()
                && addressLine != null && !addressLine.isBlank()
                && city != null && !city.isBlank()
                && neighborhood != null && !neighborhood.isBlank();
    }
}
