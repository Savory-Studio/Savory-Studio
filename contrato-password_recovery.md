# Contrato de API: Recuperación de Contraseña (`Password Recovery`)

Documentación técnica y guía de configuración para el flujo de solicitud de restablecimiento de contraseña y confirmación mediante token de un solo uso.

---

## 1. Requisitos Previos para el Equipo (Configuración Local)

Antes de levantar el proyecto o probar estos endpoints después de hacer `git pull`, cada integrante del equipo debe realizar los siguientes pasos:

### A. Recargar dependencias de Maven (`pom.xml`)
Se agregó la dependencia `spring-boot-starter-mail` para el envío de correos electrónicos vía SMTP.
* **En IntelliJ IDEA:** Haz clic en el ícono de **Reload All Maven Projects** (el botón de recarga en la pestaña lateral de Maven) o haz clic derecho sobre `pom.xml` -> **Maven** -> **Reload project**.
* **En VS Code / Terminal:** Ejecuta en la raíz del proyecto:
  ```bash
  ./mvnw clean compile
  ```

### B. Configurar variables de entorno en `.env`
Abre tu archivo `.env` en la raíz del proyecto (al mismo nivel que `pom.xml`) y agrega las siguientes variables al final:

```env
MAIL_HOST=sandbox.smtp.mailtrap.io
MAIL_PORT=2525
MAIL_USERNAME=<solicitar_username_al_equipo>
MAIL_PASSWORD=<solicitar_password_al_equipo>
MAIL_FROM=no-reply@savorystudio.com
PASSWORD_RESET_EXPIRATION_MINUTES=15
PASSWORD_RESET_FRONTEND_URL=http://localhost:3000/reset-password
```

> **Nota sobre la Base de Datos:** La tabla `password_reset_tokens` ya fue creada en la instancia compartida de Supabase, por lo que **no es necesario ejecutar ningún script SQL adicional**.
> **Nota sobre Seguridad:** El archivo `.env` está ignorado en `.gitignore` y nunca debe subirse al repositorio.

---

## 2. Resumen de Endpoints

| Método | Ruta | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/forgot-password` | Público | Solicita un enlace/token de recuperación de contraseña por correo electrónico. |
| `POST` | `/api/auth/reset-password` | Público | Valida el token recibido y actualiza la contraseña del usuario. |

---

## 3. Detalle de los Endpoints

### 3.1. Solicitar Recuperación de Contraseña

Genera un token criptográfico aleatorio (almacenado en base de datos únicamente como hash `SHA-256`), invalida cualquier token activo previo del mismo usuario y envía el correo electrónico con el token y el enlace de restablecimiento.

* **URL:** `/api/auth/forgot-password`
* **Método:** `POST`
* **Autenticación:** No requerida (Público)
* **Headers:**
  ```http
  Content-Type: application/json
  ```

#### Request Body
```json
{
  "email": "andres.propietario@savorystudio.test"
}
```

| Campo | Tipo | Obligatorio | Validaciones |
| :--- | :--- | :--- | :--- |
| `email` | `String` | Sí | No puede estar vacío (`@NotBlank`) y debe tener formato de correo válido (`@Email`). |

#### Respuestas

* **200 OK** (Se retorna siempre `200 OK` tanto si el correo existe como si no, para evitar enumeración de usuarios):
  ```json
  {
    "message": "If the email is registered, you will receive password reset instructions shortly."
  }
  ```

* **400 Bad Request** (Correo vacío o con formato inválido):
  ```json
  {
    "timestamp": "2026-11-23T12:30:00.000",
    "status": 400,
    "error": "Bad Request",
    "message": "Invalid email format"
  }
  ```

---

### 3.2. Restablecer Contraseña

Verifica que el token exista, no haya expirado (vigencia por defecto: `15 minutos`) y no haya sido utilizado previamente (`used_at IS NULL`). Si es válido, actualiza la contraseña cifrada con `BCrypt` y marca el token como usado.

* **URL:** `/api/auth/reset-password`
* **Método:** `POST`
* **Autenticación:** No requerida (Público)
* **Headers:**
  ```http
  Content-Type: application/json
  ```

#### Request Body
```json
{
  "token": "xK9mP2vL8nQ4rT7wZ1yB5cF0hJ3kM6pS9vX2zA4dG7jK",
  "newPassword": "NuevaPassword123!"
}
```

| Campo | Tipo | Obligatorio | Validaciones |
| :--- | :--- | :--- | :--- |
| `token` | `String` | Sí | No puede estar vacío (`@NotBlank`). |
| `newPassword` | `String` | Sí | No puede estar vacío (`@NotBlank`), mínimo 8 caracteres (`@Size(min = 8)`). |

#### Respuestas

* **200 OK** (Contraseña actualizada exitosamente):
  ```json
  {
    "message": "Password has been reset successfully."
  }
  ```

* **400 Bad Request** (Token inexistente, expirado, ya utilizado, o contraseña menor a 8 caracteres):
  ```json
  {
    "timestamp": "2026-11-23T12:35:00.000",
    "status": 400,
    "error": "Bad Request",
    "message": "Invalid or expired password reset token."
  }
  ```

---

## 4. Casos de Uso / Escenarios de Prueba (QA)

Para validar correctamente la funcionalidad en Postman o Swagger UI (`http://localhost:8080/swagger-ui.html`), ejecutar los siguientes escenarios en orden:

### Caso 1: Solicitud exitosa de recuperación (Flujo feliz - Paso 1)
1. Enviar `POST /api/auth/forgot-password` con un correo registrado (ej. `andres.propietario@savorystudio.test`).
2. **Resultado esperado:** Código `200 OK` y recepción de correo en la bandeja de **Mailtrap** con el asunto *"Savory Studio - Recuperación de contraseña"* conteniendo el `token`.

### Caso 2: Solicitud con correo no registrado (Prevención de enumeración)
1. Enviar `POST /api/auth/forgot-password` con `{"email": "noexiste@savorystudio.test"}`.
2. **Resultado esperado:** Código `200 OK` con el mismo mensaje genérico, **sin** enviar ningún correo a Mailtrap.

### Caso 3: Restablecimiento exitoso y verificación de Login (Flujo feliz - Paso 2)
1. Copiar el `token` del correo recibido en el Caso 1.
2. Enviar `POST /api/auth/reset-password` con ese `token` y una `newPassword` válida (mínimo 8 caracteres).
3. **Resultado esperado:** Código `200 OK`. Al probar `POST /api/auth/login` con la nueva contraseña, devuelve el token JWT correctamente (y rechaza la contraseña anterior).
4. *(Importante para pruebas compartidas)*: Volver a restablecer la contraseña original del usuario de prueba (`SavoryTest26-Andres!`) al finalizar.

### Caso 4: Reutilización de un token ya consumido (Un solo uso)
1. Volver a enviar `POST /api/auth/reset-password` utilizando exactamente el mismo `token` usado en el Caso 3.
2. **Resultado esperado:** Código `400 Bad Request` indicando que el token es inválido o ha expirado.

### Caso 5: Invalidación automática al generar un nuevo token
1. Solicitar dos veces seguidas `POST /api/auth/forgot-password` para el mismo usuario (se recibirán 2 correos en Mailtrap: `Token A` y `Token B`).
2. Intentar usar `Token A` (el primero) en `POST /api/auth/reset-password`.
3. **Resultado esperado:** Código `400 Bad Request` (`Token A` fue anulado automáticamente). Solo `Token B` (el más reciente) debe ser aceptado.

### Caso 6: Validación de contraseña débil o campos vacíos
1. Enviar `POST /api/auth/reset-password` con un token válido pero con `"newPassword": "123"`.
2. **Resultado esperado:** Código `400 Bad Request` por incumplir la longitud mínima requerida.