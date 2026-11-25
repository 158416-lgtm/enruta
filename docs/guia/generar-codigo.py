#!/usr/bin/env python3
"""
Genera 'docs/enruta-codigo-explicado.pdf': arquitectura, bases de datos, APIs, anotaciones y
fragmentos de codigo reales, explicados.

    python3 docs/guia/generar-codigo.py
"""
import os

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate,
    Flowable,
    Frame,
    KeepTogether,
    PageBreak,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUTPUT = os.path.join(ROOT, "docs", "enruta-codigo-explicado.pdf")

FONTS = "/usr/share/fonts/truetype/dejavu"
pdfmetrics.registerFont(TTFont("DejaVu", f"{FONTS}/DejaVuSans.ttf"))
pdfmetrics.registerFont(TTFont("DejaVu-Bold", f"{FONTS}/DejaVuSans-Bold.ttf"))
pdfmetrics.registerFont(TTFont("Mono", f"{FONTS}/DejaVuSansMono.ttf"))
pdfmetrics.registerFontFamily("DejaVu", normal="DejaVu", bold="DejaVu-Bold")

INK = colors.HexColor("#1e293b")
SOFT = colors.HexColor("#475569")
MUTED = colors.HexColor("#64748b")
PAPER = colors.HexColor("#ffffff")
LINE = colors.HexColor("#e2e8f0")
CODE_BG = colors.HexColor("#0f172a")
CODE_TXT = colors.HexColor("#e2e8f0")
CODE_COMMENT = colors.HexColor("#94a3b8")
CODE_ANNOT = colors.HexColor("#fbbf24")
CODE_KEY = colors.HexColor("#93c5fd")
CODE_STR = colors.HexColor("#86efac")

BLUE, BLUE_T = colors.HexColor("#2563eb"), colors.HexColor("#dbeafe")
GREEN, GREEN_T = colors.HexColor("#16a34a"), colors.HexColor("#dcfce7")
ORANGE, ORANGE_T = colors.HexColor("#ea580c"), colors.HexColor("#ffedd5")
PURPLE, PURPLE_T = colors.HexColor("#7c3aed"), colors.HexColor("#f3e8ff")
CYAN, CYAN_T = colors.HexColor("#0891b2"), colors.HexColor("#cffafe")
PINK, PINK_T = colors.HexColor("#db2777"), colors.HexColor("#fce7f3")
RED, RED_T = colors.HexColor("#dc2626"), colors.HexColor("#fee2e2")

WIDTH = A4[0] - 84


def st(name, size, leading, color=INK, bold=False, align=TA_LEFT):
    return ParagraphStyle(
        name,
        fontName="DejaVu-Bold" if bold else "DejaVu",
        fontSize=size,
        leading=leading,
        textColor=color,
        alignment=align,
    )


BODY = st("body", 10.2, 15)
SMALL = st("small", 8.8, 12.2, SOFT)
TINY = st("tiny", 7.8, 10.5, MUTED)
WHITE = st("white", 9.5, 12, PAPER, bold=True)


def p(text, style=BODY):
    return Paragraph(text, style)


def section(number, title, color):
    badge = Table(
        [[Paragraph(str(number), st("n", 13, 15, color, bold=True, align=TA_CENTER))]],
        colWidths=[24],
        rowHeights=[24],
    )
    badge.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), PAPER),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("ALIGN", (0, 0), (-1, -1), "CENTER"),
                ("ROUNDEDCORNERS", [12, 12, 12, 12]),
            ]
        )
    )
    bar = Table(
        [[badge, Paragraph(title, st("t", 14, 17, PAPER, bold=True))]],
        colWidths=[36, WIDTH - 36],
    )
    bar.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), color),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("LEFTPADDING", (1, 0), (1, 0), 9),
                ("LEFTPADDING", (0, 0), (0, 0), 6),
                ("TOPPADDING", (0, 0), (-1, -1), 6),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
                ("ROUNDEDCORNERS", [9, 9, 9, 9]),
            ]
        )
    )
    return KeepTogether([Spacer(1, 4), bar, Spacer(1, 11)])


class Code(Flowable):
    """Bloque de codigo con color por tipo de linea y comentarios en gris."""

    def __init__(self, text, width=WIDTH, size=7.2):
        super().__init__()
        self.lines = text.rstrip("\n").split("\n")
        self.width = width
        self.size = size
        self.leading = size * 1.45

    def wrap(self, available_width, available_height):
        self.height = 14 + len(self.lines) * self.leading
        return self.width, self.height

    def draw(self):
        canvas = self.canv
        canvas.setFillColor(CODE_BG)
        canvas.roundRect(0, 0, self.width, self.height, 7, fill=1, stroke=0)

        canvas.setFont("Mono", self.size)
        x = 10
        y = self.height - 10 - self.size
        for line in self.lines:
            code, comment = (line.split("//", 1) + [None])[:2] if "//" in line else (line, None)
            cursor = x
            stripped = code.lstrip()
            indent = code[: len(code) - len(stripped)]

            if indent:
                canvas.setFillColor(CODE_TXT)
                canvas.drawString(cursor, y, indent)
                cursor += pdfmetrics.stringWidth(indent, "Mono", self.size)

            if stripped.startswith("@"):
                token = stripped.split(" ")[0].split("(")[0]
                canvas.setFillColor(CODE_ANNOT)
                canvas.drawString(cursor, y, token)
                cursor += pdfmetrics.stringWidth(token, "Mono", self.size)
                rest = stripped[len(token) :]
                canvas.setFillColor(CODE_TXT)
                canvas.drawString(cursor, y, rest)
                cursor += pdfmetrics.stringWidth(rest, "Mono", self.size)
            else:
                first = stripped.split(" ")[0]
                color = CODE_KEY if first in {
                    "public", "private", "protected", "return", "if", "for", "new",
                    "final", "static", "class", "record", "enum", "import", "package",
                    "try", "catch", "throw", "while", "var", "void", "this",
                } else CODE_TXT
                canvas.setFillColor(color)
                canvas.drawString(cursor, y, stripped)
                cursor += pdfmetrics.stringWidth(stripped, "Mono", self.size)

            if comment:
                canvas.setFillColor(CODE_COMMENT)
                canvas.drawString(cursor, y, "//" + comment)

            y -= self.leading


def table(rows, widths, header=True, color=INK, tint=None, mono_cols=()):
    data = []
    for r, row in enumerate(rows):
        cells = []
        for c, value in enumerate(row):
            if isinstance(value, str):
                if header and r == 0:
                    cells.append(Paragraph(f"<b>{value}</b>", WHITE))
                elif c in mono_cols:
                    cells.append(
                        Paragraph(
                            f'<font name="Mono" size="7.4">{value}</font>', SMALL
                        )
                    )
                else:
                    cells.append(Paragraph(value, SMALL))
            else:
                cells.append(value)
        data.append(cells)

    commands = [
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ("GRID", (0, 0), (-1, -1), 0.35, LINE),
    ]
    if header:
        commands.append(("BACKGROUND", (0, 0), (-1, 0), color))
    if tint:
        commands.append(("ROWBACKGROUNDS", (0, 1), (-1, -1), [PAPER, tint]))

    t = Table(data, colWidths=widths)
    t.setStyle(TableStyle(commands))
    return t


def callout(label, text, color, tint):
    box = Table(
        [
            [
                [
                    Paragraph(label.upper(), st("cl", 7.6, 9.5, color, bold=True)),
                    Paragraph(text, st("cb", 9.4, 13, INK)),
                ]
            ]
        ],
        colWidths=[WIDTH],
    )
    box.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), tint),
                ("LINEBEFORE", (0, 0), (0, 0), 4, color),
                ("LEFTPADDING", (0, 0), (-1, -1), 11),
                ("RIGHTPADDING", (0, 0), (-1, -1), 11),
                ("TOPPADDING", (0, 0), (-1, -1), 7),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
                ("ROUNDEDCORNERS", [7, 7, 7, 7]),
            ]
        )
    )
    return KeepTogether([box, Spacer(1, 7)])


class Cover(Flowable):
    def __init__(self):
        super().__init__()
        self.width = WIDTH
        self.height = A4[1] - 120

    def wrap(self, aw, ah):
        return self.width, self.height

    def draw(self):
        c = self.canv
        w, h = self.width, self.height
        c.setFillColor(CODE_BG)
        c.roundRect(0, 0, w, h, 16, fill=1, stroke=0)

        # llaves decorativas
        c.setFillColor(colors.HexColor("#1e293b"))
        c.setFont("Mono", 120)
        c.drawString(24, h - 210, "{")
        c.drawRightString(w - 24, 120, "}")

        c.setFillColor(PAPER)
        c.setFont("DejaVu-Bold", 30)
        c.drawString(46, h - 110, "EnRuta por dentro")
        c.setFillColor(CYAN)
        c.setFont("DejaVu-Bold", 18)
        c.drawString(46, h - 142, "arquitectura, bases, APIs y código")

        c.setFillColor(colors.HexColor("#94a3b8"))
        c.setFont("DejaVu", 10.5)
        c.drawString(46, h - 175, "7 servicios · 4 bases de datos · 5 tópicos de Kafka · 125 tests")
        c.drawString(46, h - 193, "Cada pieza con su porqué, y los fragmentos que importan.")

        c.setFillColor(colors.HexColor("#475569"))
        c.setFont("Mono", 8.5)
        lines = [
            "$ ./scripts/start.sh",
            "  api-gateway .......... :18080",
            "  auth-service ......... :8081",
            "  shipment-service ..... :8082",
            "  dispatch-service ..... :8083",
            "  tracking-service ..... :8084",
            "  routing-eta-service .. :8085",
            "  notification-service . :8086",
        ]
        y = 330
        for line in lines:
            c.drawString(46, y, line)
            y -= 14

        c.setFillColor(colors.HexColor("#64748b"))
        c.setFont("DejaVu", 8.5)
        c.drawString(46, 62, "Documento técnico del proyecto · 2026")


def build():
    doc = BaseDocTemplate(
        OUTPUT,
        pagesize=A4,
        leftMargin=42,
        rightMargin=42,
        topMargin=44,
        bottomMargin=46,
        title="EnRuta por dentro: arquitectura y código",
        author="Proyecto EnRuta",
    )
    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="normal")

    def footer(canvas, _doc):
        canvas.saveState()
        canvas.setStrokeColor(LINE)
        canvas.setLineWidth(0.7)
        canvas.line(42, 34, A4[0] - 42, 34)
        canvas.setFont("DejaVu", 8)
        canvas.setFillColor(MUTED)
        canvas.drawString(42, 22, "EnRuta · el código explicado")
        canvas.drawRightString(A4[0] - 42, 22, f"página {canvas.getPageNumber()}")
        canvas.restoreState()

    doc.addPageTemplates([PageTemplate(id="page", frames=[frame], onPage=footer)])

    story = [Spacer(1, 10), Cover(), PageBreak()]

    # ---------------------------------------------------------------- 1
    story += [
        section(1, "De qué se trata el proyecto", BLUE),
        p(
            "<b>EnRuta</b> es una plataforma de logística de última milla. Un cliente pide un envío, "
            "un repartidor lo acepta, el sistema lo sigue en un mapa y al final llega un correo de "
            "confirmación. Corre entera en un portátil y se usa desde el navegador y el móvil."
        ),
        Spacer(1, 4),
        table(
            [
                ["Actor", "Qué hace", "Dónde"],
                ["Cliente", "Crea el envío, lo sigue y lo cancela", "web (React) o API"],
                ["Repartidor", "Recibe ofertas, acepta y reporta GPS", "web/PWA en el móvil"],
                ["Admin", "Ve todo y puede cancelar o reasignar", "API / panel"],
                ["Sistema", "Asigna, rastrea, calcula ETA y notifica", "7 microservicios"],
            ],
            [70, 230, WIDTH - 300],
            color=BLUE,
            tint=BLUE_T,
        ),
        Spacer(1, 10),
        p("<b>Las cifras del proyecto</b>"),
        table(
            [
                ["Elemento", "Cantidad", "Detalle"],
                ["Microservicios", "7", "gateway, auth, shipment, dispatch, tracking, routing-eta, notification"],
                ["Bases de datos", "4", "authdb, shipmentdb, dispatchdb, notifdb (PostgreSQL)"],
                ["Almacenes en memoria", "1", "Redis: posiciones, índice geográfico, caché y contadores"],
                ["Tópicos de Kafka", "5 + 4", "5 de negocio y 4 de mensajes fallidos (DLQ)"],
                ["Endpoints REST", "22", "más 1 WebSocket y 2 de Actuator por servicio"],
                ["Tests", "125", "unitarios con Mockito e integración con Testcontainers"],
                ["Decisiones documentadas", "13", "ADRs en docs/adr"],
            ],
            [120, 60, WIDTH - 180],
            color=BLUE,
            tint=BLUE_T,
        ),
        Spacer(1, 8),
        callout(
            "La idea en una línea",
            "Cada servicio tiene <b>un trabajo</b> y <b>su propia libreta</b>. Cuando pasa algo, lo "
            "<b>avisa</b>; nadie escribe en la libreta del otro.",
            BLUE,
            BLUE_T,
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------------- 2
    story += [
        section(2, "Arquitectura: quién es quién", PURPLE),
        p(
            "El navegador y el móvil hablan con <b>un solo</b> servicio (el gateway). Detrás, los "
            "servicios se hablan por HTTP cuando necesitan una respuesta y por Kafka cuando solo "
            "quieren avisar."
        ),
        Spacer(1, 6),
        table(
            [
                ["Servicio", "Puerto", "Responsabilidad", "Dato que posee"],
                ["api-gateway", "18080", "valida JWT, enruta y limita", "—"],
                ["auth-service", "8081", "usuarios, login y JWKS", "authdb"],
                ["shipment-service", "8082", "ciclo de vida del envío", "shipmentdb"],
                ["dispatch-service", "8083", "ofertas y asignación", "dispatchdb + Redis"],
                ["tracking-service", "8084", "GPS, geocercas y WebSocket", "Redis (sin BD)"],
                ["routing-eta-service", "8085", "rutas y ETA con OSRM", "caché en Redis"],
                ["notification-service", "8086", "correos con reintentos y DLQ", "notifdb"],
            ],
            [120, 40, 200, WIDTH - 360],
            color=PURPLE,
            tint=PURPLE_T,
            mono_cols=(0,),
        ),
        Spacer(1, 8),
        table(
            [
                ["Infraestructura", "Versión / detalle", "Puerto en el host"],
                ["Kafka (KRaft)", "5 tópicos + 4 DLQ, 3 particiones por tópico", "29092"],
                ["PostgreSQL 16", "4 bases independientes, una por servicio", "5433"],
                ["Redis 7", "GEO, última posición, read-model y caché", "6379"],
                ["OSRM", "extract de Fusagasugá recortado de Colombia", "5000"],
                ["MailHog", "SMTP falso para ver los correos", "8025"],
                ["Prometheus + Grafana", "8 targets y dashboard provisionado", "9090 / 3000"],
            ],
            [120, 240, WIDTH - 360],
            color=SOFT,
            tint=colors.HexColor("#f8fafc"),
            mono_cols=(0,),
        ),
        Spacer(1, 8),
        callout(
            "Regla de oro de la arquitectura",
            "Ningún servicio lee ni escribe la base de otro. Si necesita un dato, lo pide por HTTP "
            "o se suscribe al evento que lo anuncia. Eso es lo que permite cambiar uno sin tocar los demás.",
            PURPLE,
            PURPLE_T,
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------------- 3
    story += [
        section(3, "Las 4 bases de datos", GREEN),
        p(
            "Hay <b>cuatro</b> bases PostgreSQL. Cada una pertenece a un servicio y solo ese servicio "
            "la toca. En local viven en un mismo contenedor (para no gastar RAM), pero son bases "
            "separadas con su propio usuario y sus migraciones Flyway."
        ),
        Spacer(1, 4),
        table(
            [
                ["Base", "Tablas", "Para qué sirve"],
                ["authdb", "users, user_roles, refresh_tokens", "quién eres y con qué rol"],
                ["shipmentdb", "shipments, shipment_status_history, outbox, idempotency_keys, processed_events, shedlock", "el envío, su historia y los avisos pendientes"],
                ["dispatchdb", "drivers, offers, assignments, dispatch_state, outbox, processed_events, shedlock", "quién está libre, ofertas y asignaciones"],
                ["notifdb", "notifications, processed_events, shedlock", "correos, intentos y errores"],
            ],
            [70, 240, WIDTH - 310],
            color=GREEN,
            tint=GREEN_T,
            mono_cols=(0, 1),
        ),
        Spacer(1, 8),
        p("<b>¿Y tracking? No tiene base de datos.</b>"),
        p(
            "Su estado es efímero: la posición de hace 10 segundos no interesa. Vive en Redis y es "
            "reconstruible; si se reinicia, el siguiente ping lo corrige. Tampoco usa el patrón outbox "
            "porque no hay una transacción de base que proteger (ADR-0009)."
        ),
        Spacer(1, 6),
        callout(
            "Cómo se crean",
            "Cada servicio tiene sus migraciones en <font name='Mono' size='8'>src/main/resources/db/migration</font> "
            "y Flyway las aplica al arrancar. La tabla <font name='Mono' size='8'>flyway_schema_history</font> "
            "lleva el control. Hibernate corre con <font name='Mono' size='8'>ddl-auto: validate</font>: "
            "si el código y la base no coinciden, el servicio no arranca.",
            GREEN,
            GREEN_T,
        ),
        Spacer(1, 4),
        p("<b>Tablas compartidas por diseño</b>"),
        table(
            [
                ["Tabla", "Dónde", "Para qué"],
                ["outbox", "shipmentdb, dispatchdb", "evento escrito en la misma transacción (ADR-0004)"],
                ["processed_events", "las 4", "deduplicar eventos ya consumidos (eventId)"],
                ["idempotency_keys", "shipmentdb", "respuesta guardada por Idempotency-Key (ADR-0006)"],
                ["shedlock", "shipmentdb, dispatchdb, notifdb", "evitar que dos réplicas hagan el mismo tick"],
            ],
            [110, 150, WIDTH - 260],
            color=GREEN,
            tint=GREEN_T,
            mono_cols=(0,),
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------------- 4
    story += [
        section(4, "Kafka, Redis y OSRM", ORANGE),
        p("<b>Kafka: el tablón de avisos.</b> La clave de partición es siempre el id del envío o del conductor, así el orden por agregado está garantizado."),
        Spacer(1, 4),
        table(
            [
                ["Tópico", "Particiones", "Retención", "Quién publica"],
                ["shipment.events", "3", "7 días", "shipment"],
                ["dispatch.events", "3", "7 días", "dispatch"],
                ["tracking.gps.raw", "6", "1 hora", "tracking (ingesta)"],
                ["tracking.events", "3", "7 días", "tracking (geocercas)"],
                ["notification.commands", "3", "7 días", "shipment"],
                ["*.dlq", "1", "14 días", "consumidores que agotan reintentos"],
            ],
            [150, 60, 60, WIDTH - 270],
            color=ORANGE,
            tint=ORANGE_T,
            mono_cols=(0,),
        ),
        Spacer(1, 10),
        p("<b>Redis: lo que cambia todo el rato.</b>"),
        table(
            [
                ["Clave", "Tipo", "Contenido"],
                ["drivers:available", "GEO", "conductores libres con su posición (búsqueda por radio)"],
                ["driver:{id}:last", "HASH", "última lat/lng y hora del ping"],
                ["driver:{id}:shipment", "STRING", "envío que lleva ahora mismo"],
                ["shipment:{id}", "HASH", "origen, destino y conductor del envío"],
                ["geo:{envio}:{cerca}", "STRING", "contador de 3 pings y marca de confirmada"],
                ["eta:{lat,lng}:{lat,lng}", "STRING", "ruta cacheada 20 segundos"],
                ["rl:gps:{id}", "STRING", "límite de 3 lotes por segundo por conductor"],
            ],
            [160, 45, WIDTH - 205],
            color=CYAN,
            tint=CYAN_T,
            mono_cols=(0,),
        ),
        Spacer(1, 10),
        p("<b>OSRM: las calles reales.</b>"),
        p(
            "Se descarga el extract de Colombia (335 MB), se recorta al casco urbano de Fusagasugá "
            "(~4 MB) y se preprocesa una sola vez con <font name='Mono' size='8'>osrm-extract</font>, "
            "<font name='Mono' size='8'>osrm-partition</font> y <font name='Mono' size='8'>osrm-customize</font>. "
            "Después la demo funciona sin internet (las teselas del mapa sí las necesitan)."
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------------- 5
    story += [
        section(5, "Las APIs (22 endpoints)", PINK),
        p(
            "Todo entra por el gateway en <font name='Mono' size='8'>http://localhost:18080</font>. "
            "Salvo los marcados como públicos, requieren <font name='Mono' size='8'>Authorization: Bearer &lt;jwt&gt;</font>."
        ),
        Spacer(1, 4),
        table(
            [
                ["Método y ruta", "Servicio", "Auth", "Qué hace"],
                ["POST /api/v1/auth/register", "auth", "público", "crea un cliente"],
                ["POST /api/v1/auth/login", "auth", "público", "devuelve access + refresh"],
                ["POST /api/v1/auth/refresh", "auth", "público", "rota el refresh"],
                ["POST /api/v1/auth/logout", "auth", "público", "revoca el refresh"],
                ["GET /api/v1/auth/jwks", "auth", "público", "llave pública para validar"],
                ["GET /api/v1/auth/me", "auth", "jwt", "perfil del token"],
                ["POST /api/v1/shipments", "shipment", "jwt", "crea envío (Idempotency-Key)"],
                ["GET /api/v1/shipments", "shipment", "jwt", "lista (cliente ve lo suyo)"],
                ["GET /api/v1/shipments/{id}", "shipment", "jwt", "estado, historial y PIN"],
                ["POST /api/v1/shipments/{id}/cancel", "shipment", "jwt", "cancela con reglas por rol"],
                ["POST /api/v1/drivers/{id}/location", "dispatch", "jwt", "posición del conductor"],
                ["POST /api/v1/drivers/{id}/availability", "dispatch", "jwt", "AVAILABLE / BUSY / OFFLINE"],
                ["GET /api/v1/drivers/{id}/offers", "dispatch", "jwt", "ofertas vigentes"],
                ["POST /api/v1/offers/{id}/accept", "dispatch", "jwt", "acepta (tres candados)"],
                ["POST /api/v1/offers/{id}/reject", "dispatch", "jwt", "pasa al siguiente"],
                ["POST /api/v1/assignments/{id}/cancel", "dispatch", "jwt", "compensación de la saga"],
                ["GET /api/v1/dispatch/shipments/{id}", "dispatch", "jwt", "fase, ronda y ofertas"],
                ["POST /api/v1/tracking/drivers/{id}/location", "tracking", "jwt", "lote de GPS (202)"],
                ["GET /api/v1/tracking/shipments/{id}", "tracking", "jwt", "última posición y ETA"],
                ["GET /api/v1/eta", "routing", "jwt", "distancia y duración"],
                ["GET /api/v1/notifications", "notification", "jwt", "correos de un envío"],
                ["GET /api/v1/notifications/{id}", "notification", "jwt", "estado e intentos"],
                ["POST /api/v1/notifications/test", "notification", "ADMIN", "encola un correo de prueba"],
                ["WS /ws/tracking", "tracking", "jwt en query", "posiciones en vivo"],
            ],
            [215, 65, 55, WIDTH - 335],
            color=PINK,
            tint=PINK_T,
            mono_cols=(0,),
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------------- 6
    story += [
        section(6, "Errores: todos hablan el mismo idioma", RED),
        p(
            "Cualquier error sale como <font name='Mono' size='8'>application/problem+json</font> "
            "(RFC 9457) con un campo <font name='Mono' size='8'>code</font> estable. El frontend "
            "decide por el código, nunca por el texto."
        ),
        Spacer(1, 4),
        table(
            [
                ["HTTP", "code", "Cuándo"],
                ["400", "VALIDATION_ERROR", "falta un campo, un UUID mal escrito o una coordenada fuera de rango"],
                ["400", "MISSING_IDEMPOTENCY_KEY", "POST /shipments sin la cabecera"],
                ["401", "INVALID_CREDENTIALS", "correo o contraseña incorrectos"],
                ["401", "INVALID_REFRESH_TOKEN", "refresh vencido o ya usado"],
                ["403", "FORBIDDEN", "el recurso no es tuyo"],
                ["404", "SHIPMENT_NOT_FOUND", "no existe (o no es tuyo)"],
                ["409", "INVALID_TRANSITION", "cambio de estado no permitido"],
                ["409", "IDEMPOTENCY_KEY_REUSED", "misma clave con otro cuerpo"],
                ["409", "OFFER_EXPIRED / OFFER_NOT_PENDING", "la oferta ya no está vigente"],
                ["409", "OFFER_ALREADY_CLAIMED", "otro conductor la tomó primero"],
                ["409", "ASSIGNMENT_TAKEN", "dos aceptaron a la vez; el índice único decidió"],
                ["422", "IDEMPOTENCY_KEY_REUSED", "variante usada por algunos clientes"],
                ["429", "RATE_LIMITED", "más de 3 lotes de GPS por segundo"],
                ["500", "INTERNAL_ERROR", "fallo inesperado (sin filtrar detalles)"],
            ],
            [45, 190, WIDTH - 235],
            color=RED,
            tint=RED_T,
            mono_cols=(1,),
        ),
        Spacer(1, 8),
        callout(
            "El detalle importa",
            "Un UUID mal formado devolvía <b>500</b> hasta que lo probamos con un cliente real. "
            "Ahora es <b>400</b>: los errores del cliente no son culpa del servidor. Ese arreglo salió "
            "de operar el sistema, no de leer el código.",
            RED,
            RED_T,
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------------- 7
    story += [
        section(7, "Cómo está organizado el código", CYAN),
        p(
            "Es un proyecto Maven multi-módulo. Los dos módulos de abajo (<b>contracts</b> y "
            "<b>common</b>) no son servicios: son piezas compartidas."
        ),
        Spacer(1, 4),
        Code(
            """enruta/
├─ pom.xml                 parent: versiones y plugins
├─ contracts/              eventos y nombres de topicos
├─ common/                 outbox, idempotencia, errores, geo
├─ services/
│  ├─ api-gateway/         portero
│  ├─ auth-service/        recepcionista
│  ├─ shipment-service/    jefe de pedidos
│  ├─ dispatch-service/    coordinador
│  ├─ tracking-service/    rastreador
│  ├─ routing-eta-service/ navegador
│  └─ notification-service/cartero
├─ tools/driver-simulator/ conductores automaticos
├─ web/                    React + Leaflet
├─ infra/                  docker-compose y configuracion
└─ docs/                   guias, ADRs y PDFs"""
        ),
        Spacer(1, 8),
        p("<b>Dentro de cada servicio</b>, siempre las mismas capas:"),
        Spacer(1, 2),
        Code(
            """com.enruta.shipment
├─ domain/         entidades y reglas (Shipment, ShipmentStatus)
├─ repository/     interfaces Spring Data (ShipmentRepository)
├─ service/        casos de uso (ShipmentService)
├─ web/            controladores y DTOs (ShipmentController)
├─ messaging/      consumidores de Kafka
└─ config/         seguridad y beans"""
        ),
        Spacer(1, 8),
        callout(
            "Por qué estas capas",
            "El <b>dominio</b> no sabe de HTTP ni de Kafka: se puede probar solo. Los "
            "<b>controladores</b> solo traducen; los <b>servicios</b> orquestan. Cuando algo falla, "
            "sabes en qué capa mirar sin leer todo el módulo.",
            CYAN,
            CYAN_T,
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------------- 8
    story += [
        section(8, "Las anotaciones, una por una", PURPLE),
        p(
            "Si entiendes estas 20 anotaciones, entiendes el 90 % del código. Todas son de Spring "
            "o de Jakarta (JPA)."
        ),
        Spacer(1, 4),
        table(
            [
                ["Anotación", "Qué le dice a Spring", "Dónde aparece"],
                ["@SpringBootApplication", "aquí arranca el servicio", "clase *Application"],
                ["@RestController", "esta clase responde HTTP y devuelve JSON", "web/*Controller"],
                ["@RequestMapping", "prefijo de ruta de la clase", "controladores"],
                ["@GetMapping / @PostMapping", "método HTTP concreto", "controladores"],
                ["@PathVariable / @RequestParam", "toma un valor de la URL o del query", "controladores"],
                ["@RequestBody", "convierte el JSON del cuerpo en un objeto", "controladores"],
                ["@RequestHeader", "lee una cabecera (Idempotency-Key)", "controladores"],
                ["@Valid", "valida el objeto antes de entrar", "controladores"],
                ["@AuthenticationPrincipal", "inyecta el JWT ya validado", "controladores"],
                ["@Service / @Component", "es un bean de lógica", "service/"],
                ["@Configuration + @Bean", "declara objetos que Spring gestiona", "config/"],
                ["@Entity / @Table", "esta clase es una tabla", "domain/"],
                ["@Id / @Column", "clave primaria y nombre de columna", "domain/"],
                ["@Enumerated(STRING)", "guarda el enum como texto", "domain/"],
                ["@Version", "bloqueo optimista: evita pisar cambios", "Shipment, Driver"],
                ["@ElementCollection", "tabla hija (roles del usuario)", "User"],
                ["@Transactional", "todo o nada; si falla, se deshace", "service/"],
                ["@KafkaListener", "escucha un tópico en segundo plano", "messaging/"],
                ["@Scheduled + @SchedulerLock", "tarea periódica, solo en una réplica", "relay, expirador"],
                ["@CircuitBreaker", "si OSRM falla, usa el plan B", "OsrmClient"],
                ["@Value", "lee una propiedad de application.yml", "servicios y config"],
                ["@RestControllerAdvice", "maneja los errores de todos los controladores", "common/error"],
                ["@ExceptionHandler", "qué excepción atiende cada método", "common/error"],
                ["@Modifying + @Query", "UPDATE propio en el repositorio", "repositorios"],
            ],
            [165, 230, WIDTH - 395],
            color=PURPLE,
            tint=PURPLE_T,
            mono_cols=(0,),
        ),
        Spacer(1, 8),
        callout(
            "Truco para leerlas",
            "<b>@RestController</b> = puerta. <b>@Service</b> = cabeza. <b>@Entity</b> = libreta. "
            "<b>@KafkaListener</b> = oído. <b>@Scheduled</b> = despertador. <b>@Transactional</b> = "
            "todo o nada.",
            PURPLE,
            PURPLE_T,
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------------- 9
    story += [
        section(9, "El código que importa (1/3): el outbox", GREEN),
        p(
            "El cambio y el aviso se escriben <b>en la misma transacción</b>. Si falla uno, no existe "
            "el otro."
        ),
        Code(
            """// common/outbox/OutboxAppender.java
@Transactional(propagation = Propagation.MANDATORY)   // debe haber transaccion
public UUID append(EventEnvelope<?> envelope, String topic) {
    String payload = objectMapper.writeValueAsString(envelope);
    repository.save(new OutboxEvent(
        envelope.eventId(), envelope.aggregateId(), envelope.eventType(),
        topic, payload, Instant.now()));
    return envelope.eventId();
}"""
        ),
        Spacer(1, 6),
        p(
            "<b>@Transactional(MANDATORY)</b> es la clave: si alguien llama a este método sin una "
            "transacción abierta, explota. Así es imposible guardar un aviso suelto."
        ),
        Spacer(1, 6),
        Code(
            """// common/outbox/OutboxRelay.java
@Scheduled(fixedDelayString = "${enruta.outbox.poll-interval-ms:500}")
@SchedulerLock(name = "outbox-relay", lockAtMostFor = "PT30S")
@Transactional
public void publishPending() {
    List<OutboxEvent> pending = repository.lockPending(100);  // FOR UPDATE SKIP LOCKED
    for (OutboxEvent event : pending) {
        kafkaTemplate.send(event.getTopic(),
                           event.getAggregateId().toString(),
                           event.getPayload()).get(5, SECONDS);  // espera el ack
        event.markPublished(Instant.now());
    }
}"""
        ),
        Spacer(1, 6),
        p(
            "Se marca como publicado <b>solo después</b> del ack de Kafka. Si el envío falla, la fila "
            "queda pendiente y el siguiente tick reintenta. <b>SKIP LOCKED</b> permite varias réplicas "
            "sin publicar dos veces."
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------------- 10
    story += [
        section(10, "El código que importa (2/3): idempotencia y doble barrera", ORANGE),
        p("Dos problemas distintos: que un reintento no cree dos envíos, y que dos conductores no acepten el mismo pedido."),
        Spacer(1, 4),
        Code(
            """// shipment/service/IdempotencyService.java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean claim(String key, String requestHash) {
    int inserted = jdbc.update(\"\"\"
        INSERT INTO idempotency_keys (key, request_hash, response_status, created_at, expires_at)
        VALUES (?, ?, 0, ?, ?)
        ON CONFLICT (key) DO NOTHING\"\"\",
        key, requestHash, now, now.plus(TTL));
    return inserted == 1;   // solo uno gana la clave
}"""
        ),
        Spacer(1, 4),
        p(
            "El <b>INSERT ... ON CONFLICT DO NOTHING</b> es atómico: no hay ventana entre “mirar” e "
            "“insertar”. El que pierde recibe la respuesta guardada (o un 409 si el cuerpo cambió)."
        ),
        Spacer(1, 6),
        Code(
            """// dispatch/service/DispatchService.java  (aceptar una oferta)
String claimKey = "offer:" + offerId + ":claim";
Boolean claimed = redis.opsForValue()
    .setIfAbsent(claimKey, callerId.toString(), offerTtl);   // 1) candado en Redis
if (!Boolean.TRUE.equals(claimed)) throw conflict("OFFER_ALREADY_CLAIMED");

int updated = offers.updateStatusIfCurrent(                  // 2) condicion en la base
    offerId, CREATED, ACCEPTED);
if (updated == 0) throw conflict("OFFER_NOT_PENDING");

assignments.saveAndFlush(new Assignment(shipmentId, driverId)); // 3) indice unico activo"""
        ),
        Spacer(1, 4),
        p(
            "El tercer candado no se ve en el código: es el índice "
            "<font name='Mono' size='8'>uq_assignments_active</font> de PostgreSQL. Aunque fallaran "
            "los dos primeros, la base rechaza la segunda asignación activa del mismo envío."
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------------- 11
    story += [
        section(11, "El código que importa (3/3): GPS, geocerca y WebSocket", CYAN),
        p("La geocerca exige <b>3 pings seguidos</b> dentro del radio para no dispararse con el ruido del GPS."),
        Spacer(1, 4),
        Code(
            """// tracking/service/PositionService.java
double toPickup = GeoMath.distanceMeters(lat, lng, pickupLat, pickupLng);

boolean confirmed = geofences.registerHit(
        shipmentId, "PICKUP", toPickup <= 100);   // 100 metros

if (confirmed) {
    publishGeofence(shipmentId, driverId, "PICKUP", toPickup);
}

// ... y el destino solo se evalua si ya se confirmo el origen:
if (pickupDone) {
    boolean dropoff = geofences.registerHit(
            shipmentId, "DROPOFF", toDropoff <= 150);
    if (dropoff) publishGeofence(shipmentId, driverId, "DROPOFF", toDropoff);
}"""
        ),
        Spacer(1, 6),
        Code(
            """// tracking/ws/TrackingWebSocketHandler.java
public void afterConnectionEstablished(WebSocketSession session) {
    String shipmentId = queryParams(session.getUri()).get("shipmentId");
    String token      = queryParams(session.getUri()).get("token");

    jwtDecoder.decode(token);                 // el handshake exige JWT valido
    broadcaster.subscribe(shipmentId, session);
    sendSnapshot(session, shipmentId);        // ultima posicion al conectar
}"""
        ),
        Spacer(1, 6),
        callout(
            "Por qué el token va en la URL",
            "El API de WebSocket del navegador <b>no permite cabeceras propias</b>. Es una concesión "
            "consciente (ADR-0010): en producción se cambiaría por un ticket efímero de un solo uso.",
            CYAN,
            CYAN_T,
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------------- 12
    story += [
        section(12, "Los tests: qué prueba cada cosa", PINK),
        p(
            "Hay <b>125 tests</b>. Los unitarios usan <b>Mockito</b> (rápidos, sin infraestructura); los "
            "de integración usan <b>Testcontainers</b> (PostgreSQL, Redis y MailHog reales)."
        ),
        Spacer(1, 4),
        table(
            [
                ["Módulo", "Tests", "Qué cubre"],
                ["shipment-service", "33", "máquina de estados, idempotencia, eventos de dispatch/tracking"],
                ["dispatch-service", "17", "ofertas simultáneas, triple candado (50 hilos), liberación"],
                ["notification-service", "16", "plantillas, reintentos, DLQ, entrega real en MailHog"],
                ["auth-service", "16", "registro, login, rotación de refresh, JWKS"],
                ["common", "16", "outbox, deduplicación, errores RFC 9457, geodesia"],
                ["routing-eta-service", "11", "caché, fallback y cortocircuito con OSRM falso"],
                ["tracking-service", "8", "histéresis de geocerca con Redis real"],
                ["contracts", "4", "envoltorio de eventos y nombres de tópicos"],
                ["api-gateway", "3", "cabecera X-Request-Id"],
                ["driver-simulator", "1", "arranque del contexto"],
            ],
            [150, 45, WIDTH - 195],
            color=PINK,
            tint=PINK_T,
            mono_cols=(0,),
        ),
        Spacer(1, 8),
        p("<b>Los tres tests que más dicen del proyecto</b>"),
        Spacer(1, 2),
        table(
            [
                ["Test", "Qué demuestra"],
                ["DispatchConcurrencyTest", "50 hilos aceptando la misma oferta: 1 gana, 49 reciben 409 y queda 1 asignación activa."],
                ["ShipmentApiTest", "PostgreSQL real con Flyway: el envío y su evento de outbox se guardan en la misma transacción."],
                ["NotificationMailTest", "El correo llega de verdad a MailHog y se verifica por su API, no con un mock."],
            ],
            [150, WIDTH - 150],
            color=PINK,
            tint=PINK_T,
            mono_cols=(0,),
        ),
        Spacer(1, 6),
        callout(
            "Cómo correrlos",
            "<font name='Mono' size='8'>mvn verify</font> ejecuta formato, unitarios, integración y "
            "cobertura. Los de integración necesitan Docker encendido.",
            PINK,
            PINK_T,
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------------- 13
    story += [
        section(13, "Por dónde empezar a leer el código", BLUE),
        p("Si alguien abre el repositorio por primera vez, este es el orden que funciona:"),
        Spacer(1, 2),
        table(
            [
                ["#", "Archivo", "Por qué"],
                ["1", "services/shipment-service/.../ShipmentService.java", "el ciclo de vida del envío y el outbox"],
                ["2", "services/dispatch-service/.../DispatchService.java", "el corazón: ofertas y triple candado"],
                ["3", "services/tracking-service/.../PositionService.java", "GPS, geocercas y WebSocket"],
                ["4", "common/outbox/OutboxRelay.java", "cómo se publican los eventos de verdad"],
                ["5", "services/shipment-service/.../ShipmentController.java", "cómo entra una petición"],
                ["6", "infra/docker-compose.yml", "qué piezas hay y cómo se conectan"],
            ],
            [25, 250, WIDTH - 275],
            color=BLUE,
            tint=BLUE_T,
            mono_cols=(1,),
        ),
        Spacer(1, 10),
        p("<b>Comandos para explorarlo</b>"),
        Spacer(1, 2),
        Code(
            """# arrancar todo y abrir el mapa
./scripts/start.sh

# ver los 7 servicios narrando lo que hacen
./scripts/ver-servicios.sh

# jugar a ser cliente y repartidor
./scripts/cliente.py
./scripts/repartidor.py

# compilar y correr todos los tests
mvn verify"""
        ),
        Spacer(1, 8),
        callout(
            "Convención del proyecto",
            "Código en <b>inglés</b> (clases, tablas, commits); documentación y UI en <b>español</b>. "
            "Los commits siguen Conventional Commits y cada sprint deja un tag y un ADR.",
            BLUE,
            BLUE_T,
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------------- 14
    story += [
        section(14, "Glosario técnico", PURPLE),
        table(
            [
                ["Término", "En una frase"],
                ["Microservicio", "programa pequeño con un trabajo y su propia base de datos"],
                ["Evento", "aviso de algo que ya pasó, con un id único"],
                ["Outbox", "escribir el cambio y el aviso en la misma transacción"],
                ["Idempotencia", "hacer lo mismo dos veces tiene el mismo efecto que una"],
                ["Saga", "serie de pasos con compensación si uno falla"],
                ["DLQ", "bandeja de mensajes que fallaron demasiadas veces"],
                ["Circuit breaker", "si el servicio externo falla, dejo de llamarlo y uso el plan B"],
                ["Caché", "guardar una respuesta reciente para no recalcularla"],
                ["Geocerca", "círculo en el mapa; si entras, pasa algo"],
                ["Histéresis", "pedir varias señales seguidas para confirmar un cambio"],
                ["JWT / JWKS", "carné firmado y llave pública para verificarlo"],
                ["Flyway", "migraciones versionadas de la base de datos"],
                ["Testcontainers", "Docker levantado por el test para probar contra algo real"],
                ["Actuator", "endpoints de salud y métricas de cada servicio"],
            ],
            [110, WIDTH - 110],
            color=PURPLE,
            tint=PURPLE_T,
            mono_cols=(0,),
        ),
        Spacer(1, 12),
        p(
            "<i>Fin. Si puedes explicar el outbox, los tres candados y la geocerca con tus palabras, "
            "puedes defender este proyecto delante de cualquier jurado.</i>",
            st("fin", 10, 14, MUTED, align=TA_CENTER),
        ),
    ]

    doc.build(story)
    print(f"PDF generado: {OUTPUT}")


if __name__ == "__main__":
    build()
