# Contrato de API: Métricas Agregadas por Promoción (`Promotion Metrics` - APG-69)

Documentación técnica y guía de pruebas para la consulta de métricas agregadas (vistas, cupones reclamados y cupones canjeados) de una promoción específica, restringida exclusivamente al propietario del restaurante dueño de dicha promoción.

---

## 1. Requisitos Previos para el Equipo (Configuración Local)

Esta funcionalidad reutiliza la infraestructura de seguridad JWT y el esquema de base de datos existente (`discounts` y `coupon`):

- **Dependencias de Maven (`pom.xml`):** No se agregaron nuevas dependencias.
- **Variables de entorno (`.env`):** No se requieren nuevas variables de entorno.
- **Base de Datos:** No es necesario ejecutar ningún script SQL adicional.
- **Autenticación para pruebas:** Contar con al menos dos usuarios registrados en el entorno (uno propietario de un restaurante con promociones creadas y otro usuario distinto) para obtener sus respectivos tokens JWT mediante `POST /api/auth/login`.

---

## 2. Resumen de Endpoints

| Método | Ruta | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/promociones/{discountId}/metrics` | Privado (Propietario) | Obtiene los contadores agregados de vistas, cupones reclamados y cupones canjeados de una promoción específica. |

---

## 3. Detalle del Endpoint

### 3.1. Obtener Métricas Agregadas de una Promoción

Retorna los contadores agregados de rendimiento de una promoción sin incrementar su contador de vistas ni exponer identidades o datos personales de los clientes. El acceso está restringido estrictamente al propietario (`owner`) del restaurante al que pertenece la promoción.

- **URL:** `/api/promociones/{discountId}/metrics`
- **Método:** `GET`
- **Autenticación:** Requerida (`Bearer Token` JWT)
- **Headers:**
  ```http
  Authorization: Bearer <JWT_TOKEN>
  ```

#### Path Parameters

| Parámetro | Tipo | Obligatorio | Descripción |
| :--- | :--- | :--- | :--- |
| `discountId` | `Integer` | Sí | Identificador único de la promoción (descuento) a consultar. |

#### Respuestas

- **200 OK** (Métricas obtenidas exitosamente por el propietario del restaurante):
  ```json
  {
    "promotionId": 1,
    "promotionName": "Almuerzo 20% off",
    "restaurantId": 5,
    "viewCount": 42,
    "totalClaimed": 10,
    "totalRedeemed": 3
  }
  ```

  | Campo | Tipo | Descripción |
    | :--- | :--- | :--- |
  | `promotionId` | `Integer` | ID de la promoción. |
  | `promotionName` | `String` | Nombre de la promoción. |
  | `restaurantId` | `Integer` | ID del restaurante dueño de la promoción. |
  | `viewCount` | `Integer` | Cantidad total de vistas registradas de la promoción. |
  | `totalClaimed` | `Long` | Cantidad total de cupones reclamados (asociados) para esta promoción. |
  | `totalRedeemed` | `Long` | Cantidad total de cupones efectivamente canjeados (`coupon_status = 'USED'`). |

- **400 Bad Request** (Formato inválido de `discountId`, por ejemplo texto en lugar de entero):
  ```json
  {
    "timestamp": "2026-11-27T20:30:00.000",
    "status": 400,
    "error": "Bad Request",
    "message": "Invalid parameter format."
  }
  ```

- **401 Unauthorized** (Token `Bearer` ausente, inválido o expirado):
  ```json
  {
    "timestamp": "2026-11-27T20:30:00.000",
    "status": 401,
    "error": "Unauthorized",
    "message": "The Bearer token is invalid or expired."
  }
  ```

- **403 Forbidden** (El usuario autenticado no es el propietario del restaurante dueño de la promoción):
  ```json
  {
    "timestamp": "2026-11-27T20:30:00.000",
    "status": 403,
    "error": "Forbidden",
    "message": "You do not have permission to view metrics for this promotion."
  }
  ```

- **404 Not Found** (No existe una promoción con el `discountId` indicado):
  ```json
  {
    "timestamp": "2026-11-27T20:30:00.000",
    "status": 404,
    "error": "Not Found",
    "message": "Promotion not found."
  }
  ```

---

## 4. Casos de Uso / Escenarios de Prueba (QA)

Para validar correctamente la funcionalidad en Postman o Swagger UI (`http://localhost:8080/swagger-ui.html`), ejecutar los siguientes escenarios:

### Caso 1: Consulta exitosa por el propietario del restaurante (Flujo feliz)
1. Iniciar sesión en `POST /api/auth/login` con el propietario del restaurante que creó la promoción (ej. `discountId = 1`).
2. Enviar `GET /api/promociones/1/metrics` enviando el token en el header `Authorization: Bearer <token_propietario>`.
3. **Resultado esperado:** Código `200 OK` con los 6 campos agregados (`promotionId`, `promotionName`, `restaurantId`, `viewCount`, `totalClaimed`, `totalRedeemed`), sin datos personales de clientes ni campos extra.
4. **Verificación adicional:** Repetir la petición y confirmar que `viewCount` **no** se incrementa al consultar métricas.

### Caso 2: Consulta sin token o con token inválido/expirado
1. Enviar `GET /api/promociones/1/metrics` sin el header `Authorization` (o con un token alterado).
2. **Resultado esperado:** Código `401 Unauthorized`.

### Caso 3: Consulta por un usuario autenticado que NO es propietario del restaurante
1. Iniciar sesión en `POST /api/auth/login` con un usuario distinto (otro propietario o cliente).
2. Enviar `GET /api/promociones/1/metrics` con `Authorization: Bearer <token_otro_usuario>`.
3. **Resultado esperado:** Código `403 Forbidden` con el mensaje `"You do not have permission to view metrics for this promotion."`.

### Caso 4: Consulta de una promoción inexistente
1. Enviar `GET /api/promociones/99999/metrics` con un token JWT válido.
2. **Resultado esperado:** Código `404 Not Found` con el mensaje `"Promotion not found."`.

### Caso 5: Formato inválido en el parámetro `discountId`
1. Enviar `GET /api/promociones/abc/metrics` con un token JWT válido.
2. **Resultado esperado:** Código `400 Bad Request`.