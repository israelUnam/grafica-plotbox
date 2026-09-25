# ANUIES - Tablero Estadístico de Gráficas Box Plot

Sistema web desarrollado con **Spring Boot**, **Thymeleaf** y **Plotly.js** para el procesamiento, análisis y visualización de datos estadísticos institucionales de la **ANUIES** (Asociación Nacional de Universidades e Instituciones de Educación Superior).

El tablero permite cargar archivos en formato CSV que contengan métricas cuantitativas agrupadas por **Región** o **Rango de Matrícula**, generando de manera automática **diagramas de caja (*Box Plots*)** y tablas interactivas de cuartiles con opciones avanzadas de exportación (SVG, PNG de alta resolución y Markdown).

---

## Características Principales

- **Visualización Estadística Avanzada:**
  - Generación dinámica de diagramas de caja (*Box Plots*) con rango intercuartílico ($Q1 - Q3$), mediana, media aritmética punteada y rango total (mínimo a máximo).
  - **Detección inteligente de escala:** Aplica escala logarítmica decimal (base 10) por defecto para datos con alta dispersión, o escala lineal automática cuando existen valores iguales a cero o cuando se especifica la palabra clave `dec` en el título del reporte.
- **Gestión de Archivos CSV:**
  - Área interactiva *Drag & Drop* para subida de archivos (límite configurable, por defecto 10 MB).
  - Almacenamiento local configurable (directorio `./datos`).
  - Explorador y selector rápido de archivos almacenados.
  - Opción de descarga directa del archivo CSV original desde la interfaz.
- **Exportación de Gráficas y Datos:**
  - Exportación de gráficos en **SVG** vectorial.
  - Exportación en **PNG** en alta resolución (2000 × 1250 px a escala 2x), optimizado para publicaciones e informes ejecutivos.
  - Copia rápida de la tabla al portapapeles.
  - Generador de tablas en formato **Markdown** con ventana modal para previsualización y copiado directo.
- **Seguridad y Control de Acceso:**
  - Autenticación mediante **Google OAuth2 / OpenID Connect (SSO)**.
  - Lista blanca (*whitelist*) de correos autorizados (`app.security.allowed-emails`).
  - Auditoría y registro detallado en consola de accesos exitosos e intentos no autorizados.
  - Soporte para proxies inversos (Nginx, Traefik, Apache con SSL/HTTPS) mediante cabeceras reenviadas.

---

## Formato del Documento CSV de Entrada

El sistema implementa un analizador flexible y tolerante a variantes comunes de formato (diferentes codificaciones, delimitadores y notaciones numéricas).

### 1. Estructura General del Archivo

El archivo CSV debe estructurarse obligatoriamente de la siguiente manera:

1. **Línea 1 — Título del reporte:** Texto descriptivo que se usará como título principal del gráfico y de la tabla. Puede incluir comas vacías de relleno al final.
   > **Nota sobre escala decimal:** Si el segundo campo de la primera línea contiene la palabra `dec` (o variaciones como `decimal`), el sistema forzará el eje $Y$ a **escala lineal/decimal** en lugar de logarítmica.
2. **Línea 2 — Fila de encabezados:** Debe contener exactamente **8 columnas** con los nombres de las métricas.
3. **Líneas 3 en adelante — Registros de datos:** Una fila por cada categoría o rango evaluado.

```csv
"Título del Reporte o Indicador",,,,,,,
Categoría,IES,Media,Mediana,Máximo,Mínimo,Q1,Q3
"Categoría 1",18,"1,626",216,"13,353",22,153,"2,026"
"Categoría 2",37,630,146,"10,397",13,90,452
```

---

### 2. Especificación de Columnas (8 Columnas Requeridas)

| Columna | Nombre Sugerido | Tipo | Descripción |
|---|---|---|---|
| **1** | `Región` o `Rango` | Texto | Identificador de la categoría (e.g., *Centro-Occidente*, *Metropolitana*, *Más de 100,000 estudiantes*). Si la cabecera contiene "rango", el eje $X$ se etiquetará como **Rango**; si contiene "regi", como **Región**. |
| **2** | `IES` o `Universidades` | Entero | Cantidad de Instituciones de Educación Superior o Universidades consideradas en la muestra. |
| **3** | `Media` | Numérico | Media aritmética del indicador. Se grafica con una línea punteada. |
| **4** | `Mediana` | Numérico | Mediana estadística ($Q2$). Se grafica con una línea sólida central. |
| **5** | `Máximo` | Numérico | Valor máximo observado (límite superior del bigote). |
| **6** | `Mínimo` | Numérico | Valor mínimo observado (límite inferior del bigote). |
| **7** | `Q1` | Numérico | Primer cuartil (percentil 25, base de la caja). |
| **8** | `Q3` | Numérico | Tercer cuartil (percentil 75, tope de la caja). |

---

### 3. Reglas de Compatibilidad y Parseo

- **Delimitadores admitidos:** El servicio detecta automáticamente el separador analizando la fila de cabecera:
  - Coma (`,`) — Predeterminado
  - Punto y coma (`;`)
  - Tabulación (`\t`)
- **Codificación de caracteres (*Encoding*):**
  - **UTF-8** (con o sin marca de orden de bytes `BOM` / `\uFEFF`).
  - **ISO-8859-1 (Latin-1)** para compatibilidad con archivos generados en versiones en español de Microsoft Excel en Windows.
- **Formato de números:**
  - Valores enteros simples: `123827`, `2500`
  - Números con separador de miles entre comillas: `"143,558"`, `"211,584"`
  - Números decimales tanto con punto (`143.55`) como con coma (`143,55`).

---

### 4. Ejemplos de Archivos CSV Válidos

#### Ejemplo A: Agrupado por Rango de Matrícula (con escala logarítmica)
```csv
"Estudiantes conectados a la red inalámbrica",,,,,,,
Rango,IES,Media,Mediana,Máximo,Mínimo,Q1,Q3
"Más de 100,000 estudiantes",6,"143,558",123827,"211,584","100,491",113413,"174,715"
"Entre 30,000 y 100,000 estudiantes",15,"46,733",40736,"96,042","9,000",32407,57954
"Menos de 30,000 estudiantes",89,"4,235",2500,"28,303",0,1129,"4,777"
```

#### Ejemplo B: Agrupado por Región Geográfica
```csv
Total de computadoras para uso administrativo,,,,,,,
Región,IES,Media,Mediana,Máximo,Mínimo,Q1,Q3
Centro-Occidente,18,"1,626",216,"13,353",22,153,"2,026"
Centro-Sur,37,630,146,"10,397",13,90,452
Metropolitana,14,"3,074",323,"27,941",7,91,"1,208"
```

#### Ejemplo C: Con forzado de escala decimal en la primera línea
```csv
"Razón de docentes por aula",dec,,,,,,
Región,IES,Media,Mediana,Máximo,Mínimo,Q1,Q3
Noroeste,12,4.5,4.2,8.1,1.5,3.0,5.8
Sur-Sureste,20,3.8,3.5,7.0,1.2,2.8,4.9
```

---

## Pila Tecnológica (*Tech Stack*)

- **Lenguaje:** Java 17
- **Framework:** Spring Boot 4.1.1
- **Módulos Spring:**
  - `spring-boot-starter-webmvc`
  - `spring-boot-starter-thymeleaf`
  - `spring-boot-starter-oauth2-client`
- **Frontend:**
  - HTML5 / CSS3 / JavaScript (Vanilla)
  - [Bootstrap 5.3.3](https://getbootstrap.com/)
  - [Plotly.js 2.35.2](https://plotly.com/javascript/)
  - Fuente *Source Sans 3*
- **Utilidades:** Project Lombok, Maven Wrapper (`mvnw`)

---

## Configuración y Variables de Entorno

La configuración principal se encuentra en [`src/main/resources/application.properties`](src/main/resources/application.properties). Las principales propiedades configurables son:

| Propiedad | Valor por Defecto | Descripción |
|---|---|---|
| `server.port` | `17006` | Puerto HTTP en el que se ejecuta la aplicación. |
| `app.storage.path` | `./datos` | Ruta local del directorio de almacenamiento de archivos CSV. |
| `spring.servlet.multipart.max-file-size` | `10MB` | Tamaño máximo permitido por archivo subido. |
| `spring.security.oauth2.client.registration.google.client-id` | `${GOOGLE_CLIENT_ID}` | Client ID de Google Cloud Console (variable de entorno `GOOGLE_CLIENT_ID`). |
| `spring.security.oauth2.client.registration.google.client-secret` | `${GOOGLE_CLIENT_SECRET}` | Client Secret de Google Cloud Console (variable de entorno `GOOGLE_CLIENT_SECRET`). |
| `app.security.allowed-emails` | `${ALLOWED_EMAILS:}` *(vacío por defecto)* | Lista de correos autorizados separados por comas (o variable de entorno `ALLOWED_EMAILS`). Si está vacío, cualquier cuenta Google autenticada puede ingresar. |
| `server.forward-headers-strategy` | `framework` | Procesa cabeceras `X-Forwarded-*` cuando se ejecuta tras un reverse proxy (Nginx, Traefik). |

> **Configuración en Google Cloud Console:**
> Asegúrese de registrar en la consola de Google las siguientes **URIs de redireccionamiento autorizadas**:
> - En desarrollo local: `http://localhost:17006/login/oauth2/code/google`
> - En servidor de producción: `https://<DOMINIO_O_IP>:17006/login/oauth2/code/google`

---

## Instalación y Ejecución

### Requisitos Previos

- **Java JDK 17** o superior instalado.
- Conexión a Internet (para la resolución de librerías CDN y dependencias Maven iniciales).

### 1. Clonar el repositorio
```bash
git clone <URL_DEL_REPOSITORIO>
cd graficas
```

### 2. Ejecutar en modo desarrollo

En Windows:
```powershell
.\mvnw.cmd spring-boot:run
```

En Linux / macOS:
```bash
./mvnw spring-boot:run
```

### 3. Compilar paquete ejecutable para producción
```bash
./mvnw clean package -DskipTests
java -jar target/graficas-0.0.1-SNAPSHOT.jar
```

Una vez iniciado el servicio, acceda desde su navegador a:
```
http://localhost:17006
```

---

## Endpoints de la API REST

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/archivos/upload` | Carga un archivo CSV mediante multipart (`multipart/form-data`, campo `file`). |
| `GET` | `/api/archivos` | Devuelve el listado en JSON de los archivos `.csv` disponibles en el directorio de almacenamiento. |
| `GET` | `/api/archivos/{nombreArchivo}` | Obtiene en formato JSON la estructura analizada del archivo (`ReporteDto`), incluyendo título, categoría y lista de registros con sus cuartiles. |
| `GET` | `/api/archivos/{nombreArchivo}/descargar` | Descarga el archivo CSV original como archivo adjunto (`Content-Disposition: attachment`). |

---

## Estructura del Proyecto

```text
graficas/
├── datos/                               # Directorio de almacenamiento de datasets CSV
├── src/
│   ├── main/
│   │   ├── java/mx/unam/anuies/graficas/
│   │   │   ├── config/                  # Seguridad (SecurityConfig, filtros y JWT)
│   │   │   ├── controller/              # CsvApiController, DashboardController, LoginController
│   │   │   ├── dto/                     # RegistroRegionDto, ReporteDto, ErrorResponseDto
│   │   │   ├── exception/               # Manejador global y excepciones de CSV
│   │   │   ├── security/                # Autenticación Google OAuth2 / OIDC y lista blanca
│   │   │   ├── service/                 # CsvStorageService (parseo, validación y almacenamiento)
│   │   │   └── GraficasApplication.java # Clase principal Spring Boot
│   │   └── resources/
│   │       ├── application.properties   # Configuración de puertos, storage y OAuth2
│   │       ├── static/                  # Estilos CSS y lógica JS de Plotly
│   │       └── templates/               # Vistas Thymeleaf (index.html, login.html)
│   └── test/                            # Pruebas unitarias y de integración
├── pom.xml                              # Configuración y dependencias Maven
└── README.md                            # Documentación del sistema
```
