# ANUIES - Tablero Estadístico de Gráficas

Sistema web desarrollado con **Spring Boot**, **Thymeleaf** y **Plotly.js** para el procesamiento, análisis y visualización de datos estadísticos institucionales de la **ANUIES** (Asociación Nacional de Universidades e Instituciones de Educación Superior).

El tablero tiene dos secciones, accesibles desde la navegación del encabezado (junto al perfil del usuario):

- **Diagramas de caja** (`/`): a partir de CSV con estadísticos precalculados agrupados por **Región** o **Rango de Matrícula**, genera **diagramas de caja (*Box Plots*)** y tablas de cuartiles.
- **Diagramas de dispersión** (`/dispersion`): a partir de CSV con un registro por institución, genera **diagramas de dispersión** coloreados por grupo, con resumen estadístico por grupo.

Ambas secciones ofrecen exportación en SVG y PNG de alta resolución y copiado de tablas al portapapeles.

---

## Características Principales

- **Diagramas de caja (*Box Plots*):**
  - Generación dinámica con rango intercuartílico ($Q1 - Q3$), mediana, media aritmética punteada y rango total (mínimo a máximo), con leyenda explicativa.
  - **Detección inteligente de escala:** Aplica escala logarítmica decimal (base 10) por defecto para datos con alta dispersión, o escala lineal automática cuando existen valores iguales a cero o cuando se especifica la palabra clave `dec` en el título del reporte.
  - Tabla de cuartiles con copia al portapapeles y generador de tablas en formato **Markdown** (ventana modal para previsualización y copiado).
- **Diagramas de dispersión:**
  - Un punto por institución; color por grupo (por ejemplo, rango de matrícula) con leyenda ordenada de menor a mayor.
  - Eje de las abscisas en escala logarítmica decimal automática cuando todos los valores son positivos.
  - Eje de las ordenadas en escala logarítmica cuando la línea de título contiene `log -coordenadas`.
  - Indicadores: IES graficadas, IES excluidas y coeficiente de correlación **ρ de Spearman**.
  - Tabla por grupo con IES, Media, Mediana, Máximo, Mínimo, Q1 y Q3 (cuartiles equivalentes a `CUARTIL.INC` de Excel) y botón **Copiar tabla**.
  - Lista desplegable de instituciones excluidas (sin dato o marcadas con 0).
- **Gestión de Archivos CSV:**
  - Área interactiva *Drag & Drop* en cada sección para subida de archivos (límite configurable, por defecto 10 MB).
  - Almacenamiento local configurable con directorios separados: `./datos` (cajas) y `./datos/dispersion` (dispersión).
  - Explorador y selector rápido de archivos almacenados.
  - Opción de descarga directa del archivo CSV original desde la interfaz.
- **Exportación de Gráficas y Datos:**
  - Exportación de gráficos en **SVG** vectorial.
  - Exportación en **PNG** en alta resolución (2000 × 1250 px a escala 2x), con fuentes ampliadas y la nota de escala incluida, optimizado para publicaciones e informes ejecutivos. La exportación se genera fuera de pantalla, sin alterar la gráfica visible.
  - Copia de tablas al portapapeles en Arial 10 pt, con encabezado sombreado en gris, lista para pegar en Word o Excel.
- **Seguridad y Control de Acceso:**
  - Autenticación mediante **Google OAuth2 / OpenID Connect (SSO)**.
  - Lista blanca (*whitelist*) de correos autorizados (`app.security.allowed-emails`).
  - Auditoría y registro detallado en consola de accesos exitosos e intentos no autorizados.
  - Soporte para proxies inversos (Nginx, Traefik, Apache con SSL/HTTPS) mediante cabeceras reenviadas.
  - Tolerancia de hasta 1 hora de desfase de reloj al validar el ID Token de Google (evita errores `invalid_id_token` por el claim `iat`). Aun así, se recomienda sincronizar el reloj del servidor con NTP.

---

## Formato del CSV para Diagramas de Caja

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

## Formato del CSV para Diagramas de Dispersión

Los archivos se cargan en la sección **Diagramas de dispersión** y se guardan en `./datos/dispersion`. Aplican las mismas reglas de delimitadores, codificación y formato de números descritas arriba.

### 1. Estructura General del Archivo

1. **Línea de título (opcional):** Se reconoce porque tiene menos de 4 celdas con texto. Su primer texto se usa como título del reporte. Si se omite, el título se forma con las cabeceras: `<valor Y> vs. <valor X>`.
   > **Escala logarítmica en las ordenadas:** Si la línea de título contiene `log -coordenadas` (en cualquier celda o dentro del propio texto; no distingue mayúsculas y también acepta `log-ordenadas`), el eje $Y$ se presenta en escala logarítmica. La marca se omite del título mostrado. Las IES con valor 0 no pueden representarse en esa escala: no se dibujan, se indica cuántas son en la nota y sí se consideran en los indicadores y la tabla.
2. **Fila de encabezados:** Al menos 4 columnas; sus nombres se usan como títulos de ejes, leyenda y tabla.
3. **Registros de datos:** Una fila por institución.

### 2. Especificación de Columnas (por posición)

| Columna | Ejemplo | Tipo | Descripción |
|---|---|---|---|
| **1** | `Institución` | Texto | Nombre de la institución; se muestra al pasar el cursor sobre el punto. |
| **2** | `Proyectos TI` | Numérico | Valor del eje de las ordenadas ($Y$). |
| **3** | `Alumnos` | Numérico | Valor del eje de las abscisas ($X$). |
| **4** | `Rango alumnos` | Texto | Grupo que define el color del punto, la leyenda y las filas de la tabla resumen. |
| **5** *(opcional)* | `Para gráfica (1=sí)` | Marca | Si existe, solo se grafican las filas con `1`, `sí`, `si`, `x` o `true`. |

Las filas marcadas con 0 o con valores no numéricos (por ejemplo, `Sin valor` o vacío) se excluyen y se listan en el apartado **Instituciones excluidas**.

### 3. Ejemplos

#### Ejemplo A: Sin línea de título
```csv
Institución,Proyectos TI,Alumnos,Rango alumnos,Para gráfica (1=sí)
Benemérita Universidad Autónoma de Puebla,40,124312,50000 o más,1
"El Colegio de México, A.C.",43,487,Menos de 1000,1
Escuela Normal de Zumpango,Sin valor,291,Menos de 1000,0
```

#### Ejemplo B: Con título y escala logarítmica en las ordenadas
```csv
Proyectos TI por matrícula,log -coordenadas,,,
Institución,Proyectos TI,Alumnos,Rango alumnos,Para gráfica (1=sí)
Instituto Politécnico Nacional,50,212911,50000 o más,1
Universidad de Monterrey,110,22618,12000 a 49999,1
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

La configuración principal se encuentra en `src/main/resources/application.properties`, que no se versiona; la plantilla es [`application.properties.example`](src/main/resources/application.properties.example). Las principales propiedades configurables son:

| Propiedad | Valor por Defecto | Descripción |
|---|---|---|
| `server.port` | `17006` | Puerto HTTP en el que se ejecuta la aplicación. |
| `app.storage.path` | `./datos` | Ruta local del directorio de almacenamiento de archivos CSV de diagramas de caja. |
| `app.storage.dispersion-path` | `./datos/dispersion` | Ruta local del directorio de archivos CSV de diagramas de dispersión. Si no se define, se usa `${app.storage.path}/dispersion`. |
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

### 2. Crear el archivo de configuración

`application.properties` no se versiona (está en `.gitignore`). Créelo a partir de la plantilla y complete las credenciales de Google y los correos autorizados:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

### 3. Ejecutar en modo desarrollo

En Windows:
```powershell
.\mvnw.cmd spring-boot:run
```

En Linux / macOS:
```bash
./mvnw spring-boot:run
```

### 4. Compilar paquete ejecutable para producción
```bash
./mvnw clean package -DskipTests
java -jar target/graficas-0.1.0.jar
```

Una vez iniciado el servicio, acceda desde su navegador a:
```
http://localhost:17006
```

---

## Endpoints

Todas las rutas, excepto `/login` y los recursos estáticos, requieren sesión iniciada con Google.

### Vistas

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/` | Sección **Diagramas de caja**. |
| `GET` | `/dispersion` | Sección **Diagramas de dispersión**. |
| `GET` | `/login` | Página de inicio de sesión. |

### API de diagramas de caja

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/archivos/upload` | Carga un archivo CSV mediante multipart (`multipart/form-data`, campo `file`). |
| `GET` | `/api/archivos` | Devuelve el listado en JSON de los archivos `.csv` disponibles en el directorio de almacenamiento. |
| `GET` | `/api/archivos/{nombreArchivo}` | Obtiene en formato JSON la estructura analizada del archivo (`ReporteDto`), incluyendo título, categoría y lista de registros con sus cuartiles. |
| `GET` | `/api/archivos/{nombreArchivo}/descargar` | Descarga el archivo CSV original como archivo adjunto (`Content-Disposition: attachment`). |

### API de diagramas de dispersión

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/dispersion/upload` | Carga un archivo CSV de dispersión (`multipart/form-data`, campo `file`). |
| `GET` | `/api/dispersion` | Devuelve el listado en JSON de los archivos `.csv` del directorio de dispersión. |
| `GET` | `/api/dispersion/{nombreArchivo}` | Obtiene el archivo analizado (`DispersionDto`): título, etiquetas de ejes y grupo, grupos ordenados, puntos, instituciones excluidas y si las ordenadas usan escala logarítmica (`escalaLogY`). |
| `GET` | `/api/dispersion/{nombreArchivo}/descargar` | Descarga el archivo CSV original como adjunto. |

---

## Estructura del Proyecto

```text
graficas/
├── datos/                               # Datasets CSV de diagramas de caja
│   └── dispersion/                      # Datasets CSV de diagramas de dispersión
├── src/
│   ├── main/
│   │   ├── java/mx/unam/anuies/graficas/
│   │   │   ├── config/                  # Seguridad (SecurityConfig, validación del ID Token)
│   │   │   ├── controller/              # DashboardController (vistas), CsvApiController,
│   │   │   │                            # DispersionApiController, LoginController
│   │   │   ├── dto/                     # ReporteDto, RegistroRegionDto, DispersionDto,
│   │   │   │                            # PuntoDispersionDto, ErrorResponseDto
│   │   │   ├── exception/               # Manejador global y excepciones de CSV
│   │   │   ├── security/                # Autenticación Google OAuth2 / OIDC y lista blanca
│   │   │   ├── service/                 # CsvStorageService (cajas) y DispersionStorageService
│   │   │   └── GraficasApplication.java # Clase principal Spring Boot
│   │   └── resources/
│   │       ├── application.properties   # Configuración de puertos, storage y OAuth2
│   │       ├── static/
│   │       │   ├── css/dashboard.css    # Estilos compartidos
│   │       │   └── js/                  # dashboard.js (cajas) y dispersion.js (dispersión)
│   │       └── templates/               # index.html, dispersion.html, login.html y
│   │                                    # fragments/header.html (encabezado y navegación)
│   └── test/                            # Pruebas unitarias y de integración
├── pom.xml                              # Configuración y dependencias Maven
└── README.md                            # Documentación del sistema
```
