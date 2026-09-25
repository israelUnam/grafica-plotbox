(() => {
    const dropzone = document.getElementById("dropzone");
    const fileInput = document.getElementById("file-input");
    const uploadStatus = document.getElementById("upload-status");
    const fileList = document.getElementById("file-list");
    const fileCount = document.getElementById("file-count");
    const emptyState = document.getElementById("empty-state");
    const reportView = document.getElementById("report-view");
    const reportTitle = document.getElementById("report-title");
    const reportFile = document.getElementById("report-file");
    const tableBody = document.getElementById("data-table-body");
    const dataTable = document.getElementById("data-table");
    const tableTitleCell = document.getElementById("table-title-cell");
    const copyTableBtn = document.getElementById("copy-table");
    const copyMarkdownBtn = document.getElementById("copy-markdown");
    const markdownTextarea = document.getElementById("markdown-text");
    const copyMarkdownTextBtn = document.getElementById("copy-markdown-text");
    const markdownModalEl = document.getElementById("markdown-modal");
    const boxplotEl = document.getElementById("boxplot");
    const downloadSvgBtn = document.getElementById("download-svg");
    const downloadPngBtn = document.getElementById("download-png");
    const chartFootnote = document.getElementById("chart-footnote");

    const numberFormatter = new Intl.NumberFormat("es-MX", { maximumFractionDigits: 2 });
    let selectedFile = null;
    let escalaDecimalActiva = false;
    const markdownModal = markdownModalEl ? new bootstrap.Modal(markdownModalEl) : null;

    const notaEscalaOrdenadas = (tipoEje) => tipoEje === "linear"
        ? "El eje de las ordenadas se presenta en escala lineal."
        : "El eje de las ordenadas se presenta en escala logarítmica decimal (base 10).";

    const actualizarNotaEscala = (tipoEje, ocultarNota = false) => {
        if (!chartFootnote) {
            return;
        }
        if (ocultarNota) {
            chartFootnote.classList.add("d-none");
            chartFootnote.textContent = "";
            return;
        }
        chartFootnote.classList.remove("d-none");
        chartFootnote.innerHTML = `<em>Nota.</em> ${notaEscalaOrdenadas(tipoEje)}`;
    };

    const setStatus = (message, type = "") => {
        uploadStatus.textContent = message;
        uploadStatus.className = `status-line${type ? ` is-${type}` : ""}`;
    };

    const SVG_DOWNLOAD_ICON = `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>`;

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
            downloadLink.href = `/api/archivos/${encodeURIComponent(nombre)}/descargar`;
            downloadLink.download = nombre;
            downloadLink.title = `Descargar ${nombre}`;
            downloadLink.setAttribute("aria-label", `Descargar ${nombre}`);
            downloadLink.innerHTML = SVG_DOWNLOAD_ICON;

            item.append(downloadLink, button);
            fileList.append(item);
        });
    };

    const refreshFileList = async () => {
        const response = await fetch("/api/archivos");
        if (!response.ok) {
            throw new Error("No se pudo actualizar la lista de archivos.");
        }
        renderFileList(await response.json());
    };

    const formatNumber = (value) => {
        if (value === null || value === undefined || Number.isNaN(value)) {
            return "—";
        }
        return numberFormatter.format(value);
    };

    const escapeHtml = (valor) => String(valor)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");

    const renderTable = (titulo, registros, nombreCategoria = "Región") => {
        tableTitleCell.textContent = titulo;
        const thCategoria = document.getElementById("th-categoria")
            || document.querySelector("#data-table thead .table-columns th:first-child");
        if (thCategoria) {
            thCategoria.textContent = nombreCategoria;
        }
        tableBody.replaceChildren();
        registros.forEach((registro) => {
            const row = document.createElement("tr");
            [
                registro.region,
                formatNumber(registro.universidades),
                formatNumber(registro.media),
                formatNumber(registro.mediana),
                formatNumber(registro.maximo),
                formatNumber(registro.minimo),
                formatNumber(registro.q1),
                formatNumber(registro.q3)
            ].forEach((valor, indice) => {
                const cell = document.createElement("td");
                cell.textContent = valor;
                if (indice > 0) {
                    cell.className = "col-cantidad";
                }
                row.append(cell);
            });
            tableBody.append(row);
        });
    };

    const pasoNiceLineal = (rango, ticksObjetivo = 6) => {
        if (!(rango > 0)) {
            return 1;
        }
        const aproximado = rango / Math.max(ticksObjetivo - 1, 1);
        const potencia = Math.floor(Math.log10(aproximado));
        const base = 10 ** potencia;
        const fraccion = aproximado / base;
        let nice;
        if (fraccion <= 1) {
            nice = 1;
        } else if (fraccion <= 2) {
            nice = 2;
        } else if (fraccion <= 2.5) {
            nice = 2.5;
        } else if (fraccion <= 5) {
            nice = 5;
        } else {
            nice = 10;
        }
        return nice * base;
    };

    const ticksEjeY = (registros, forzarLineal = false) => {
        const numeros = registros
            .flatMap((registro) => [
                registro.minimo, registro.q1, registro.mediana,
                registro.media, registro.q3, registro.maximo
            ])
            .filter((valor) => Number.isFinite(valor));

        const minimoDatos = numeros.length ? Math.min(...numeros) : 1;
        const maximo = numeros.length ? Math.max(...numeros) : 10000;
        const usarLineal = forzarLineal || minimoDatos <= 0;

        // Escala lineal: cabecera con "dec", o mínimo en 0 (la logarítmica no admite 0).
        if (usarLineal) {
            const piso = forzarLineal ? Math.min(0, minimoDatos) : 0;
            const tope = maximo > piso ? maximo : piso + 1;
            const amplitud = Math.max(tope - piso, 1);
            const paso = pasoNiceLineal(amplitud, 6);
            const valores = [];
            const textos = [];
            const inicio = Math.floor(piso / paso) * paso;
            for (let valor = inicio; valor < tope - paso * 0.05; valor += paso) {
                const marca = Math.round(valor / paso) * paso;
                if (marca < piso - paso * 0.01) {
                    continue;
                }
                valores.push(marca);
                textos.push(numberFormatter.format(marca));
            }
            if (valores.length === 0) {
                valores.push(piso);
                textos.push(numberFormatter.format(piso));
            }
            const ultimoTick = valores[valores.length - 1];
            const rangoSuperior = Math.max(tope * 1.05, ultimoTick + paso * 0.35);
            return {
                tipo: "linear",
                valores,
                textos,
                rango: [piso, rangoSuperior]
            };
        }

        const minimo = Math.min(...numeros.filter((valor) => valor > 0));
        const expMin = Math.max(0, Math.floor(Math.log10(minimo)));
        const expMax = Math.max(expMin, Math.ceil(Math.log10(maximo)));

        const valores = [];
        const textos = [];
        for (let exp = expMin; exp <= expMax; exp++) {
            const valor = 10 ** exp;
            if (valor < maximo && Math.log10(maximo) - Math.log10(valor) >= 0.12) {
                valores.push(valor);
                textos.push(numberFormatter.format(valor));
            }
        }

        if (!valores.includes(maximo)) {
            valores.push(maximo);
            textos.push(numberFormatter.format(maximo));
        }

        return { tipo: "log", valores, textos, rango: null };
    };


    const renderBoxplot = (titulo, registros, nombreCategoria = "Región", forzarEscalaDecimal = false) => {
        const categorias = registros.map((registro) => registro.region);
        const traces = registros.map((registro) => ({
            type: "box",
            name: registro.region,
            x: [registro.region],
            q1: [registro.q1],
            median: [registro.mediana],
            q3: [registro.q3],
            lowerfence: [registro.minimo],
            upperfence: [registro.maximo],
            mean: [registro.media],
            width: 0.28,
            boxpoints: false,
            boxmean: true,
            showlegend: false,
            marker: { color: "#12385b" },
            line: { color: "#12385b", width: 1.4 },
            fillcolor: "rgba(196, 163, 90, 0.35)",
            whiskerwidth: 0.45
        }));

        traces.push(
            {
                type: "scatter",
                x: [null],
                y: [null],
                mode: "markers",
                name: "Rango intercuartílico (Q1–Q3)",
                marker: {
                    symbol: "square",
                    size: 16,
                    color: "rgba(196, 163, 90, 0.65)",
                    line: { color: "#12385b", width: 1.5 }
                },
                hoverinfo: "skip",
                showlegend: true
            },
            {
                type: "scatter",
                x: [null],
                y: [null],
                mode: "lines",
                name: "Media aritmética",
                line: { color: "#12385b", width: 2.4, dash: "dot" },
                hoverinfo: "skip",
                showlegend: true
            },
            {
                type: "scatter",
                x: [null],
                y: [null],
                mode: "lines",
                name: "Mediana",
                line: { color: "#12385b", width: 3 },
                hoverinfo: "skip",
                showlegend: true
            },
            {
                type: "scatter",
                x: [null],
                y: [null],
                mode: "markers",
                name: "Rango total",
                marker: {
                    symbol: "line-ns",
                    size: 18,
                    color: "#12385b",
                    line: { color: "#12385b", width: 3 }
                },
                hoverinfo: "skip",
                showlegend: true
            }
        );

        const ticksY = ticksEjeY(registros, forzarEscalaDecimal);

        const layout = {
            margin: { l: 120, r: 36, t: 86, b: 130 },
            paper_bgcolor: "#ffffff",
            plot_bgcolor: "#ffffff",
            showlegend: true,
            legend: {
                orientation: "h",
                yanchor: "bottom",
                y: 1.08,
                xanchor: "center",
                x: 0.5,
                bgcolor: "#ffffff",
                font: { family: "Arial, sans-serif", size: 12, color: "#10233a" }
            },
            boxmode: "overlay",
            boxgap: 0.65,
            font: { family: "Arial, sans-serif", color: "#10233a", size: 13 },
            xaxis: {
                title: {
                    text: nombreCategoria,
                    standoff: 28,
                    font: { size: 15, color: "#10233a" }
                },
                type: "category",
                categoryorder: "array",
                categoryarray: categorias,
                range: [-0.7, Math.max(categorias.length - 0.3, 0.7)],
                tickangle: -25,
                automargin: true,
                titlefont: { size: 15, color: "#10233a" }
            },
            yaxis: {
                type: ticksY.tipo,
                title: {
                    text: titulo,
                    standoff: 20,
                    font: { size: 15, color: "#10233a" }
                },
                titlefont: { size: 15, color: "#10233a" },
                automargin: true,
                tickmode: "array",
                tickvals: ticksY.valores,
                ticktext: ticksY.textos,
                ...(ticksY.rango ? { range: ticksY.rango, rangemode: "tozero" } : {}),
                exponentformat: "none",
                showexponent: "none",
                gridcolor: "#e6ddc8"
            }
        };

        Plotly.react(boxplotEl, traces, layout, {
            responsive: true,
            displaylogo: false,
            toImageButtonOptions: { format: "svg" }
        });
        actualizarNotaEscala(ticksY.tipo, forzarEscalaDecimal);
    };

    const nombreArchivoExport = () => {
        const base = (selectedFile || reportTitle.textContent || "boxplot")
            .replace(/\.csv$/i, "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .replace(/[^a-zA-Z0-9-_]+/g, "-")
            .replace(/^-+|-+$/g, "")
            .toLowerCase();
        return `${base || "boxplot"}`;
    };

    const descargarGrafica = async (formato, boton) => {
        if (!boxplotEl || !boxplotEl.data) {
            setStatus("Cargue un dataset antes de descargar la gráfica.", "error");
            return;
        }
        boton.disabled = true;

        const exportador = document.createElement("div");
        exportador.style.cssText = "position:fixed;left:-10000px;top:0;width:2000px;height:1250px;background:#fff;";
        document.body.appendChild(exportador);

        try {
            const traces = JSON.parse(JSON.stringify(boxplotEl.data));
            const layout = JSON.parse(JSON.stringify(boxplotEl.layout));
            layout.width = 2000;
            layout.height = 1250;
            layout.margin = { l: 280, r: 90, t: 180, b: 450 };
            layout.paper_bgcolor = "#ffffff";
            layout.plot_bgcolor = "#ffffff";
            layout.font = { ...(layout.font || {}), family: "Arial, sans-serif", size: 32 };
            layout.legend = {
                ...(layout.legend || {}),
                font: { family: "Arial, sans-serif", size: 28, color: "#10233a" }
            };
            layout.xaxis = {
                ...(layout.xaxis || {}),
                automargin: true,
                tickfont: { size: 32, color: "#10233a" },
                title: {
                    ...(layout.xaxis.title || {}),
                    standoff: 28,
                    font: { size: 40, color: "#10233a" }
                }
            };
            layout.yaxis = {
                ...(layout.yaxis || {}),
                automargin: true,
                tickfont: { size: 32, color: "#10233a" },
                title: {
                    ...(layout.yaxis.title || {}),
                    standoff: 50,
                    font: { size: 36, color: "#10233a" }
                }
            };
            layout.annotations = escalaDecimalActiva
                ? []
                : [
                    {
                        text: `<i>Nota.</i> ${notaEscalaOrdenadas(layout.yaxis?.type || "log")}`,
                        showarrow: false,
                        xref: "paper",
                        yref: "paper",
                        x: 0,
                        y: -0.68,
                        xanchor: "left",
                        yanchor: "bottom",
                        font: { family: "Arial, sans-serif", size: 24, color: "#546e7a" }
                    }
                ];

            await Plotly.newPlot(exportador, traces, layout, {
                staticPlot: true,
                displaylogo: false
            });
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

    const loadReport = async (nombreArchivo) => {
        selectedFile = nombreArchivo;
        document.querySelectorAll(".file-entry").forEach((entry) => {
            const button = entry.querySelector(".file-item");
            const match = button && button.dataset.nombre === nombreArchivo;
            entry.classList.toggle("is-active", match);
            if (button) {
                button.classList.toggle("is-active", match);
            }
        });

        const response = await fetch(`/api/archivos/${encodeURIComponent(nombreArchivo)}`);
        if (!response.ok) {
            const error = await response.json().catch(() => ({ mensaje: "No se pudo cargar el archivo." }));
            setStatus(error.mensaje || "No se pudo cargar el archivo.", "error");
            return;
        }

        const reporte = await response.json();
        const categoria = reporte.nombreCategoria || "Región";
        escalaDecimalActiva = Boolean(reporte.escalaDecimal);
        reportTitle.textContent = reporte.titulo;
        reportFile.textContent = reporte.nombreArchivo;
        renderTable(reporte.titulo, reporte.registros, categoria);
        renderBoxplot(reporte.titulo, reporte.registros, categoria, escalaDecimalActiva);
        emptyState.classList.add("d-none");
        reportView.classList.remove("d-none");
        setStatus(`Dataset cargado: ${reporte.nombreArchivo}`, "ok");
        window.requestAnimationFrame(() => Plotly.Plots.resize(boxplotEl));
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

        const response = await fetch("/api/archivos/upload", {
            method: "POST",
            body: formData
        });

        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
            setStatus(payload.mensaje || "No se pudo subir el archivo.", "error");
            return;
        }

        selectedFile = payload.nombre;
        await refreshFileList();
        setStatus(payload.mensaje || "Archivo cargado correctamente.", "ok");
        await loadReport(payload.nombre);
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

    const obtenerDatosTabla = () => {
        const titulo = tableTitleCell.textContent.trim();
        const encabezados = [...dataTable.querySelectorAll("thead .table-columns th")]
            .map((celda) => celda.textContent.trim());
        const filas = [...tableBody.querySelectorAll("tr")].map((fila) =>
            [...fila.children].map((celda) => celda.textContent.trim())
        );
        return { titulo, encabezados, filas };
    };

    const escaparMarkdownCelda = (valor) => String(valor)
        .replaceAll("|", "\\|")
        .replaceAll("\n", " ");

    const tablaActualMarkdown = () => {
        const { titulo, encabezados, filas } = obtenerDatosTabla();
        const encabezadoMd = `| ${encabezados.map(escaparMarkdownCelda).join(" | ")} |`;
        const separadorMd = `| ${encabezados.map(() => "---").join(" | ")} |`;
        const filasMd = filas.map((fila) => `| ${fila.map(escaparMarkdownCelda).join(" | ")} |`);
        return [
            `### ${titulo}`,
            "",
            encabezadoMd,
            separadorMd,
            ...filasMd
        ].join("\n");
    };

    const abrirMarkdown = () => {
        if (!dataTable || tableBody.children.length === 0) {
            setStatus("Cargue un dataset antes de generar el markdown.", "error");
            return;
        }
        markdownTextarea.value = tablaActualMarkdown();
        markdownModal.show();
        window.requestAnimationFrame(() => {
            markdownTextarea.focus();
            markdownTextarea.select();
        });
    };

    const copiarMarkdown = async () => {
        try {
            await navigator.clipboard.writeText(markdownTextarea.value);
            setStatus("Markdown copiado al portapapeles.", "ok");
        } catch (error) {
            setStatus("No se pudo copiar el markdown.", "error");
        }
    };

    const copiarTabla = async () => {
        if (!dataTable || tableBody.children.length === 0) {
            setStatus("Cargue un dataset antes de copiar la tabla.", "error");
            return;
        }

        const { titulo, encabezados, filas } = obtenerDatosTabla();

        const texto = [
            titulo,
            encabezados.join("\t"),
            ...filas.map((fila) => fila.join("\t"))
        ].join("\n");

        const estiloEncabezado = "background:#d9d9d9;color:#222;font-family:Arial,sans-serif;font-size:10pt;font-weight:600;text-align:left;text-decoration:none;padding:4px 6px;border:1px solid #bfbfbf;";
        const estiloEncabezadoCantidad = "background:#d9d9d9;color:#222;font-family:Arial,sans-serif;font-size:10pt;font-weight:600;text-align:center;text-decoration:none;padding:4px 6px;border:1px solid #bfbfbf;";
        const estiloDato = "background:#ffffff;color:#222;font-family:Arial,sans-serif;font-size:10pt;text-align:left;padding:4px 6px;border:1px solid #d0d0d0;";
        const estiloDatoCantidad = "background:#ffffff;color:#222;font-family:Arial,sans-serif;font-size:10pt;text-align:center;padding:4px 6px;border:1px solid #d0d0d0;";

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
        <tr>${encabezados.map((h, i) => `<td style="${i === 0 ? estiloEncabezado : estiloEncabezadoCantidad}">${escapeHtml(h)}</td>`).join("")}</tr>
        ${filas.map((fila) => `<tr>${fila.map((celda, i) => `<td style="${i === 0 ? estiloDato : estiloDatoCantidad}">${escapeHtml(celda)}</td>`).join("")}</tr>`).join("")}
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

    downloadSvgBtn.addEventListener("click", () => descargarGrafica("svg", downloadSvgBtn));
    downloadPngBtn.addEventListener("click", () => descargarGrafica("png", downloadPngBtn));
    copyTableBtn.addEventListener("click", copiarTabla);
    copyMarkdownBtn.addEventListener("click", abrirMarkdown);
    copyMarkdownTextBtn.addEventListener("click", copiarMarkdown);

    fileList.addEventListener("click", (event) => {
        if (event.target.closest(".btn-file-download")) {
            return;
        }
        const button = event.target.closest(".file-item");
        if (button) {
            loadReport(button.dataset.nombre);
        }
    });

    window.addEventListener("resize", () => {
        if (!reportView.classList.contains("d-none")) {
            Plotly.Plots.resize(boxplotEl);
        }
    });
})();
