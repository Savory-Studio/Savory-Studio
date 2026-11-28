# APG-73 — Reporte QA: CRUD de Restaurantes, Autorización de Propietarios/Personal, Aislamiento y REST-05

- **Ticket Jira:** `APG-73` (`[QA] Probar CRUD básico de restaurantes y autorización de propietarios y personal`)
- **Trazabilidad:** `DAT-02` (`APG-49`) · `AUTH-03` (`APG-53`) · `REST-01` (`APG-57`) · `REST-02` (`APG-58`) · `REST-03` (`APG-59`) · `REST-04` (`APG-60`) · `REST-05`
- **Base evaluada:** `origin/develop` (`18326969086d8f15d7ca87477a1c9b55e260ff56`)
- **Rama QA / correcciones:** `testing/APG-73-restaurant-crud-and-staff-auth-tests`

---

## 1. Alcance y Artefactos Entregables

| Artefacto | Ruta | Propósito |
|---|---|---|
| Suite QA PostgreSQL (E2E HTTP + SQL) | [`src/test/java/com/apg/promociones/apg_api/controller/RestaurantCrudAndAuthorizationQaIntegrationTest.java`](../../src/test/java/com/apg/promociones/apg_api/controller/RestaurantCrudAndAuthorizationQaIntegrationTest.java) | Verificación integral con `@SpringBootTest`, `MockMvc`, filtro JWT real (`SimulatedAuthFilter`), `postgres:17.11-alpine` (Testcontainers) y aserciones SQL con `JdbcTemplate`. |
| Suite controlador de personal | [`src/test/java/com/apg/promociones/apg_api/controller/RestaurantStaffControllerTest.java`](../../src/test/java/com/apg/promociones/apg_api/controller/RestaurantStaffControllerTest.java) | Pruebas MockMvc de `POST`/`GET`/`DELETE /api/restaurants/{restaurantId}/staff` (`201`, `200`, `204`, `400`, `401`, `403`, `404`, `409`) y payload `ErrorResponse`. |
| Pruebas unitarias y seguridad ampliadas | [`PromocionServiceTest.java`](../../src/test/java/com/apg/promociones/apg_api/service/PromocionServiceTest.java), [`RestaurantServiceTest.java`](../../src/test/java/com/apg/promociones/apg_api/service/RestaurantServiceTest.java), [`SecurityAndAuthIntegrationTest.java`](../../src/test/java/com/apg/promociones/apg_api/security/SecurityAndAuthIntegrationTest.java) | Cobertura unitaria de `REST-05` (dueño, empleado vinculado, tercero no autorizado, principal sin ID) y consultas `REST-02` (`getMyRestaurants`, `getById`). |
| Colección Postman QA | [`docs/postman/Savory-Studio-Restaurant-CRUD-Auth-QA.postman_collection.json`](../postman/Savory-Studio-Restaurant-CRUD-Auth-QA.postman_collection.json) | 48 peticiones secuenciales automatizadas con scripts `pm.test(...)` para ejecutar flujo positivo, negativo, aislamiento y `REST-05` sobre servidor en ejecución. |
| Contratos actualizados | [`contrato-promociones.md`](../../contrato-promociones.md), [`contrato-restaurante_staff.md`](../../contrato-restaurante_staff.md) | Documentación alineada de permisos `REST-05`, códigos `403` y validaciones `400` en gestión de personal. |

---

## 2. Matriz de Autorización por Rol y Endpoint

| Operación / Endpoint | Owner A (Rest. A) | Staff A vinculado (Rest. A) | Staff A desvinculado | Owner B (Rest. B) | Outsider | Sin JWT / Inválido |
|---|---|---|---|---|---|---|
| `POST /api/restaurantes` | **201** | **201** *(crea propio Rest.)* | **201** *(crea propio Rest.)* | **201** *(crea Rest. B)* | **201** *(crea propio Rest.)* | **401** |
| `GET /api/restaurantes/mios` | **200** *(solo suyos)* | **200** `[]` *(si no es dueño)* | **200** `[]` | **200** *(solo Rest. B)* | **200** `[]` | **401** |
| `GET /api/restaurantes/{restaurantA}` | **200** | **403** *(solo dueño)* | **403** | **403** | **403** | **401** |
| `PATCH /api/restaurantes/{restaurantA}` | **200** | **403** *(solo dueño)* | **403** | **403** | **403** | **401** |
| `POST /api/restaurants/{restaurantA}/staff` | **201** | **403** | **403** | **403** | **403** | **401** |
| `GET /api/restaurants/{restaurantA}/staff` | **200** | **403** | **403** | **403** | **403** | **401** |
| `DELETE /api/restaurants/{restaurantA}/staff/{userId}` | **204** | **403** | **403** | **403** | **403** | **401** |
| `POST /api/promociones` (para Rest. A — `REST-05`) | **201** | **201** | **403** *(revocado)* | **403** | **403** | **401** |
| `POST /api/promociones` (para Rest. B — aislamiento) | **403** | **403** | **403** | **201** | **403** | **401** |

---

## 3. Matriz de Casos de Prueba (Positivos y Negativos)

| ID | Módulo / Regla | Escenario | HTTP Esperado | Automatización Java | Petición Postman |
|---|---|---|---|---|---|
| `REST-01-POS-01` | `REST-01` | Creación válida con `name`, `description` y `fullAddress` + recorte de espacios | `201 Created` | `RestaurantCrudAndAuthorizationQaIntegrationTest#fullRestaurantCrudLifecyclePersistsExpectedRowsAndEnforcesValidation` | `1.1`, `1.2` |
| `REST-01-POS-02` | `REST-01` / `AUTH-03` | `ownerId` en body o header `X-User-Id` suplantado junto al JWT se ignora; prevalece el `userId` del JWT | `201 Created` | `RestaurantCrudAndAuthorizationQaIntegrationTest#fullRestaurantCrudLifecycle...` | `1.1` |
| `REST-01-POS-03` | `REST-01` | Creación válida sin `description` (`null` persistido) | `201 Created` | `RestaurantCrudAndAuthorizationQaIntegrationTest#fullRestaurantCrudLifecycle...` | — |
| `REST-01-NEG-01` | `REST-01` | Body `{}` / JSON malformado / `name` o `fullAddress` faltantes, en blanco o `> 255` caracteres | `400 Bad Request` | `RestaurantCrudAndAuthorizationQaIntegrationTest#fullRestaurantCrudLifecycle...`, `RestaurantControllerTest` | `1.3` |
| `REST-01-NEG-02` | `AUTH-03` | Petición sin token, JWT adulterado, JWT expirado o fallback sin `X-User-Id` | `401 Unauthorized` | `RestaurantCrudAndAuthorizationQaIntegrationTest#fullRestaurantCrudLifecycle...` | `1.4`, `1.5` |
| `REST-02-POS-01` | `REST-02` | `GET /api/restaurantes/mios` devuelve solo restaurantes del dueño autenticados ordenados por fecha descendente; lista `[]` sin restaurantes | `200 OK` | `RestaurantCrudAndAuthorizationQaIntegrationTest#fullRestaurantCrudLifecycle...`, `RestaurantServiceTest` | `2.1`, `2.2` |
| `REST-02-POS-02` | `REST-02` | `GET /api/restaurantes/{id}` por dueño legítimo | `200 OK` | `RestaurantCrudAndAuthorizationQaIntegrationTest#fullRestaurantCrudLifecycle...` | `2.3` |
| `REST-02-NEG-01` | `REST-02` | `restaurantId <= 0` (`0`, `-1`) o no numérico (`abc`) en consulta por ID | `400 Bad Request` | `RestaurantCrudAndAuthorizationQaIntegrationTest#fullRestaurantCrudLifecycle...`, `RestaurantServiceTest` | `2.4` |
| `REST-02-NEG-02` | `AUTH-03` | Consulta sin Bearer token en `/mios` y `/{id}` | `401 Unauthorized` | `RestaurantCrudAndAuthorizationQaIntegrationTest#fullRestaurantCrudLifecycle...` | `2.5` |
| `REST-02-NEG-03` | `REST-02` | Consulta de detalle de restaurante ajeno (por OwnerB, StaffA o Outsider) | `403 Forbidden` | `RestaurantCrudAndAuthorizationQaIntegrationTest#crossRestaurantIsolation...` | `5.1`, `5.6` |
| `REST-02-NEG-04` | `REST-02` | Consulta de restaurante inexistente (`999999`) | `404 Not Found` | `RestaurantCrudAndAuthorizationQaIntegrationTest#fullRestaurantCrudLifecycle...` | `2.6` |
| `REST-03-POS-01` | `REST-03` | Actualización completa y parcial de `name`, `description` (`null` limpia), `fullAddress` preservando `ownerId` y `creationDate` | `200 OK` | `RestaurantCrudAndAuthorizationQaIntegrationTest#fullRestaurantCrudLifecycle...`, `RestaurantUpdateControllerTest` | `3.1` |
| `REST-03-NEG-01` | `REST-03` | Intento de reasignar `ownerId`/`restaurantOwnerId`, body `{}`, campos en blanco, `> 255` caracteres o `restaurantId <= 0` | `400 Bad Request` | `RestaurantCrudAndAuthorizationQaIntegrationTest#fullRestaurantCrudLifecycle...`, `RestaurantUpdateControllerTest` | `3.2`, `3.3` |
| `REST-03-NEG-02` | `AUTH-03` | `PATCH` sin token, token inválido o sin usuario | `401 Unauthorized` | `RestaurantCrudAndAuthorizationQaIntegrationTest#fullRestaurantCrudLifecycle...` | `3.4` |
| `REST-03-NEG-03` | `REST-03` | `PATCH` por OwnerB, StaffA vinculado o Outsider | `403 Forbidden` | `RestaurantCrudAndAuthorizationQaIntegrationTest#crossRestaurantIsolation...` | `5.2`, `5.7`, `5.9` |
| `REST-03-NEG-04` | `REST-03` | `PATCH` sobre restaurante inexistente (`999999`) | `404 Not Found` | `RestaurantCrudAndAuthorizationQaIntegrationTest#fullRestaurantCrudLifecycle...` | `3.5` |
| `REST-04-POS-01` | `REST-04` / `DAT-02` | OwnerA vincula usuario existente por correo (`POST .../staff`) | `201 Created` | `RestaurantCrudAndAuthorizationQaIntegrationTest#staffLifecycleAndDatabaseConstraints...` | `4.1` |
| `REST-04-POS-02` | `REST-04` | OwnerA lista empleados de su restaurante (`GET .../staff`) | `200 OK` | `RestaurantCrudAndAuthorizationQaIntegrationTest#staffLifecycleAndDatabaseConstraints...` | `4.7` |
| `REST-04-POS-03` | `REST-04` | OwnerA desvincula empleado (`DELETE .../staff/{userId}`); la cuenta en `users` permanece intacta | `204 No Content` | `RestaurantCrudAndAuthorizationQaIntegrationTest#staffLifecycleAndDatabaseConstraints...` | `6.6` |
| `REST-04-NEG-01` | `REST-04` | Correo inválido/en blanco, auto-asignación del dueño, `restaurantId <= 0`, `userId <= 0` o no numérico | `400 Bad Request` | `RestaurantCrudAndAuthorizationQaIntegrationTest#staffLifecycleAndDatabaseConstraints...`, `RestaurantStaffControllerTest` | `4.3`, `4.4` |
| `REST-04-NEG-02` | `AUTH-03` | `POST` / `GET` / `DELETE` de personal sin token o token inválido | `401 Unauthorized` | `RestaurantCrudAndAuthorizationQaIntegrationTest#staffLifecycleAndDatabaseConstraints...` | `4.6` |
| `REST-04-NEG-03` | `REST-04` | OwnerB, StaffA o Outsider intentando vincular, listar o desvincular staff en Restaurante A | `403 Forbidden` | `RestaurantCrudAndAuthorizationQaIntegrationTest#crossRestaurantIsolation...` | `5.3`, `5.4`, `5.5`, `5.8` |
| `REST-04-NEG-04` | `REST-04` | Restaurante inexistente, correo no registrado o `DELETE` sobre usuario ya desvinculado | `404 Not Found` | `RestaurantCrudAndAuthorizationQaIntegrationTest#staffLifecycleAndDatabaseConstraints...` | `4.5`, `6.7` |
| `REST-04-NEG-05` | `REST-04` / `DAT-02` | Re-vincular al mismo empleado en el mismo restaurante (`uq_staff_user_restaurant`) | `409 Conflict` | `RestaurantCrudAndAuthorizationQaIntegrationTest#staffLifecycleAndDatabaseConstraints...` | `4.2` |
| `REST-05-POS-01` | `REST-05` | Dueño del restaurante publica promoción (`POST /api/promociones`) | `201 Created` | `RestaurantCrudAndAuthorizationQaIntegrationTest#promotionPublishingHonorsRest05...` | `6.1` |
| `REST-05-POS-02` | `REST-05` | Empleado vinculado en `restaurant_staff` publica promoción para su restaurante asignado | `201 Created` | `RestaurantCrudAndAuthorizationQaIntegrationTest#promotionPublishingHonorsRest05...`, `SecurityAndAuthIntegrationTest` | `6.2` |
| `REST-05-NEG-01` | `REST-05` | Empleado de Restaurante A intenta publicar en Restaurante B; empleado no vinculado o Outsider intentan publicar; empleado desvinculado reintenta publicar inmediatamente tras `DELETE` | `403 Forbidden` | `RestaurantCrudAndAuthorizationQaIntegrationTest#promotionPublishingHonorsRest05...` | `6.3`, `6.4`, `6.8` |
| `REST-05-NEG-02` | `REST-05` | Promoción sin `endTimestamp`/`maxUses` (`400`), sin autenticación válida (`401`), o sobre restaurante inexistente (`404`) | `400` / `401` / `404` | `RestaurantCrudAndAuthorizationQaIntegrationTest#promotionPublishingHonorsRest05...` | `6.5` |

---

## 4. Comprobación SQL de Persistencia e Integridad (`DAT-02` · `REST-01`..`REST-05`)

Todas las validaciones SQL fueron ejecutadas contra PostgreSQL `17.11-alpine` inicializado con `src/test/resources/sql/apg42-schema.sql` mediante `JdbcTemplate` dentro de `RestaurantCrudAndAuthorizationQaIntegrationTest`:

| Tabla / Restricción | Consulta de Verificación | Resultado Observado |
|---|---|---|
| `restaurants` (`REST-01`, `REST-03`) | `SELECT restaurant_name, restaurant_owner_id, restaurant_description, restaurant_full_address, restaurant_creation_date FROM restaurants WHERE restaurant_id = ?` | Persiste campos normalizados (`trim`); `restaurant_owner_id` coincide con el JWT pese a suplantación en payload/headers; `PATCH` actualiza solo campos editables y conserva `restaurant_owner_id` y `restaurant_creation_date`; los rechazos `400`/`403` dejan `COUNT(*)` y valores intactos. |
| `restaurant_staff` (`REST-04`, `DAT-02`) | `SELECT COUNT(*) FROM restaurant_staff WHERE restaurant_id = ? AND user_id = ?` | `1` tras `POST` exitoso; sigue en `1` tras intento duplicado (`409`); pasa a `0` tras `DELETE` (`204`) conservando `COUNT(*) = 1` en `users`. |
| `uq_staff_user_restaurant` (`DAT-02`) | `INSERT INTO restaurant_staff (user_id, restaurant_id) VALUES (?, ?)` duplicado en SQL | PostgreSQL rechaza inserción duplicada (`DataIntegrityViolationException`), garantizando unicidad a nivel de motor de base de datos. |
| `ON DELETE RESTRICT` (`DAT-02`) | `DELETE FROM users WHERE user_id = ?` y `DELETE FROM restaurants WHERE restaurant_id = ?` con asignación activa | PostgreSQL rechaza ambos borrados (`DataIntegrityViolationException`), protegiendo la integridad referencial. |
| `discounts` (`REST-05`) | `SELECT discount_restaurant_id, discount_status::text FROM discounts WHERE discount_id = ?` y `SELECT COUNT(*) FROM discounts WHERE discount_restaurant_id = ?` | Las promociones creadas por dueño o empleado vinculado persisten en `Active` asociadas al `restaurant_id` correspondiente; los intentos `403` (otro restaurante, tercero o empleado recién desvinculado) no insertan filas (`0` cambios en BD). |

---

## 5. Evidencia de Aislamiento Entre Restaurantes

El método `crossRestaurantIsolationBlocksForeignOwnersStaffAndOutsidersWithZeroSqlSideEffects` y el escenario `REST-05` demostraron con PostgreSQL real:
1. **Owner B** (propietario legítimo del Restaurante B) no puede leer detalle (`GET /api/restaurantes/{A}`), actualizar (`PATCH /api/restaurantes/{A}`), vincular/listar/desvincular empleados (`/api/restaurants/{A}/staff*`) ni publicar promociones en el Restaurante A: todas responden `403 Forbidden`.
2. **Staff A** (empleado vinculado al Restaurante A) puede publicar promociones únicamente en el Restaurante A (`REST-05` -> `201`), pero recibe `403 Forbidden` en detalle administrativo (`GET /api/restaurantes/{A}`), actualización (`PATCH /api/restaurantes/{A}`), gestión de personal (`/api/restaurants/{A}/staff*`), y sobre cualquier recurso o publicación de promoción del Restaurante B (`0` filas afectadas en BD).
3. Tras `DELETE /api/restaurants/{A}/staff/{staffAId}`, **Staff A** pierde inmediatamente la capacidad de publicar promociones en el Restaurante A (`403 Forbidden`).

---

## 6. Registro de Defectos (`Registro de Defectos`)

| ID | Severidad | Componente | Descripción del Defecto Detectado en `develop` | Corrección Aplicada en este PR | Estado |
|---|---|---|---|---|---|
| `DEF-01` | **Alta** | `PromocionService`, `PromocionController`, `contrato-promociones.md` | `POST /api/promociones` verificaba exclusivamente `restaurant.getOwner().getId().equals(authUserId)` y rechazaba con `403` a empleados vinculados en `restaurant_staff`, incumpliendo `REST-05`. Además, si existía un contexto autenticado sin `userId` resuelto, omitía el control de identidad. | Se inyectó `RestaurantStaffRepository` en `PromocionService` (manteniendo constructor de compatibilidad), permitiendo publicar al dueño o empleado registrado en `restaurant_staff`, exigiendo `userId` cuando hay `Authentication` activa (`401`), y actualizando Swagger y contrato. | **Corregido y verificado** |
| `DEF-02` | **Media** | `RestaurantStaffService`, `contrato-restaurante_staff.md` | Faltaba validar `restaurantId > 0` y `userId > 0` (`400`), recortar espacios en `email`, bloquear auto-vinculación del dueño como empleado (`restaurant_staff` es solo para personal adicional según `DAT-02`), y capturar condición de carrera duplicada con `saveAndFlush` + `DataIntegrityViolationException` (`409`). | Se agregaron validaciones `> 0` (`400`), normalización de correo, rechazo `400` a auto-asignación del propietario y manejo atómico `saveAndFlush`/`409`. | **Corregido y verificado** |
| `DEF-03` | **Media** | `GlobalExceptionHandler` | `EmpleadoYaVinculadoException` (`409`), `PermisoDenegadoException` (`403`) y `RecursoNoEncontradoException` (`404`) devolvían `Map.of("error", ...)` en vez del esquema estándar `ErrorResponse` (`status`, `message`, `timestamp`). | Se unificaron los tres manejadores para retornar `ErrorResponse` con su `@ResponseStatus` correspondiente. | **Corregido y verificado** |
| `DEF-04` | **Media** | `CreateRestaurantRequest`, `RestaurantService`, `OpenApiConfig` | `CreateRestaurantRequest` no tenía `@Size(max = 255)` en `name`/`fullAddress` (riesgo de fallo de longitud PostgreSQL `500`), `RestaurantService.create` no recortaba espacios ni normalizaba descripción en blanco, `getById` no validaba `restaurantId > 0`, y `OpenApiConfig` omitía `/api/restaurants/**`. | Se añadieron `@Size(max = 255)`, normalización en `create`, validación `restaurantId > 0` en `getById`, y ruta `/api/restaurants/**` en OpenAPI. | **Corregido y verificado** |
| `DEF-05` | **Media (Build)** | `ViewCountAtomicityTest.java` | Archivo fusionado previamente en `develop` (PR #41) declaraba `PostgreSQLContainer<?>` / `new PostgreSQLContainer<>(...)`, incompatible con Testcontainers `2.0.5`, provocando fallo de `testCompile` en `mvnw test`. | Se ajustó a `PostgreSQLContainer` no genérico alineado con el resto de pruebas Testcontainers del repositorio; todas las 5 pruebas de atomicidad compilan y pasan en PostgreSQL. | **Corregido y verificado** |
| `OBS-01` | **Baja (Observación)** | `RestaurantController` vs `RestaurantStaffController` | `RestaurantController` expone `/api/restaurantes/**` mientras `RestaurantStaffController` expone `/api/restaurants/{restaurantId}/staff`. | Se preservan ambos prefijos publicados por contratos previos para no romper clientes existentes; documentado para consumidores de API. | **Documentado (sin cambio de ruta)** |

---

## 7. Resumen de Ejecución y Comandos de Reproducción

### Entorno Verificado
- **Java / Build:** JDK `25.0.2` (Amazon Corretto, target `release 21`) · Wrapper Maven `3.10.0` (`.\mvnw.cmd`)
- **Base de datos de integración:** Docker Desktop `29.9.3` · Testcontainers `2.0.5` · Imagen `postgres:17.11-alpine`

### Resultados Observados (`.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=!ApgApiApplicationTests" test`)
- **Total ejecutado:** **`251` pruebas ejecutadas, `0` fallos, `0` errores, `0` omitidas (`BUILD SUCCESS`)**.
  - `RestaurantCrudAndAuthorizationQaIntegrationTest`: `4/4` (PostgreSQL real)
  - `RestaurantStaffControllerTest`: `8/8`
  - `RestaurantControllerTest`: `7/7`
  - `RestaurantUpdateControllerTest`: `6/6`
  - `RestaurantServiceTest`: `14/14`
  - `PromocionControllerTest`: `29/29`
  - `PromocionServiceTest`: `9/9`
  - `SecurityAndAuthIntegrationTest`: `21/21`
  - `ViewCountAtomicityTest`: `5/5` (PostgreSQL real)
  - Resto de suites unitarias e integración PostgreSQL (`PromotionMetricsQueryTest`, `PromotionRepositoryTest`, `RegistrationIntegrationTest`, `CanjeConcurrencyIntegrationTest`, `PromotionLifecycleIntegrationTest`, etc.): todas en verde (`0` fallos).
- **Exclusión declarada:** `ApgApiApplicationTests` se excluyó explícitamente porque intenta conectar contra la URL/credenciales externas de Supabase configuradas en `.env` cuando no se pasan variables `DB_URL`/`DB_USER`/`DB_PASSWORD` locales.

### Comandos para Reproducir
```powershell
# 1. Ejecutar solo las suites asociadas a APG-73
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=PromocionServiceTest,PromocionControllerTest,RestaurantServiceTest,RestaurantControllerTest,RestaurantUpdateControllerTest,RestaurantStaffControllerTest,SecurityAndAuthIntegrationTest,RestaurantCrudAndAuthorizationQaIntegrationTest" test

# 2. Ejecutar toda la regresión local con Docker/Testcontainers
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=!ApgApiApplicationTests" test

# 3. Validar colección Postman (con la aplicación levantada en http://localhost:8080)
# Importar docs/postman/Savory-Studio-Restaurant-CRUD-Auth-QA.postman_collection.json en Postman Collection Runner
```
