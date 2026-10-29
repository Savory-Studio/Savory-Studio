package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.PromocionRequest;
import com.apg.promociones.apg_api.dto.PromocionResponse;
import com.apg.promociones.apg_api.exception.BadRequestException;
import com.apg.promociones.apg_api.exception.ResourceNotFoundException;
import com.apg.promociones.apg_api.model.Discount;
import com.apg.promociones.apg_api.model.Restaurant;
import com.apg.promociones.apg_api.model.enums.DiscountStatus;
import com.apg.promociones.apg_api.model.enums.DiscountType;
import com.apg.promociones.apg_api.repository.DiscountRepository;
import com.apg.promociones.apg_api.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PromocionService {

    private final DiscountRepository discountRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional
    public PromocionResponse create(PromocionRequest request) {

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado."));

        DiscountType type;
        try {
            type = DiscountType.valueOf(request.getType());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "El tipo de descuento no es válido. Valores permitidos: PERCENTAGE, FIXED_VALUE, IXJ.");
        }

        // --- RN-06: amount validation per type ---
        if (type == DiscountType.PERCENTAGE || type == DiscountType.FIXED_VALUE) {
            if (request.getAmount() == null) {
                throw new BadRequestException(
                        "El campo amount es obligatorio para descuentos de tipo " + type + ".");
            }
            if (type == DiscountType.PERCENTAGE) {
                if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0
                        || request.getAmount().compareTo(new BigDecimal("100")) > 0) {
                    throw new BadRequestException(
                            "El porcentaje debe ser mayor a 0 y menor o igual a 100.");
                }
            } else {
                if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("El monto del descuento debe ser mayor a 0.");
                }
            }
        }

        // --- RN-08: IXJ field validation ---
        if (type == DiscountType.IXJ) {
            if (request.getCustomerTakesAmount() == null || request.getCustomerPaysAmount() == null) {
                throw new BadRequestException(
                        "Los campos customerTakesAmount y customerPaysAmount son obligatorios para tipo IXJ.");
            }
            if (request.getCustomerPaysAmount() <= 0) {
                throw new BadRequestException(
                        "La cantidad que paga el cliente debe ser mayor a 0.");
            }
            if (request.getCustomerTakesAmount() <= request.getCustomerPaysAmount()) {
                throw new BadRequestException(
                        "La cantidad que lleva el cliente debe ser mayor a la que paga.");
            }
        }

        // --- RN-09: at least one bound ---
        if (request.getEndTimestamp() == null && request.getMaxUses() == null) {
            throw new BadRequestException(
                    "Debe especificar fecha de vencimiento o límite de usos (o ambos).");
        }

        // --- Date range validation ---
        if (request.getStartTimestamp() != null && request.getEndTimestamp() != null
                && !request.getStartTimestamp().isBefore(request.getEndTimestamp())) {
            throw new BadRequestException(
                    "La fecha de inicio debe ser anterior a la fecha de vencimiento.");
        }

        // --- Positive limits ---
        if (request.getMaxUses() != null && request.getMaxUses() <= 0) {
            throw new BadRequestException("El límite de usos debe ser mayor a 0.");
        }
        if (request.getMaxUseAmountPerUser() != null && request.getMaxUseAmountPerUser() <= 0) {
            throw new BadRequestException("El límite de usos por usuario debe ser mayor a 0.");
        }

        // --- RN-07: status forced to Active ---
        Discount discount = Discount.builder()
                .restaurant(restaurant)
                .viewAmount(0)
                .name(request.getName())
                .status(DiscountStatus.Active)
                .applicableProducts(request.getApplicableProducts())
                .type(type)
                .amount(request.getAmount())
                .customerTakesAmount(request.getCustomerTakesAmount())
                .customerPaysAmount(request.getCustomerPaysAmount())
                .startTimestamp(request.getStartTimestamp())
                .endTimestamp(request.getEndTimestamp())
                .maxUses(request.getMaxUses())
                .maxUseAmountPerUser(request.getMaxUseAmountPerUser())
                .imageUrl(request.getImageUrl())
                .additionalUseConditions(request.getAdditionalUseConditions())
                .build();

        discountRepository.save(discount);

        return PromocionResponse.builder()
                .discountId(discount.getId())
                .restaurantId(restaurant.getId())
                .name(discount.getName())
                .type(type.name())
                .amount(discount.getAmount())
                .status(discount.getStatus().name())
                .startTimestamp(discount.getStartTimestamp())
                .endTimestamp(discount.getEndTimestamp())
                .maxUses(discount.getMaxUses())
                .message("Promoción creada exitosamente.")
                .build();
    }
}
