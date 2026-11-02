(() => {
    const API = "/api/dispersion";
    const dropzone = document.getElementById("dropzone");
    const fileInput = document.getElementById("file-input");
    const uploadStatus = document.getElementById("upload-status");
    const fileList = document.getElementById("file-list");
    const fileCount = document.getElementById("file-count");
    const emptyState = document.getElementById("empty-state");
    const reportView = document.getElementById("report-view");
    const reportTitle = document.getElementById("report-title");
    const reportFile = document.getElementById("report-file");
    const scatterEl = document.getElementById("scatter");
    const statsEl = document.getElementById("scatter-stats");
    const footnoteEl = document.querySelector("#report-view .chart-footnote");
    const summaryBody = document.getElementById("summary-body");
    const thGrupo = document.getElementById("th-grupo");
    const excludedPanel = document.getElementById("excluded-panel");
    const excludedSummary = document.getElementById("excluded-summary");
    const excludedList = document.getElementById("excluded-list");
    const downloadSvgBtn = document.getElementById("download-svg");
    const downloadPngBtn = document.getElementById("download-png");
    const summaryTable = document.getElementById("summary-table");
    const copyTableBtn = document.getElementById("copy-table");

    const COLORES = ["#12385b", "#c9a227", "#2e8b57", "#b5543c", "#6a4c93", "#3a86a8", "#8c6d46", "#d17a9a"];
    const numberFormatter = new Intl.NumberFormat("es-MX", { maximumFractionDigits: 2 });
    const formatNumber = (valor) => numberFormatter.format(valor);

    let selectedFile = null;
    let datosActuales = null;

    const SVG_DOWNLOAD_ICON = `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>`;

    const setStatus = (message, type = "") => {
        uploadStatus.textContent = message;
        uploadStatus.className = `status-line${type ? ` is-${type}` : ""}`;
    };

    const renderFileList = (archivos) => {
        fileCount.textContent = archivos.length;
        fileList.replaceChildren();

        if (!archivos.length) {
            const empty = document.createElement("li");
            empty.className = "empty-item";
            empty.textContent = "No hay archivos CSV todavía.";
            fileList.append(empty);
            return;
        }

        archivos.forEach((nombre) => {
            const item = document.createElement("li");
            item.className = `file-entry${nombre === selectedFile ? " is-active" : ""}`;

            const button = document.createElement("button");
            button.type = "button";
            button.className = `file-item${nombre === selectedFile ? " is-active" : ""}`;
            button.dataset.nombre = nombre;
            button.title = nombre;
            button.textContent = nombre;

            const downloadLink = document.createElement("a");
            downloadLink.className = "btn-file-download";
            downloadLink.href = `${API}/${encodeURIComponent(nombre)}/descargar`;
            downloadLink.download = nombre;
            downloadLink.title = `Descargar ${nombre}`;
            downloadLink.setAttribute("aria-label", `Descargar ${nombre}`);
            downloadLink.innerHTML = SVG_DOWNLOAD_ICON;

            item.append(downloadLink, button);
            fileList.append(item);
        });
    };

    const refreshFileList = async () => {
        const response = await fetch(API);
        if (!response.ok) {
            throw new Error("No se pudo actualizar la lista de archivos.");
        }
        renderFileList(await response.json());
    };

    const marcarSeleccion = (nombre) => {
        selectedFile = nombre;
        fileList.querySelectorAll(".file-item").forEach((button) => {
            const activo = button.dataset.nombre === nombre;
            button.classList.toggle("is-active", activo);
            button.closest(".file-entry")?.classList.toggle("is-active", activo);
        });
    };

    /** Cuantil con interpolación lineal, equivalente a CUARTIL.INC de Excel. */
    const cuantil = (ordenados, p) => {
        const posicion = (ordenados.length - 1) * p;
        const base = Math.floor(posicion);
        const resto = posicion - base;
        return base + 1 < ordenados.length
            ? ordenados[base] + resto * (ordenados[base + 1] - ordenados[base])
            : ordenados[base];
    };

    const rangos = (valores) => {
        const indices = valores.map((valor, i) => [valor, i]).sort((a, b) => a[0] - b[0]);
        const resultado = new Array(valores.length);
        let i = 0;
        while (i < indices.length) {
            let j = i;
            while (j + 1 < indices.length && indices[j + 1][0] === indices[i][0]) {
                j++;
            }
            const rangoPromedio = (i + j) / 2 + 1;
            for (let k = i; k <= j; k++) {
                resultado[indices[k][1]] = rangoPromedio;
            }
            i = j + 1;
        }
        return resultado;
    };

    const pearson = (a, b) => {
        const n = a.length;
        const mediaA = a.reduce((s, v) => s + v, 0) / n;
        const mediaB = b.reduce((s, v) => s + v, 0) / n;
        let cov = 0;
        let varA = 0;
        let varB = 0;
        for (let i = 0; i < n; i++) {
            cov += (a[i] - mediaA) * (b[i] - mediaB);
            varA += (a[i] - mediaA) ** 2;
            varB += (b[i] - mediaB) ** 2;
        }
        return varA && varB ? cov / Math.sqrt(varA * varB) : NaN;
    };

    const spearman = (puntos) => pearson(rangos(puntos.map((p) => p.x)), rangos(puntos.map((p) => p.y)));

    const ticksLog = (valores) => {
        const minimo = Math.floor(Math.log10(Math.min(...valores)));
        const maximo = Math.ceil(Math.log10(Math.max(...valores)));
        const tickvals = [];
        for (let e = minimo; e <= maximo; e++) {
            tickvals.push(10 ** e);
        }
        return { tickvals, ticktext: tickvals.map(formatNumber) };
    };

    let notaActual = [];

    const textoNota = (logX, logY, ocultosPorCero) => {
        let escala = "";
        if (logX && logY) {
            escala = "Los ejes de las abscisas y de las ordenadas se presentan en escala logarítmica decimal (base 10).";
        } else if (logX) {
            escala = "El eje de las abscisas se presenta en escala logarítmica decimal (base 10).";
        } else if (logY) {
            escala = "El eje de las ordenadas se presenta en escala logarítmica decimal (base 10).";
        }
        const ceros = ocultosPorCero
            ? `${formatNumber(ocultosPorCero)} ${ocultosPorCero === 1 ? "IES con valor 0 no se muestra" : "IES con valor 0 no se muestran"} en la escala logarítmica.`
            : "";
        return [escala, ceros].filter(Boolean);
    };

    const renderScatter = (datos) => {
        const usarLog = datos.puntos.every((p) => p.x > 0);
        const usarLogY = Boolean(datos.escalaLogY);
        const visibles = usarLogY ? datos.puntos.filter((p) => p.y > 0) : datos.puntos;
        const trazas = datos.grupos.map((grupo, i) => {
            const puntos = visibles.filter((p) => p.grupo === grupo);
            return {
                type: "scatter",
                mode: "markers",
                name: `${grupo} (${puntos.length})`,
                x: puntos.map((p) => p.x),
                y: puntos.map((p) => p.y),
                text: puntos.map((p) => p.nombre),
                customdata: puntos.map((p) => [formatNumber(p.x), formatNumber(p.y)]),
                hovertemplate: `<b>%{text}</b><br>${datos.etiquetaX}: %{customdata[0]}<br>${datos.etiquetaY}: %{customdata[1]}<extra>${grupo}</extra>`,
                marker: {
                    size: 11,
                    color: COLORES[i % COLORES.length],
                    opacity: 0.85,
                    line: { width: 1, color: "#ffffff" }
                }
            };
        });

        const xaxis = {
            title: { text: datos.etiquetaX, standoff: 12 },
            type: usarLog ? "log" : "linear",
            gridcolor: "#e6e9ee",
            zeroline: false,
            showline: true,
            linecolor: "#10233a",
            linewidth: 1.5,
            ticks: "outside",
            tickcolor: "#10233a",
            automargin: true
        };
        if (usarLog) {
            Object.assign(xaxis, ticksLog(datos.puntos.map((p) => p.x)), { tickmode: "array" });
        } else {
            xaxis.tickformat = ",d";
        }

        const layout = {
            paper_bgcolor: "#ffffff",
            plot_bgcolor: "#ffffff",
            font: { family: "Source Sans 3, Arial, sans-serif", size: 14, color: "#10233a" },
            margin: { l: 70, r: 20, t: 20, b: 60 },
            xaxis,
            yaxis: {
                title: { text: datos.etiquetaY, standoff: 12 },
                type: usarLogY ? "log" : "linear",
                gridcolor: "#e6e9ee",
                zeroline: false,
                showline: true,
                linecolor: "#10233a",
                linewidth: 1.5,
                ticks: "outside",
                tickcolor: "#10233a",
                automargin: true,
                ...(usarLogY
                    ? { ...ticksLog(visibles.map((p) => p.y)), tickmode: "array" }
                    : { rangemode: "tozero", tickformat: ",d" })
            },
            legend: {
                title: { text: datos.etiquetaGrupo },
                orientation: "h",
                x: 0,
                y: -0.2,
                yanchor: "top"
            },
            hoverlabel: { bgcolor: "#ffffff", font: { color: "#10233a" } }
        };

        notaActual = textoNota(usarLog, usarLogY, datos.puntos.length - visibles.length);
        footnoteEl.classList.toggle("d-none", !notaActual.length);
        footnoteEl.innerHTML = notaActual.length ? `<em>Nota.</em> ${notaActual.join(" ")}` : "";
        Plotly.react(scatterEl, trazas, layout, { responsive: true, displaylogo: false });
    };

    const renderStats = (datos) => {
        const rho = spearman(datos.puntos);
        const chips = [
            ["IES graficadas", formatNumber(datos.puntos.length)],
            ["Excluidas", formatNumber(datos.excluidos.length)],
            ["ρ de Spearman", Number.isNaN(rho) ? "—" : rho.toFixed(2)]
        ];
        statsEl.replaceChildren(...chips.map(([etiqueta, valor]) => {
            const chip = document.createElement("div");
            chip.className = "stat-chip";
            const strong = document.createElement("strong");
            strong.textContent = valor;
            const span = document.createElement("span");
            span.textContent = etiqueta;
            chip.append(strong, span);
            return chip;
        }));
    };

    const renderResumen = (datos) => {
        thGrupo.textContent = datos.etiquetaGrupo;
        summaryBody.replaceChildren(...datos.grupos.map((grupo) => {
            const valores = datos.puntos.filter((p) => p.grupo === grupo).map((p) => p.y)
                .sort((a, b) => a - b);
            const media = valores.reduce((s, v) => s + v, 0) / valores.length;
            const fila = document.createElement("tr");
            [
                grupo,
                valores.length,
                media,
                cuantil(valores, 0.5),
                valores[valores.length - 1],
                valores[0],
                cuantil(valores, 0.25),
                cuantil(valores, 0.75)
            ]
                .map((valor, i) => (i === 0 ? valor : formatNumber(valor)))
                .forEach((texto, i) => {
                    const celda = document.createElement("td");
                    celda.textContent = texto;
                    if (i > 0) {
                        celda.className = "col-cantidad";
                    }
                    fila.append(celda);
                });
            return fila;
        }));
    };

    const renderExcluidos = (datos) => {
        excludedPanel.classList.toggle("d-none", !datos.excluidos.length);
        excludedSummary.textContent = `Instituciones excluidas (${datos.excluidos.length}): sin dato o marcadas con 0`;
        excludedList.replaceChildren(...datos.excluidos.map((nombre) => {
            const li = document.createElement("li");
            li.textContent = nombre;
            return li;
        }));
    };

    const loadDispersion = async (nombre) => {
        marcarSeleccion(nombre);
        const response = await fetch(`${API}/${encodeURIComponent(nombre)}`);
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
            setStatus(payload.mensaje || "No se pudo cargar el archivo.", "error");
            return;
        }

        datosActuales = payload;
        reportTitle.textContent = payload.titulo;
        reportFile.textContent = payload.nombreArchivo;
        emptyState.classList.add("d-none");
        reportView.classList.remove("d-none");
        renderStats(payload);
        renderScatter(payload);
        renderResumen(payload);
        renderExcluidos(payload);
        setStatus(`Dataset cargado: ${payload.nombreArchivo}`, "ok");
    };

    const nombreArchivoExport = () => (datosActuales?.nombreArchivo || "dispersion")
        .replace(/\.csv$/i, "")
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/[^a-zA-Z0-9-_]+/g, "-")
        .replace(/^-+|-+$/g, "")
        .toLowerCase() || "dispersion";

    /**
     * Las posiciones "paper" de Plotly son fracciones del área de trazado; se miden los
     * elementos ya dibujados para colocar leyenda y nota debajo del título del eje X sin
     * salirse de la imagen, ampliando el margen inferior si hace falta.
     */
    const acomodarPieExportacion = async (contenedor) => {
        const separacion = 28;
        const margenFinal = 30;
        const inferiorDe = (selector) => {
            const elemento = contenedor.querySelector(selector);
            if (!elemento) {
                return null;
            }
            return elemento.getBoundingClientRect().bottom - contenedor.getBoundingClientRect().top;
        };
        const aPaper = (px) => {
            const { t, h } = contenedor._fullLayout._size;
            return -(px - (t + h)) / h;
        };

        for (let intento = 0; intento < 4; intento++) {
            const finEjeX = inferiorDe(".xtitle") ?? inferiorDe(".xaxislayer-above") ?? 0;
            await Plotly.relayout(contenedor, { "legend.y": aPaper(finEjeX + separacion) });

            const finLeyenda = inferiorDe(".legend") ?? finEjeX;
            if (contenedor.layout.annotations?.length) {
                await Plotly.relayout(contenedor, { "annotations[0].y": aPaper(finLeyenda + separacion) });
            }

            const leyenda = contenedor.querySelector(".legend");
            const inicioLeyenda = leyenda
                ? leyenda.getBoundingClientRect().top - contenedor.getBoundingClientRect().top
                : Infinity;
            const finEjeXActual = inferiorDe(".xtitle") ?? inferiorDe(".xaxislayer-above") ?? 0;
            const finPie = inferiorDe(".annotation") ?? finLeyenda;
            const exceso = finPie + margenFinal - contenedor._fullLayout.height;
            if (exceso <= 0 && inicioLeyenda >= finEjeXActual) {
                return;
            }
            if (exceso > 0) {
                await Plotly.relayout(contenedor, { "margin.b": contenedor._fullLayout.margin.b + exceso });
            }
        }
    };

    const descargarGrafica = async (formato, boton) => {
        if (!scatterEl.data) {
            setStatus("Cargue un dataset antes de descargar la gráfica.", "error");
            return;
        }
        boton.disabled = true;

        const exportador = document.createElement("div");
        exportador.style.cssText = "position:fixed;left:-10000px;top:0;width:2000px;height:1250px;background:#fff;";
        document.body.appendChild(exportador);

        try {
            const trazas = JSON.parse(JSON.stringify(scatterEl.data));
            trazas.forEach((traza) => {
                traza.marker = { ...traza.marker, size: 26, line: { width: 2, color: "#ffffff" } };
            });
            const layout = JSON.parse(JSON.stringify(scatterEl.layout));
            const fuenteEje = { size: 32, color: "#10233a" };
            layout.width = 2000;
            layout.height = 1250;
            layout.margin = { l: 200, r: 80, t: 150, b: 400 };
            layout.font = { family: "Arial, sans-serif", size: 32, color: "#10233a" };
            layout.title = {
                text: datosActuales.titulo,
                font: { size: 44, color: "#10233a" },
                x: 0.5
            };
            layout.legend = {
                ...layout.legend,
                y: -0.3,
                yanchor: "top",
                font: { family: "Arial, sans-serif", size: 28, color: "#10233a" },
                title: { text: datosActuales.etiquetaGrupo, font: { size: 30 } }
            };
            ["xaxis", "yaxis"].forEach((eje) => {
                layout[eje] = {
                    ...layout[eje],
                    tickfont: fuenteEje,
                    ...(layout[eje].showline ? { linewidth: 3, tickwidth: 3, ticklen: 12 } : {}),
                    title: { ...layout[eje].title, standoff: 30, font: { size: 40, color: "#10233a" } }
                };
            });
            layout.annotations = notaActual.length
                ? [{
                    text: `<i>Nota.</i> ${notaActual.join("<br>")}`,
                    showarrow: false,
                    xref: "paper",
                    yref: "paper",
                    x: 0,
                    y: -0.6,
                    xanchor: "left",
                    yanchor: "top",
                    align: "left",
                    font: { family: "Arial, sans-serif", size: 26, color: "#546e7a" }
                }]
                : [];

            await Plotly.newPlot(exportador, trazas, layout, { staticPlot: true, displaylogo: false });
            await acomodarPieExportacion(exportador);
            await Plotly.downloadImage(exportador, {
                format: formato,
                filename: nombreArchivoExport(),
                width: 2000,
                height: 1250,
                scale: formato === "png" ? 2 : 1
            });
            setStatus(`Gráfica descargada en ${formato.toUpperCase()}.`, "ok");
        } catch (error) {
            setStatus(`No se pudo generar el ${formato.toUpperCase()} de la gráfica.`, "error");
        } finally {
            Plotly.purge(exportador);
            exportador.remove();
            boton.disabled = false;
        }
    };

    const escapeHtml = (valor) => String(valor)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");

    const copiarTabla = async () => {
        if (!datosActuales || summaryBody.children.length === 0) {
            setStatus("Cargue un dataset antes de copiar la tabla.", "error");
            return;
        }

        const titulo = `${datosActuales.etiquetaY} por ${datosActuales.etiquetaGrupo.toLowerCase()}`;
        const encabezados = [...summaryTable.querySelectorAll("thead th")].map((celda) => celda.textContent.trim());
        const filas = [...summaryBody.querySelectorAll("tr")].map((fila) =>
            [...fila.children].map((celda) => celda.textContent.trim())
        );

        const texto = [titulo, encabezados.join("\t"), ...filas.map((fila) => fila.join("\t"))].join("\n");

        const estiloBase = "font-family:Arial,sans-serif;font-size:10pt;color:#222;padding:4px 6px;";
        const estiloEncabezado = (i) => `${estiloBase}background:#d9d9d9;font-weight:600;text-decoration:none;border:1px solid #bfbfbf;text-align:${i === 0 ? "left" : "center"};`;
        const estiloDato = (i) => `${estiloBase}background:#ffffff;border:1px solid #d0d0d0;text-align:${i === 0 ? "left" : "center"};`;

        const html = `<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:w="urn:schemas-microsoft-com:office:word">
<head><meta charset="utf-8">
<style>
  table { border-collapse: collapse; mso-yfti-tbllook: 0; }
  td, p { text-decoration: none; }
</style>
</head>
<body>
<table class="MsoNormalTable" border="0" cellspacing="0" cellpadding="0" style="border-collapse:collapse;border-spacing:0;mso-yfti-tbllook:0;mso-padding-alt:0 0 0 0;border:1px solid #bfbfbf;font-family:Arial,sans-serif;font-size:10pt;">
  <tr>
    <td style="background:#d9d9d9;border:none;mso-border-alt:none;mso-border-bottom-alt:none;padding:6px 8px;text-align:center;">
      <span style="font-family:Arial,sans-serif;font-size:10pt;font-weight:700;color:#222;text-decoration:none;border:none;">${escapeHtml(titulo)}</span>
    </td>
  </tr>
  <tr>
    <td style="padding:0;border:none;mso-border-alt:none;">
      <table width="100%" border="0" cellspacing="0" cellpadding="0" style="border-collapse:collapse;mso-yfti-tbllook:0;font-family:Arial,sans-serif;font-size:10pt;">
        <tr>${encabezados.map((h, i) => `<td style="${estiloEncabezado(i)}">${escapeHtml(h)}</td>`).join("")}</tr>
        ${filas.map((fila) => `<tr>${fila.map((celda, i) => `<td style="${estiloDato(i)}">${escapeHtml(celda)}</td>`).join("")}</tr>`).join("")}
      </table>
    </td>
  </tr>
</table>
</body>
</html>`;

        copyTableBtn.disabled = true;
        try {
            if (navigator.clipboard && window.ClipboardItem) {
                await navigator.clipboard.write([
                    new ClipboardItem({
                        "text/html": new Blob([html], { type: "text/html" }),
                        "text/plain": new Blob([texto], { type: "text/plain" })
                    })
                ]);
            } else {
                await navigator.clipboard.writeText(texto);
            }
            setStatus("Tabla copiada al portapapeles.", "ok");
        } catch (error) {
            setStatus("No se pudo copiar la tabla al portapapeles.", "error");
        } finally {
            copyTableBtn.disabled = false;
        }
    };

    const uploadFile = async (file) => {
        if (!file) {
            return;
        }
        if (!file.name.toLowerCase().endsWith(".csv")) {
            setStatus("Solo se permiten archivos con extensión .csv.", "error");
            return;
        }

        const formData = new FormData();
        formData.append("file", file);
        setStatus(`Subiendo ${file.name}…`);

        try {
            const response = await fetch(`${API}/upload`, { method: "POST", body: formData });
            const payload = await response.json().catch(() => ({}));
            if (!response.ok) {
                setStatus(payload.mensaje || "No se pudo subir el archivo.", "error");
                return;
            }
            selectedFile = payload.nombre;
            await refreshFileList();
            await loadDispersion(payload.nombre);
        } catch (error) {
            setStatus("No se pudo subir el archivo.", "error");
        }
    };

    dropzone.addEventListener("click", () => fileInput.click());
    dropzone.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            fileInput.click();
        }
    });
    fileInput.addEventListener("change", () => {
        const [file] = fileInput.files;
        uploadFile(file);
        fileInput.value = "";
    });

    ["dragenter", "dragover"].forEach((eventName) => {
        dropzone.addEventListener(eventName, (event) => {
            event.preventDefault();
            dropzone.classList.add("is-active");
        });
    });

    ["dragleave", "drop"].forEach((eventName) => {
        dropzone.addEventListener(eventName, (event) => {
            event.preventDefault();
            dropzone.classList.remove("is-active");
        });
    });

    dropzone.addEventListener("drop", (event) => {
        const [file] = event.dataTransfer.files;
        uploadFile(file);
    });

    fileList.addEventListener("click", (event) => {
        if (event.target.closest(".btn-file-download")) {
            return;
        }
        const button = event.target.closest(".file-item");
        if (button) {
            loadDispersion(button.dataset.nombre);
        }
    });

    downloadSvgBtn.addEventListener("click", () => descargarGrafica("svg", downloadSvgBtn));
    downloadPngBtn.addEventListener("click", () => descargarGrafica("png", downloadPngBtn));
    copyTableBtn.addEventListener("click", copiarTabla);
})();
