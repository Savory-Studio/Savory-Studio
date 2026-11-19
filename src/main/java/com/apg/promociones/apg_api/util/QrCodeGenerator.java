package com.apg.promociones.apg_api.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Generates in-memory PNG QR code images from a plain text payload.
 */
@Component
public class QrCodeGenerator {

    public static final int DEFAULT_SIZE = 300;
    private static final String IMAGE_FORMAT = "PNG";

    /**
     * Encodes the given non-blank payload as a 300x300 PNG QR code image.
     */
    public byte[] generatePng(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("QR payload must not be blank.");
        }

        Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, 2
        );

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            BitMatrix bitMatrix = new QRCodeWriter().encode(
                    payload,
                    BarcodeFormat.QR_CODE,
                    DEFAULT_SIZE,
                    DEFAULT_SIZE,
                    hints
            );
            MatrixToImageWriter.writeToStream(bitMatrix, IMAGE_FORMAT, outputStream);
            return outputStream.toByteArray();
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Failed to generate QR code image.", ex);
        }
    }
}
