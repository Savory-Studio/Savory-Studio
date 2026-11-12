package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.ActivePromotionResponse;
import com.apg.promociones.apg_api.dto.PromocionRequest;
import com.apg.promociones.apg_api.dto.PromocionResponse;
import com.apg.promociones.apg_api.exception.BadRequestException;
import com.apg.promociones.apg_api.exception.PromocionExceptions.PromocionDuplicadaException;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromocionService {

    private final DiscountRepository discountRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<ActivePromotionResponse> getActivePromotions() {
        return discountRepository.findActivePromotions().stream()
                .map(this::toActivePromotionResponse)
                .toList();
    }

    @Transactional
    public PromocionResponse create(PromocionRequest request) {

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado."));

        // Validación de duplicados (mismo restaurante + mismo nombre activo)
        if (discountRepository.existsActiveByRestaurantAndName(
                restaurant.getId(), request.getName().trim(), DiscountStatus.Active.name())) {
            throw new PromocionDuplicadaException(
                    "Ya existe una promoción activa con ese nombre para este restaurante.");
        }

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
                .startTimestamp(request.getStartTimestamp())
                .endTimestamp(request.getEndTimestamp())
                .maxUses(request.getMaxUses())
                .maxUseAmountPerUser(request.getMaxUseAmountPerUser())
                .imageUrl(request.getImageUrl())
                .additionalUseConditions(request.getAdditionalUseConditions())
                .build();

        if (type == DiscountType.IXJ) {
            discount.setCustomerTakesAmount(request.getCustomerTakesAmount());
            discount.setCustomerPaysAmount(request.getCustomerPaysAmount());
        } else {
            discount.setAmount(request.getAmount());
        }
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

    private ActivePromotionResponse toActivePromotionResponse(Discount discount) {
        Restaurant restaurant = discount.getRestaurant();
        return ActivePromotionResponse.builder()
                .discountId(discount.getId())
                .restaurantId(restaurant != null ? restaurant.getId() : null)
                .restaurantName(restaurant != null ? restaurant.getName() : null)
                .name(discount.getName())
                .applicableProducts(discount.getApplicableProducts())
                .type(discount.getType() != null ? discount.getType().name() : null)
                .amount(discount.getAmount())
                .customerTakesAmount(discount.getCustomerTakesAmount())
                .customerPaysAmount(discount.getCustomerPaysAmount())
                .status(discount.getStatus() != null ? discount.getStatus().name() : null)
                .startTimestamp(discount.getStartTimestamp())
                .endTimestamp(discount.getEndTimestamp())
                .maxUses(discount.getMaxUses())
                .maxUseAmountPerUser(discount.getMaxUseAmountPerUser())
                .imageUrl(discount.getImageUrl())
                .additionalUseConditions(discount.getAdditionalUseConditions())
                .build();
    }
}
