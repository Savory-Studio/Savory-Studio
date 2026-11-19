package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.exception.GlobalExceptionHandler;
import com.apg.promociones.apg_api.model.Coupon;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.model.enums.CouponStatus;
import com.apg.promociones.apg_api.repository.CouponRepository;
import com.apg.promociones.apg_api.security.SimulatedAuthFilter;
import com.apg.promociones.apg_api.security.SimulatedAuthProperties;
import com.apg.promociones.apg_api.service.AssociatePromotionService;
import com.apg.promociones.apg_api.service.CouponQrService;
import com.apg.promociones.apg_api.service.LoginServices.JwtService;
import com.apg.promociones.apg_api.util.QrCodeGenerator;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CouponQrControllerTest {

    private static final String JWT_SECRET = "MiClaveSuperSecretaYLoSuficientementeLargaParaHS25612345";
    private static final String COUPON_HASH = "a3f1b2c4-d5e6-7890-abcd-ef1234567890";
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Mock
    private AssociatePromotionService associatePromotionService;

    @Mock
    private CouponRepository couponRepository;

    private JwtService jwtService;
    private MockMvc mockMvc;
    private User ownerUser;
    private User otherUser;
    private Coupon ownerCoupon;
    private String ownerToken;
    private String otherUserToken;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(JWT_SECRET, 86_400_000L);
        CouponQrService couponQrService = new CouponQrService(couponRepository, new QrCodeGenerator());

        SimulatedAuthProperties authProperties = new SimulatedAuthProperties();
        authProperties.setEnabled(true);
        authProperties.setToken("fallback-qr-test-token");

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new CouponController(associatePromotionService, couponQrService))
                .addFilters(new SimulatedAuthFilter(authProperties, jwtService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        ownerUser = User.builder()
                .id(10)
                .firstName("Ana")
                .lastName("Lopez")
                .email("ana.lopez@example.com")
                .build();

        otherUser = User.builder()
                .id(99)
                .firstName("Carlos")
                .lastName("Gomez")
                .email("carlos.gomez@example.com")
                .build();

        ownerCoupon = Coupon.builder()
                .id(15)
                .user(ownerUser)
                .status(CouponStatus.UNUSED)
                .couponHash(COUPON_HASH)
                .build();

        ownerToken = jwtService.generateToken(ownerUser);
        otherUserToken = jwtService.generateToken(otherUser);
    }

    @Test
    void returns200PngWithExactCouponHashWhenRequestedByOwner_QR01() throws Exception {
        when(couponRepository.findById(15)).thenReturn(Optional.of(ownerCoupon));

        MvcResult result = mockMvc.perform(get("/api/coupons/15/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertValidPngAndExactHash(body, COUPON_HASH);
    }

    @Test
    void supportsCuponesAliasRouteAndDecodesExactCouponHash_QR02() throws Exception {
        when(couponRepository.findById(15)).thenReturn(Optional.of(ownerCoupon));

        MvcResult result = mockMvc.perform(get("/api/cupones/15/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertValidPngAndExactHash(body, COUPON_HASH);
    }

    @Test
    void returns401WhenBearerTokenIsMissingTamperedOrUnidentified_QR03() throws Exception {
        mockMvc.perform(get("/api/coupons/15/qr"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));

        String tampered = ownerToken.substring(0, ownerToken.length() - 4) + "XXXX";
        mockMvc.perform(get("/api/coupons/15/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/coupons/15/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer fallback-qr-test-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Could not identify the authenticated user."));

        verifyNoInteractions(couponRepository);
    }

    @Test
    void returns403WhenAuthenticatedUserRequestsAnotherUsersCoupon_QR04() throws Exception {
        when(couponRepository.findById(15)).thenReturn(Optional.of(ownerCoupon));

        mockMvc.perform(get("/api/coupons/15/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUserToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void returns404WhenCouponDoesNotExist_QR05() throws Exception {
        when(couponRepository.findById(999)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/coupons/999/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Coupon not found."));
    }

    @Test
    void returns400WhenCouponIdIsNonPositiveOrNonNumeric_QR06() throws Exception {
        mockMvc.perform(get("/api/coupons/0/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("The couponId must be a positive integer."));

        mockMvc.perform(get("/api/coupons/-1/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/coupons/abc/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("The value of couponId is invalid."));

        verifyNoInteractions(couponRepository);
    }

    private static void assertValidPngAndExactHash(byte[] body, String expectedHash) throws Exception {
        byte[] header = new byte[PNG_SIGNATURE.length];
        System.arraycopy(body, 0, header, 0, PNG_SIGNATURE.length);
        assertArrayEquals(PNG_SIGNATURE, header);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(body));
        assertNotNull(image);
        assertEquals(QrCodeGenerator.DEFAULT_SIZE, image.getWidth());
        assertEquals(QrCodeGenerator.DEFAULT_SIZE, image.getHeight());

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        String decoded = new QRCodeReader().decode(bitmap).getText();
        assertEquals(expectedHash, decoded);
    }
}
