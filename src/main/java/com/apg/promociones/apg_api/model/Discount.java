package com.apg.promociones.apg_api.model;

import com.apg.promociones.apg_api.model.enums.DiscountStatus;
import com.apg.promociones.apg_api.model.enums.DiscountType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "discounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Discount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "discount_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "discount_view_amount", nullable = false)
    private Integer viewAmount;

    @Column(name = "discount_name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_status", nullable = false)
    private DiscountStatus status;

    @Column(name = "discount_applicable_products", nullable = false, columnDefinition = "TEXT")
    private String applicableProducts;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType type;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "discount_customer_takes_amount")
    private Integer customerTakesAmount;

    @Column(name = "discount_customer_pays_amount")
    private Integer customerPaysAmount;

    @Column(name = "discount_start_timestamp")
    private LocalDateTime startTimestamp;

    @Column(name = "discount_end_timestamp")
    private LocalDateTime endTimestamp;

    @Column(name = "discount_max_uses")
    private Integer maxUses;

    @Column(name = "discount_max_use_amount_per_user")
    private Integer maxUseAmountPerUser;

    @Column(name = "discount_image_url")
    private String imageUrl;

    @Column(name = "discount_additional_use_conditions", columnDefinition = "TEXT")
    private String additionalUseConditions;
}
