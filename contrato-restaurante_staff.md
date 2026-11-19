# Contrato API - Gestión de Personal del Restaurante (Restaurant Staff)

Este documento describe los endpoints correspondientes a la gestión de empleados de un restaurante. Permite a los dueños vincular, listar y desvincular personal (staff) de sus respectivos restaurantes.

## 1. Vincular Empleado a un Restaurante
Asocia un usuario existente (con rol de empleado) a un restaurante específico.

- **URL:** `/api/restaurants/{id}/staff`
- **Método:** `POST`
- **Autenticación:** Requerida (Token JWT).
- **Autorización:** Solo el `DUEÑO` del restaurante.

### Parámetros de Ruta
- `id` (Long): ID del restaurante.

### Body (JSON)
```json
{
  "email": "emmanuel@example.com"
}
```

### Respuestas
- **201 Created:** El empleado fue vinculado exitosamente al restaurante.
```json
{
  "userId": 7,
  "email": "emmanuel@example.com",
  "assignedAt": "2026-11-18T22:28:27.034"
}
```
- **400 Bad Request:** Datos inválidos en la petición (ej. el formato del correo es inválido).
- **401 Unauthorized:** No hay token o es inválido.
- **403 Forbidden:** El usuario autenticado no es el dueño de este restaurante.
- **404 Not Found:** El restaurante no existe o no existe un usuario registrado con el correo proporcionado.
- **409 Conflict:** El usuario con el correo proporcionado ya es empleado de este restaurante.

---

## 2. Listar Personal del Restaurante
Obtiene la lista de todos los empleados vinculados a un restaurante específico.

- **URL:** `/api/restaurants/{id}/staff`
- **Método:** `GET`
- **Autenticación:** Requerida (Token JWT).
- **Autorización:** Solo el `DUEÑO` del restaurante.

### Parámetros de Ruta
- `id` (Long): ID del restaurante.

### Respuestas
- **200 OK:** Devuelve la lista de empleados.
```json
[
  {
    "userId": 7,
    "email": "emmanuel@example.com",
    "assignedAt": "2026-11-18T22:28:27.034"
  },
  {
    "userId": 8,
    "email": "laura@example.com",
    "assignedAt": "2026-11-18T22:35:12.104"
  }
]
```
- **401 Unauthorized:** No hay token o es inválido.
- **403 Forbidden:** El usuario autenticado no es el dueño de este restaurante.
- **404 Not Found:** El restaurante no existe.

---

## 3. Desvincular Empleado del Restaurante
Elimina la asociación entre un empleado y un restaurante. No elimina la cuenta del usuario, solo revoca su acceso a este restaurante específico.

- **URL:** `/api/restaurants/{restaurantId}/staff/{userId}`
- **Método:** `DELETE`
- **Autenticación:** Requerida (Token JWT).
- **Autorización:** Solo el `DUEÑO` del restaurante.

### Parámetros de Ruta
- `restaurantId` (Long): ID del restaurante.
- `userId` (Long): ID del empleado que se va a desvincular.

### Respuestas
- **204 No Content:** El empleado fue desvinculado exitosamente.
- **401 Unauthorized:** No hay token o es inválido.
- **403 Forbidden:** El usuario autenticado no es el dueño de este restaurante.
- **404 Not Found:** El restaurante no existe, o el usuario especificado no es empleado de este restaurante.