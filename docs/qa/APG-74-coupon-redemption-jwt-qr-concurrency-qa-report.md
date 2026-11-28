# APG-74 — Reporte QA Ampliado: Reclamación JWT, QR PNG, Autorización de Canje, Concurrencia y Cierre por Vencimiento/Cupo

- **Ticket Jira:** `APG-74` (`[QA] Ampliar pruebas de canje para JWT, QR, autorización y concurrencia`)
- **Trazabilidad:** `APG-7` (`RF-02`), `APG-8` (`RF-03`), `APG-9` (`RF-04`) · `APG-30`, `APG-45` · `CUP-01` (`APG-50`) · `CUP-02` (`APG-62`) · `CUP-03` (`APG-63`) · `CUP-04` · `CUP-05` (`APG-65`) · `CUP-06` (`APG-66`) · `RN-01` a `RN-13` · `CA-01` a `CA-03`
- **Base evaluada:** `origin/develop` (`5e260141c4f9f0d6aa24dcbf641fe58225e6991a`)
- **Rama QA / correcciones:** `testing/APG-74-coupon-redemption-jwt-qr-auth-concurrency-qa`

---

## 1. Alcance y Cobertura Diferencial frente a `APG-30` y `APG-45`

Para cumplir con el ticket sin repetir los casos básicos ya cubiertos en `APG-30` (canje básico inicial y enum PostgreSQL) y `APG-45` (creación básica con `couponHash`), esta ampliación valida exclusivamente las capacidades y controles de seguridad de Sprint 3:

| Bloque Previo | Ya cubierto antes | Ampliación implementada en `APG-74` |
|---|---|---|
| `APG-45` (`POST /api/coupons`) | Reclamación simple por `userId` y generación de `couponHash` | Reclamación derivada estrictamente del JWT (`SecurityContext`), paridad `/api/coupons` y `/api/cupones`, anti-suplantación (`403` ante `userId` o `X-User-Id` ajenos), carrera concurrente de duplicados (`RN-13` + índice `uq_unused_coupon_per_user_promotion`), rechazo `400` a `promotionId <= 0`, y `409` para `RN-10`/`RN-11`/`RN-12` sin escribir filas en `coupon`. |
| `APG-30` (`POST /api/canjes`) | Canje básico secuencial y reglas unitarias iniciales | Canje conducido desde imagen QR PNG decodificada con ZXing (`300x300`), sensibilidad a mayúsculas/minúsculas de UUID (`RN-HASH-03`), prioridad de `couponHash` (`PH-05`), control de acceso por restaurante para dueño y `restaurant_staff` + revocación inmediata tras `DELETE /staff/{userId}` (`CUP-04`), carrera paralela dueño+staff (`RN-01`), agotamiento concurrente (`RN-05` + `CUP-06`), límite por usuario tras ciclos reclamo→canje→re-reclamo (`RN-04`), y transición `Active` → `Finished` por vencimiento preservando historial `coupon` (`RN-02`/`RN-03`). |

---

## 2. Artefactos Entregables

| Artefacto | Ruta | Descripción |
|---|---|---|
| Suite E2E PostgreSQL + Concurrencia (JUnit 5 / Testcontainers) | [`CouponRedemptionExtendedQaIntegrationTest.java`](../../src/test/java/com/apg/promociones/apg_api/controller/CouponRedemptionExtendedQaIntegrationTest.java) | 5 métodos integrales HTTP + ZXing + concurrencia real (`Executors` + `CountDownLatch`) + verificaciones SQL directas (`JdbcTemplate`) sobre `postgres:17.11-alpine`. |
| Pruebas unitarias y seguridad ampliadas | [`CanjeServiceTest.java`](../../src/test/java/com/apg/promociones/apg_api/service/CanjeServiceTest.java), [`SecurityAndAuthIntegrationTest.java`](../../src/test/java/com/apg/promociones/apg_api/security/SecurityAndAuthIntegrationTest.java) | Cobertura de `CUP-04` (staff autorizado vs tercero), rechazo `401` ante principal sin `userId`, y `couponHash` en blanco (`400`). |
| Colección Postman QA Extendida | [`Savory-Studio-Coupon-Redemption-Extended-QA.postman_collection.json`](../postman/Savory-Studio-Coupon-Redemption-Extended-QA.postman_collection.json) | 50 peticiones automatizadas en 6 carpetas con validaciones `pm.test(...)` encadenadas. |
| Contrato y código corregidos (`DEF-01`, `DEF-02`) | [`CanjeService.java`](../../src/main/java/com/apg/promociones/apg_api/service/CanjeService.java), [`CanjeController.java`](../../src/main/java/com/apg/promociones/apg_api/controller/CanjeController.java), [`AssociatePromotionRequest.java`](../../src/main/java/com/apg/promociones/apg_api/dto/AssociatePromotionRequest.java), [`contrato-canje.md`](../../contrato-canje.md) | Habilitación de personal autorizado `restaurant_staff` en `POST /api/canjes`, validación de identidad/hash en blanco y `@Positive` en `promotionId`. |

---

## 3. Matriz de Autorización por Rol y Endpoint

| Operación / Endpoint | Customer 1 (Dueño cupón) | Customer 2 (Otro cliente) | Owner A (Rest. A) | Staff A vinculado (Rest. A) | Staff A desvinculado | Owner B (Rest. B) | Sin JWT / Inválido |
|---|---|---|---|---|---|---|---|
| `POST /api/coupons` · `/api/cupones` (propio) | **201** *(o **409** RN-10..13)* | **201** *(propio)* | **201** | **201** | **201** | **201** | **401** |
| `POST /api/coupons` (`userId` distinto al JWT) | **403** *(anti-spoofing)* | **403** | **403** | **403** | **403** | **403** | **401** |
| `GET /api/cupones/usuario/{id}` | **200** *(solo propio)* / **403** *(ajeno)* | **403** *(sobre C1)* | **403** *(sobre C1)* | **403** *(sobre C1)* | **403** *(sobre C1)* | **403** *(sobre C1)* | **401** |
| `GET /api/coupons/{id}/qr` · `/api/cupones/{id}/qr` | **200** `image/png` | **403** | **403** | **403** | **403** | **403** | **401** |
| `POST /api/canjes` (cupón de Rest. A) | **403** *(auto-canje bloqueado)* | **403** | **201** | **201** *(CUP-04)* | **403** *(revocado)* | **403** | **401** |
| `POST /api/canjes` (cupón de Rest. B) | **403** | **403** | **403** | **403** *(aislamiento)* | **403** | **201** | **401** |

---

## 4. Matriz de Casos de Prueba Ampliados (`APG-74`)

| Caso | Regla / Subtarea | Escenario | HTTP Esperado | Evidencia Automatizada | Postman |
|---|---|---|---|---|---|
| `EX-01` | `CUP-02` / `AUTH-03` | Reclamación sin token, JWT adulterado, JWT expirado o fallback sin `X-User-Id` en `/api/coupons` y `/api/cupones` | `401` | `CouponRedemptionExtendedQaIntegrationTest#jwtAuthenticatedClaiming...` | `1.1`, `1.2` |
| `EX-02` | `CUP-02` | Suplantación enviando `userId` ajeno en body (`POST`) o `X-User-Id` ajeno (`GET`) | `403` | `CouponRedemptionExtendedQaIntegrationTest#jwtAuthenticatedClaiming...` | `1.3`, `1.4` |
| `EX-03` | `RN-10`..`RN-12` | Reclamar promoción `Suspended`, futura (`start > now`), expirada (`end < now`), inexistente (`404`) o `promotionId <= 0` (`400`) | `400` / `404` / `409` | `CouponRedemptionExtendedQaIntegrationTest#jwtAuthenticatedClaiming...` | `1.5`, `1.6` |
| `EX-04` | `CUP-01` / `RN-13` | 8 peticiones simultáneas del mismo usuario reclamando misma promoción (`CountDownLatch`) | 1× `201`, 7× `409` | `CouponRedemptionExtendedQaIntegrationTest#jwtAuthenticatedClaiming...` | `1.7`, `1.8` |
| `EX-05` | `CUP-03` | Obtener QR PNG (`image/png`, `Cache-Control: no-store`, `300x300`) en `/api/coupons/{id}/qr` y `/api/cupones/{id}/qr`; decodificación ZXing == `coupon_hash` sin PII | `200` | `CouponRedemptionExtendedQaIntegrationTest#qrCodePngGeneration...`, `CouponQrControllerTest` | `2.1`, `2.2` |
| `EX-06` | `CUP-03` | Seguridad QR: sin token (`401`), otro cliente o dueño del restaurante (`403`), inexistente (`404`), ID no positivo/alfanumérico (`400`) | `400`/`401`/`403`/`404` | `CouponRedemptionExtendedQaIntegrationTest#qrCodePngGeneration...`, `CouponQrControllerTest` | `2.3`..`2.7` |
| `EX-07` | `RN-HASH-03` / `PH-05` | `couponHash` en mayúsculas rechazado (`404`); prioridad de `couponHash` válido sobre `couponId: 999999` (`201`) sin mutar `coupon_hash` (`RN-HASH-01`) | `404` / `201` | `CouponRedemptionExtendedQaIntegrationTest#qrCodePngGeneration...` | `3.5`, `3.6` |
| `EX-08` | `CUP-04` | Canje autorizado por `Staff A` vinculado a Restaurante A (`201`), bloqueo a cliente/OwnerB/Outsider/StaffA-en-RestB (`403`) y revocación inmediata (`403`) tras `DELETE .../staff/{id}` (`204`) | `201` / `204` / `403` | `CouponRedemptionExtendedQaIntegrationTest#restaurantStaffRedemption...`, `CanjeServiceTest`, `SecurityAndAuthIntegrationTest` | `3.2`..`3.10` |
| `EX-09` | `CUP-05` / `RN-01` | Carrera mixta concurrente Owner A (4 hilos) + Staff A (4 hilos) sobre el mismo `couponHash` | 1× `201`, 7× `409` | `CouponRedemptionExtendedQaIntegrationTest#concurrentSameCoupon...` | `3.6`, `3.7` |
| `EX-10` | `CUP-05` / `CUP-06` / `RN-05` | 6 canjes simultáneos sobre promoción con `maxUses = 2`: exactamente 2× `201`, 4× `409`, `discount_status` pasa a `Finished` y bloquea nuevas reclamaciones (`RN-10`) | 2× `201`, 4× `409` | `CouponRedemptionExtendedQaIntegrationTest#concurrentSameCoupon...`, `CanjeConcurrencyIntegrationTest` | `5.1`..`5.4` |
| `EX-11` | `CUP-01` / `RN-04` | Ciclo reclamar → canjear (`1/2`) → re-reclamar → canjear (`2/2`) → re-reclamar (`3ro UNUSED` permitido) → 3er canje bloqueado por `RN-04` (`409`); historial conserva los 3 cupones | `201` / `409` / `200` | `CouponRedemptionExtendedQaIntegrationTest#concurrentSameCoupon...` | `4.1`..`4.4` |
| `EX-12` | `CUP-06` / `RN-02` / `RN-03` / `RN-12` | Promoción vencida: antes del cierre falla reclamo (`409` RN-12) y canje (`409` RN-03); tras cierre automático a `Finished`, `Suspended` e historial `coupon` permanecen intactos y rechaza con `RN-10`/`RN-02` (`409`) | `409` / `0` mutaciones | `CouponRedemptionExtendedQaIntegrationTest#expirationTransitions...`, `PromotionLifecycleIntegrationTest` | `5.3`..`5.5` |

---

## 5. Evidencia SQL y Garantías de Concurrencia / No-Modificación

Consultas verificadas directamente sobre `postgres:17.11-alpine` mediante `JdbcTemplate`:

1. **Unicidad concurrente en reclamación (`RN-13` / `uq_unused_coupon_per_user_promotion`):**
   - `SELECT COUNT(*) FROM coupon WHERE user_id = ? AND discount_id = ?` → `1` fila única tras 8 `POST /api/cupones` simultáneos; `coupon_hash` persistido coincide bit a bit con el devuelto en el QR PNG y en el `201`.
2. **Serialización de canjes concurrentes (`CUP-05` — `SELECT ... FOR UPDATE` en `coupon` y `discounts`):**
   - Carrera mixta dueño+staff sobre un mismo `couponHash`: `1` actualización a `USED`, `coupon_redemption_timestamp` en PostgreSQL igual al timestamp devuelto al ganador (< 1 ms), y `7` rechazos `409` sin pérdida de actualización.
   - Carrera de cupo global (`maxUses = 2` con 6 cupones distintos): `SELECT COUNT(*) FROM coupon WHERE discount_id = ? AND coupon_status = 'USED'` = `2`; `SELECT COUNT(*) FROM coupon WHERE discount_id = ? AND coupon_status = 'UNUSED' AND coupon_redemption_timestamp IS NULL` = `4`; `discounts.discount_status` = `'Finished'`.
3. **Inmutabilidad ante rechazos (`400`, `401`, `403`, `404`, `409`) y cierre por vencimiento (`CUP-06`):**
   - Todos los rechazos (`403` de staff/dueño ajeno/staff desvinculado/auto-canje, `404` de hash en mayúsculas, `409` de `RN-01`..`RN-13`) dejaron `coupon_status = 'UNUSED'` y `coupon_redemption_timestamp IS NULL`, y las promociones `Suspended` y el historial `USED` conservaron sus timestamps originales tras ejecutar `closeExpiredAndExhaustedPromotions()`.

---

## 6. Registro de Defectos (`Registro de Defectos`)

| ID | Severidad | Componente | Defecto detectado en `origin/develop` (`5e26014`) | Corrección aplicada en este PR | Estado |
|---|---|---|---|---|---|
| `DEF-01` | **Alta** | [`CanjeService.java`](../../src/main/java/com/apg/promociones/apg_api/service/CanjeService.java), [`CanjeController.java`](../../src/main/java/com/apg/promociones/apg_api/controller/CanjeController.java), [`contrato-canje.md`](../../contrato-canje.md) | `POST /api/canjes` autorizaba únicamente a `restaurant.getOwner()` (faltando `CUP-04` para `restaurant_staff`), no fallaba cerrado con `401` ante un contexto autenticado sin `userId` resuelto, y trataba un `couponHash` solo con espacios como búsqueda inexistente (`404`) en vez de `400`. | Se inyectó `RestaurantStaffRepository` en `CanjeService` habilitando canjes para el propietario o personal vinculado en `restaurant_staff` (con revocación inmediata al desvincular), validación explícita de `authUserId` (`401`), recorte/validación de `couponHash` en blanco (`400`), y actualización de OpenAPI/`contrato-canje.md`. | **Corregido y verificado** |
| `DEF-02` | **Baja / Media** | [`AssociatePromotionRequest.java`](../../src/main/java/com/apg/promociones/apg_api/dto/AssociatePromotionRequest.java) | `promotionId` solo tenía `@NotNull` sin `@Positive`: valores `0` o negativos llegaban al servicio y respondían `404 Not Found` en lugar de `400 Bad Request` como exige el contrato de parámetros positivos. | Se agregó `@Positive(message = "The promotionId must be a positive integer.")` en `AssociatePromotionRequest.promotionId`. | **Corregido y verificado** |

---

## 7. Resultados Observados y Reproducción

* **Comando ejecutado:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=!ApgApiApplicationTests" test`
* **Entorno:** JDK `25.0.2` (target Java `21`) · Maven `3.10.0` · Docker Desktop `29.9.3` · PostgreSQL `17.11-alpine` (Testcontainers `2.0.5`)
* **Resultado observado:** **`260` pruebas ejecutadas, `0` fallos, `0` errores, `0` omitidas (`BUILD SUCCESS`)**, incluyendo:
  - `CouponRedemptionExtendedQaIntegrationTest`: `5/5` (PostgreSQL real)
  - `CanjeConcurrencyIntegrationTest`: `5/5` (PostgreSQL real)
  - `PromotionLifecycleIntegrationTest`: `3/3` (PostgreSQL real)
  - `CanjeServiceTest`: `19/19`
  - `AssociatePromotionServiceTest`: `12/12`
  - `CouponControllerTest`: `13/13`
  - `CouponQrControllerTest`: `6/6`
  - `SecurityAndAuthIntegrationTest`: `22/22`
* **Colección Postman validada:** `ConvertFrom-Json` en UTF-8 (`6` carpetas, `50` peticiones).
* **Nota de alcance:** `ApgApiApplicationTests` se excluyó explícitamente porque intenta conectar con las variables externas de Supabase (`.env`) cuando no se configuran credenciales locales; los flujos de la colección Postman se ejercitaron automáticamente vía `@SpringBootTest` + `MockMvc` + Testcontainers y quedan listos para correr en Postman/Newman contra el servidor en ejecución.
