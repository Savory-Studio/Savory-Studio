# Contrato API - Consulta de Restaurantes Propios y Detalles

Este documento describe los endpoints correspondientes a la consulta de los restaurantes asociados al propietario autenticado y el detalle de un restaurante específico, respetando la matriz de permisos.

## 1. Consultar Mis Restaurantes
Obtiene la lista de todos los restaurantes que pertenecen al usuario autenticado (dueño). Los registros se ordenan del más reciente al más antiguo.

- **URL:** `/api/restaurantes/mios`
- **Método:** `GET`
- **Autenticación:** Requerida (Token JWT).
- **Autorización:** Cualquier usuario autenticado (retorna lista vacía si no tiene restaurantes registrados).

### Respuestas
- **200 OK:** Devuelve la lista de restaurantes propios sin datos sensibles.
```json
[
  {
    "restaurantId": 1,
    "name": "La Parrilla de María",
    "ownerId": 4,
    "description": "El mejor asado de la ciudad",
    "fullAddress": "Calle 10 # 20-30, Centro",
    "creationDate": "2026-11-20T21:45:10.000"
  },
  {
    "restaurantId": 2,
    "name": "Café del Parque",
    "ownerId": 4,
    "description": "Café de especialidad y repostería",
    "fullAddress": "Carrera 15 # 40-10, Norte",
    "creationDate": "2026-11-18T10:30:00.000"
  }
]
```
- **401 Unauthorized:** No hay token, está expirado o es inválido.

---

## 2. Consultar Detalle de Restaurante
Obtiene la información detallada de un restaurante en particular mediante su ID.

- **URL:** `/api/restaurantes/{restaurantId}`
- **Método:** `GET`
- **Autenticación:** Requerida (Token JWT).
- **Autorización:** Solo el `DUEÑO` del restaurante (según la matriz de permisos).

### Parámetros de Ruta
- `restaurantId` (Integer): ID del restaurante a consultar.

### Respuestas
- **200 OK:** Devuelve el detalle del restaurante exitosamente.
```json
{
  "restaurantId": 1,
  "name": "La Parrilla de María",
  "ownerId": 4,
  "description": "El mejor asado de la ciudad",
  "fullAddress": "Calle 10 # 20-30, Centro",
  "creationDate": "2026-11-20T21:45:10.000"
}
```
- **401 Unauthorized:** No hay token, está expirado o es inválido.
- **403 Forbidden:** El usuario autenticado no tiene permisos para ver este restaurante (no es el dueño).
- **404 Not Found:** El restaurante especificado no existe en el sistema.