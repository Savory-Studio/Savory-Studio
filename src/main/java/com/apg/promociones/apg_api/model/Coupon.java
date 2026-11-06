package com.apg.promociones.apg_api.model;

import com.apg.promociones.apg_api.model.enums.CouponStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_id", nullable = false)
    private Discount discount;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.ColumnTransformer(write = "?::coupon_status_enum")
    @Column(name = "coupon_status", nullable = false)
    private CouponStatus status;

    @Column(name = "coupon_hash", nullable = false, unique = true, length = 36)
    private String couponHash;

    @Column(name = "coupon_claim_timestamp", updatable = false)
    private LocalDateTime claimTimestamp;

    @Column(name = "coupon_redemption_timestamp")
    private LocalDateTime redemptionTimestamp;
}
