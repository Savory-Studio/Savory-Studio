# Contrato: Generación de Hash Único de Cupón

## Objetivo
Cada cupón recibe un identificador UUID único e irrepetible (`coupon_hash`)
que sirve como payload del código QR para la validación presencial.

---

## Cambio en modelo de datos

### Tabla `coupon` — nueva columna

| Columna | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `coupon_hash` | `VARCHAR(36)` | `NOT NULL`, `UNIQUE` | UUID v4 generado al crear el cupón |

---

## Utilidad: `CouponHashGenerator`

**Paquete:** `com.apg.promociones.apg_api.util`

| Método | Retorna | Descripción |
|---|---|---|
| `generate()` | `String` (36 chars) | UUID v4 vía `java.util.UUID.randomUUID()` |

Se invoca al momento de crear/reclamar un cupón (RF-02, cuando se implemente).
Para los cupones existentes, el hash se genera vía SQL (`gen_random_uuid()`).

---

## Modificación al endpoint POST /api/canjes

### Request (actualizado)

```json
{
  "couponId": 3,
  "couponHash": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `couponId` | Integer | No* | ID numérico del cupón |
| `couponHash` | String | No* | UUID único del cupón (del QR) |

**\*Al menos uno de los dos debe estar presente.** Si ambos llegan, se
prioriza `couponHash` (es el flujo QR). Si ninguno llega → 400.

### Response (actualizado)

```json
{
  "couponId": 3,
  "couponHash": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "USED",
  "redemptionTimestamp": "2026-10-28T...",
  "message": "Cupón canjeado exitosamente."
}
```

Ahora incluye `couponHash` para que el frontend pueda confirmar qué cupón se canjeó.

### Errores

| HTTP | Condición | Mensaje |
|---|---|---|
| 400 | Ni `couponId` ni `couponHash` presentes | `"Debe proporcionar couponId o couponHash."` |
| 404 | Hash/ID no corresponde a ningún cupón | `"Cupón no encontrado."` |
| 409 | Reglas RN-01 a RN-05 (sin cambios) | Mensajes existentes |

---

## Reglas de negocio

- **RN-HASH-01:** El hash se genera una sola vez al crear el cupón y no cambia.
- **RN-HASH-02:** El hash es UUID v4 (36 caracteres, formato `xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx`).
- **RN-HASH-03:** La búsqueda por hash es case-sensitive.

---

## Matriz de pruebas

| ID | Escenario | Input | Expected |
|---|---|---|---|
| PH-01 | Canje por hash válido (cupón UNUSED) | `{ "couponHash": "..." }` | 201, cupón canjeado |
| PH-02 | Canje por couponId (compatibilidad) | `{ "couponId": 4 }` | 201, cupón canjeado |
| PH-03 | Hash inexistente | `{ "couponHash": "no-existe" }` | 404 |
| PH-04 | Ni hash ni ID | `{}` | 400 |
| PH-05 | Ambos presentes, hash tiene prioridad | `{ "couponId": 1, "couponHash": "..." }` | Busca por hash |
