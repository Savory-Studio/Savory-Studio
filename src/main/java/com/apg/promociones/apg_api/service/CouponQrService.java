package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.exception.BadRequestException;
import com.apg.promociones.apg_api.exception.ResourceNotFoundException;
import com.apg.promociones.apg_api.model.Coupon;
import com.apg.promociones.apg_api.repository.CouponRepository;
import com.apg.promociones.apg_api.util.QrCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates QR code PNG images on the fly for coupons owned by the authenticated customer.
 */
@Service
@RequiredArgsConstructor
public class CouponQrService {

    private final CouponRepository couponRepository;
    private final QrCodeGenerator qrCodeGenerator;

    /**
     * Validates ownership of the coupon and encodes exclusively its persisted {@code coupon_hash}
     * into a scannable PNG QR image without exposing sequential IDs or personal data.
     */
    @Transactional(readOnly = true)
    public byte[] generateCouponQrPng(Integer couponId, Integer authenticatedUserId) {
        if (couponId == null || couponId <= 0) {
            throw new BadRequestException("The couponId must be a positive integer.");
        }

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found."));

        Integer ownerId = coupon.getUser() != null ? coupon.getUser().getId() : null;
        if (ownerId == null || !ownerId.equals(authenticatedUserId)) {
            throw new AccessDeniedException(
                    "Acceso denegado: no tiene permiso para consultar el QR de este cupón.");
        }

        String couponHash = coupon.getCouponHash();
        if (couponHash == null || couponHash.isBlank()) {
            throw new IllegalStateException("Coupon hash is not available.");
        }

        return qrCodeGenerator.generatePng(couponHash);
    }
}
