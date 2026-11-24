#!/usr/bin/env python3
"""
Genera 'docs/enruta-explicado-con-plastilina.pdf': guia visual y didactica del proyecto.

    python3 docs/guia/generar.py
"""
import os

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
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
OUTPUT = os.path.join(ROOT, "docs", "enruta-explicado-con-plastilina.pdf")

FONT_DIR = "/usr/share/fonts/truetype/dejavu"
pdfmetrics.registerFont(TTFont("DejaVu", f"{FONT_DIR}/DejaVuSans.ttf"))
pdfmetrics.registerFont(TTFont("DejaVu-Bold", f"{FONT_DIR}/DejaVuSans-Bold.ttf"))
pdfmetrics.registerFontFamily("DejaVu", normal="DejaVu", bold="DejaVu-Bold")

# ---------------------------------------------------------------- paleta
INK = colors.HexColor("#1e293b")
SOFT = colors.HexColor("#475569")
MUTED = colors.HexColor("#64748b")
PAPER = colors.HexColor("#ffffff")
LINE = colors.HexColor("#e2e8f0")

PURPLE, PURPLE_T = colors.HexColor("#7c3aed"), colors.HexColor("#f3e8ff")
BLUE, BLUE_T = colors.HexColor("#2563eb"), colors.HexColor("#dbeafe")
GREEN, GREEN_T = colors.HexColor("#16a34a"), colors.HexColor("#dcfce7")
ORANGE, ORANGE_T = colors.HexColor("#ea580c"), colors.HexColor("#ffedd5")
CYAN, CYAN_T = colors.HexColor("#0891b2"), colors.HexColor("#cffafe")
PINK, PINK_T = colors.HexColor("#db2777"), colors.HexColor("#fce7f3")
AMBER, AMBER_T = colors.HexColor("#ca8a04"), colors.HexColor("#fef9c3")
RED, RED_T = colors.HexColor("#dc2626"), colors.HexColor("#fee2e2")

# ---------------------------------------------------------------- estilos
def style(name, size, leading, color=INK, bold=False, align=TA_LEFT, space_after=0):
    return ParagraphStyle(
        name,
        fontName="DejaVu-Bold" if bold else "DejaVu",
        fontSize=size,
        leading=leading,
        textColor=color,
        alignment=align,
        spaceAfter=space_after,
    )


BODY = style("body", 10.5, 15.5, INK, space_after=6)
SMALL = style("small", 9, 12.5, SOFT, space_after=4)
TINY = style("tiny", 8, 11, MUTED)
CARD_NAME = style("card_name", 12, 15, INK, bold=True)
CARD_BODY = style("card_body", 9.2, 12.6, SOFT)
WHITE = style("white", 10, 13, PAPER, bold=True)

WIDTH = A4[0] - 84


def p(text, st=BODY):
    return Paragraph(text, st)


def bullets(items, color=INK):
    rows = [[Paragraph("●", style("dot", 8, 14, color)), Paragraph(item, BODY)] for item in items]
    table = Table(rows, colWidths=[14, WIDTH - 14])
    table.setStyle(
        TableStyle(
            [
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 0),
                ("TOPPADDING", (0, 0), (-1, -1), 1),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
            ]
        )
    )
    return table


def section(number, title, color):
    """Barra de titulo con numero en circulo."""
    badge = Table(
        [[Paragraph(str(number), style("n", 14, 16, color, bold=True, align=TA_CENTER))]],
        colWidths=[26],
        rowHeights=[26],
    )
    badge.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), PAPER),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("ALIGN", (0, 0), (-1, -1), "CENTER"),
                ("ROUNDEDCORNERS", [13, 13, 13, 13]),
            ]
        )
    )
    bar = Table(
        [[badge, Paragraph(title, style("t", 15, 18, PAPER, bold=True))]],
        colWidths=[40, WIDTH - 40],
    )
    bar.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), color),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("LEFTPADDING", (1, 0), (1, 0), 10),
                ("LEFTPADDING", (0, 0), (0, 0), 7),
                ("TOPPADDING", (0, 0), (-1, -1), 6),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
                ("ROUNDEDCORNERS", [10, 10, 10, 10]),
            ]
        )
    )
    return KeepTogether([Spacer(1, 4), bar, Spacer(1, 12)])


def callout(label, text, color, tint):
    """Caja de aviso / idea clave."""
    content = [
        Paragraph(label.upper(), style("cl", 8, 10, color, bold=True)),
        Paragraph(text, style("cb", 9.8, 13.5, INK)),
    ]
    box = Table([[content]], colWidths=[WIDTH])
    box.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), tint),
                ("BOX", (0, 0), (-1, -1), 0, tint),
                ("LINEBEFORE", (0, 0), (0, 0), 4, color),
                ("LEFTPADDING", (0, 0), (-1, -1), 12),
                ("RIGHTPADDING", (0, 0), (-1, -1), 12),
                ("TOPPADDING", (0, 0), (-1, -1), 8),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
                ("ROUNDEDCORNERS", [8, 8, 8, 8]),
            ]
        )
    )
    return KeepTogether([box, Spacer(1, 8)])


def card(number, name, role, keeps, color, tint):
    """Tarjeta de un servicio."""
    badge = Table(
        [[Paragraph(str(number), style("b", 14, 16, PAPER, bold=True, align=TA_CENTER))]],
        colWidths=[30],
        rowHeights=[30],
    )
    badge.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), color),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("ALIGN", (0, 0), (-1, -1), "CENTER"),
                ("ROUNDEDCORNERS", [15, 15, 15, 15]),
            ]
        )
    )
    text = [
        Paragraph(name, CARD_NAME),
        Paragraph(role, CARD_BODY),
        Paragraph(f"<b>Su libreta:</b> {keeps}", style("k", 8.6, 11.5, MUTED)),
    ]
    row = Table([[badge, text]], colWidths=[42, WIDTH - 42])
    row.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), tint),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (1, 0), (1, 0), 10),
                ("LEFTPADDING", (0, 0), (0, 0), 8),
                ("RIGHTPADDING", (-1, 0), (-1, 0), 12),
                ("TOPPADDING", (0, 0), (-1, -1), 9),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 9),
                ("ROUNDEDCORNERS", [10, 10, 10, 10]),
            ]
        )
    )
    return KeepTogether([row, Spacer(1, 7)])


def flow(steps, color):
    """Fila horizontal de cajas con flechas."""
    cells, widths = [], []
    for index, (label, tint) in enumerate(steps):
        cells.append(Paragraph(label, style("f", 8.6, 11, INK, bold=True, align=TA_CENTER)))
        widths.append((WIDTH - 18 * (len(steps) - 1)) / len(steps))
        if index < len(steps) - 1:
            cells.append(Paragraph("→", style("a", 13, 14, color, bold=True, align=TA_CENTER)))
            widths.append(18)
    table = Table([cells], colWidths=widths)
    commands = [("VALIGN", (0, 0), (-1, -1), "MIDDLE")]
    for index in range(len(steps)):
        column = index * 2
        commands += [
            ("BACKGROUND", (column, 0), (column, 0), steps[index][1]),
            ("BOX", (column, 0), (column, 0), 0.8, color),
            ("ROUNDEDCORNERS", [8, 8, 8, 8]),
            ("TOPPADDING", (column, 0), (column, 0), 9),
            ("BOTTOMPADDING", (column, 0), (column, 0), 9),
        ]
    table.setStyle(TableStyle(commands))
    return KeepTogether([table, Spacer(1, 10)])


def step(number, title, text, color):
    badge = Paragraph(
        str(number), style("sn", 12, 14, PAPER, bold=True, align=TA_CENTER)
    )
    head = Table([[badge, Paragraph(title, style("st", 10.5, 13, INK, bold=True))]], colWidths=[26, WIDTH - 26])
    head.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (0, 0), color),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("ROUNDEDCORNERS", [13, 13, 13, 13]),
                ("LEFTPADDING", (1, 0), (1, 0), 8),
                ("TOPPADDING", (0, 0), (-1, -1), 3),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
            ]
        )
    )
    return KeepTogether([head, Spacer(1, 3), Paragraph(text, style("sb", 9.5, 13, SOFT)), Spacer(1, 9)])


# ---------------------------------------------------------------- portada
class Cover(Flowable):
    def __init__(self):
        super().__init__()
        self.width = WIDTH
        self.height = A4[1] - 120

    def wrap(self, available_width, available_height):
        return self.width, self.height

    def draw(self):
        canvas = self.canv
        width, height = self.width, self.height

        canvas.setFillColor(colors.HexColor("#0f172a"))
        canvas.roundRect(0, 0, width, height, 18, fill=1, stroke=0)

        # ruta decorativa
        canvas.setStrokeColor(CYAN)
        canvas.setLineWidth(5)
        canvas.setLineCap(1)
        path = canvas.beginPath()
        path.moveTo(50, 120)
        path.curveTo(140, 190, 210, 60, 330, 150)
        path.curveTo(400, 205, 430, 170, width - 60, 250)
        canvas.drawPath(path)
        canvas.setFillColor(GREEN)
        canvas.circle(50, 120, 9, fill=1, stroke=0)
        canvas.setFillColor(ORANGE)
        canvas.circle(width - 60, 250, 9, fill=1, stroke=0)

        canvas.setFillColor(PAPER)
        canvas.setFont("DejaVu-Bold", 34)
        canvas.drawString(50, height - 110, "EnRuta")
        canvas.setFont("DejaVu-Bold", 24)
        canvas.setFillColor(CYAN)
        canvas.drawString(50, height - 145, "explicado con plastilina")

        canvas.setFillColor(colors.HexColor("#94a3b8"))
        canvas.setFont("DejaVu", 12)
        canvas.drawString(50, height - 180, "Cómo funciona un sistema de microservicios")
        canvas.drawString(50, height - 197, "sin que se te derrita la cabeza")

        canvas.setFillColor(PAPER)
        canvas.setFont("DejaVu-Bold", 11)
        canvas.drawString(50, 96, "Cliente")
        canvas.drawString(width - 150, 262, "Repartidor")

        canvas.setFillColor(colors.HexColor("#94a3b8"))
        canvas.setFont("DejaVu", 9)
        canvas.drawString(50, 70, "Guía de estudio · 7 servicios · 1 pedido · muchas flechas")
        canvas.drawString(50, 55, "Proyecto universitario · Fusagasugá")


# ---------------------------------------------------------------- documento
def build():
    doc = BaseDocTemplate(
        OUTPUT,
        pagesize=A4,
        leftMargin=42,
        rightMargin=42,
        topMargin=44,
        bottomMargin=46,
        title="EnRuta explicado con plastilina",
        author="Proyecto EnRuta",
        subject="Guía visual de microservicios",
    )

    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="normal")

    def footer(canvas, _doc):
        canvas.saveState()
        canvas.setStrokeColor(LINE)
        canvas.setLineWidth(0.7)
        canvas.line(42, 34, A4[0] - 42, 34)
        canvas.setFont("DejaVu", 8)
        canvas.setFillColor(MUTED)
        canvas.drawString(42, 22, "EnRuta · guía visual")
        canvas.drawRightString(A4[0] - 42, 22, f"página {canvas.getPageNumber()}")
        canvas.restoreState()

    doc.addPageTemplates([PageTemplate(id="page", frames=[frame], onPage=footer)])

    story = [Spacer(1, 10), Cover(), PageBreak()]

    # ---------------------------------------------------------- 1. idea
    story += [
        section(1, "La idea, en una frase", BLUE),
        p(
            "Un cliente pide un envío. Un repartidor lo recoge y lo lleva. Tú miras el mapa "
            "mientras eso pasa. Y al final llega un correo diciendo <b>“entregado”</b>.",
            style("lead", 12.5, 18, INK),
        ),
        Spacer(1, 10),
        flow(
            [("Cliente<br/>pide", BLUE_T), ("Repartidor<br/>acepta", GREEN_T), ("Se mueve<br/>(GPS)", CYAN_T), ("Llega y<br/>avisa", AMBER_T)],
            SOFT,
        ),
        p(
            "Eso es todo. El resto del proyecto es <b>repartir ese trabajo entre varios programas pequeños</b> "
            "para que ninguno tenga que hacerlo todo."
        ),
        Spacer(1, 6),
        callout(
            "¿Qué es un microservicio?",
            "Un programa pequeño con <b>una sola responsabilidad</b>, que tiene <b>su propia libreta</b> "
            "y habla con los demás <b>por la red</b>. En vez de un empleado que hace de todo, "
            "tienes un equipo donde cada uno sabe lo suyo.",
            BLUE,
            BLUE_T,
        ),
        Spacer(1, 4),
        p("<b>¿Por qué no hacerlo todo en un solo programa?</b>", style("q", 11, 15, INK)),
        bullets(
            [
                "Porque el GPS escribe muchísimo y los envíos muy poco: no necesitan la misma fuerza.",
                "Porque si el correo se cae, el envío no debería dejar de funcionar.",
                "Porque cada parte puede cambiar sin tocar a las demás.",
            ],
            BLUE,
        ),
        Spacer(1, 8),
        callout(
            "La trampa",
            "Microservicios no significa “más archivos”. Significa <b>más conversaciones por la red</b>. "
            "Y las conversaciones por la red fallan: llegan tarde, llegan dos veces o no llegan. "
            "Casi todo este proyecto consiste en <b>sobrevivir a eso</b>.",
            RED,
            RED_T,
        ),
        Spacer(1, 6),
        callout(
            "Cómo leer esta guía",
            "<b>1.</b> Cada capítulo es una idea y cabe en una página. "
            "<b>2.</b> Las cajas de color son definiciones; las rojas, avisos. "
            "<b>3.</b> Al final hay un glosario y cinco preguntas para comprobar que lo entendiste.",
            INK,
            colors.HexColor("#f1f5f9"),
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------- 2. personajes
    story += [
        section(2, "Los 7 personajes", PURPLE),
        p("Cada servicio es una persona con un trabajo claro. Apréndete el nombre y su libreta."),
        Spacer(1, 4),
        card(1, "El portero  ·  api-gateway", "Recibe a todo el mundo por la puerta principal. Revisa el carné (JWT) y dice “por aquí”.", "No guarda nada: solo abre puertas.", PURPLE, PURPLE_T),
        card(2, "El recepcionista  ·  auth-service", "Da los carnés. Comprueba correo y contraseña y firma un pase con fecha de caducidad.", "Usuarios, contraseñas y carnés.", BLUE, BLUE_T),
        card(3, "El jefe de pedidos  ·  shipment-service", "Anota cada envío y su historia: creado, asignado, recogido, en camino, entregado.", "Los envíos y su historial.", GREEN, GREEN_T),
        card(4, "El coordinador  ·  dispatch-service", "Busca quién está cerca, le manda una oferta y decide quién se queda el pedido.", "Quién está libre y quién lleva qué.", ORANGE, ORANGE_T),
        card(5, "El rastreador  ·  tracking-service", "Recibe el GPS, dibuja el mapa en vivo y avisa cuando el repartidor llega a un sitio.", "La posición de cada repartidor (en memoria).", CYAN, CYAN_T),
        card(6, "El navegador  ·  routing-eta-service", "Calcula la ruta por las calles y cuánto falta. Si no puede, hace una estimación.", "Rutas ya calculadas (caché de 20 s).", PINK, PINK_T),
        card(7, "El cartero  ·  notification-service", "Manda el correo al cliente. Si falla, lo reintenta tres veces y luego lo aparta.", "Los correos enviados y sus intentos.", AMBER, AMBER_T),
        Spacer(1, 2),
        callout(
            "Truco para recordarlo",
            "Portero → Recepcionista → Jefe de pedidos → Coordinador → Rastreador → Navegador → Cartero. "
            "Es el mismo orden en el que viaja un pedido.",
            PURPLE,
            PURPLE_T,
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------- 3. dos formas de hablar
    story += [
        section(3, "Dos formas de hablar", CYAN),
        p("Los personajes se comunican de dos maneras. Confundirlas es el error número uno."),
        Spacer(1, 6),
        Table(
            [
                [
                    Paragraph("LLAMADA DIRECTA (HTTP)", style("h", 10, 13, BLUE, bold=True)),
                    Paragraph("PAPELITO EN EL TABLÓN (Kafka)", style("h2", 10, 13, ORANGE, bold=True)),
                ],
                [
                    Paragraph(
                        "“Oye, ¿cuánto falta?”<br/><br/>Preguntas y <b>esperas</b> la respuesta. "
                        "Sirve para cuando necesitas el dato <b>ya</b>: el ETA, el carné, el estado.",
                        style("a1", 9.5, 13, SOFT),
                    ),
                    Paragraph(
                        "“Oye, ha pasado esto.”<br/><br/>Dejas un aviso y <b>sigues a lo tuyo</b>. "
                        "Sirve para avisar sin que el otro esté disponible: pedido creado, pedido asignado.",
                        style("a2", 9.5, 13, SOFT),
                    ),
                ],
                [
                    Paragraph("Ejemplo: el mapa pregunta “¿cuánto falta?”", TINY),
                    Paragraph("Ejemplo: el jefe de pedidos avisa “acabo de crear uno”", TINY),
                ],
            ],
            colWidths=[WIDTH / 2 - 5, WIDTH / 2 - 5],
            style=TableStyle(
                [
                    ("VALIGN", (0, 0), (-1, -1), "TOP"),
                    ("BACKGROUND", (0, 0), (0, -1), BLUE_T),
                    ("BACKGROUND", (1, 0), (1, -1), ORANGE_T),
                    ("BOX", (0, 0), (0, -1), 1, BLUE),
                    ("BOX", (1, 0), (1, -1), 1, ORANGE),
                    ("ROUNDEDCORNERS", [10, 10, 10, 10]),
                    ("LEFTPADDING", (0, 0), (-1, -1), 12),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 12),
                    ("TOPPADDING", (0, 0), (-1, -1), 10),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 10),
                ]
            ),
        ),
        Spacer(1, 12),
        callout(
            "La regla del proyecto",
            "Si necesitas una respuesta para continuar → <b>llamada directa</b>. "
            "Si solo estás contando algo que pasó → <b>papelito</b>. "
            "Por eso el pedido no “llama” al coordinador: le deja un papelito y se desentiende.",
            CYAN,
            CYAN_T,
        ),
        Spacer(1, 4),
        p("<b>¿Y si el tablón está lleno o el otro no mira?</b>", style("q2", 11, 15, INK)),
        p(
            "Los papelitos se guardan en orden y no se borran al leerlos. Si el coordinador estaba "
            "reiniciándose, al volver lee los que se perdió. Eso se llama <b>evento</b>, y el "
            "“tablón” es <b>Kafka</b>."
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------- 4. viaje 1
    story += [
        section(4, "El viaje del pedido · parte 1: nace", GREEN),
        p("El cliente pulsa “crear envío”. Esto es lo que pasa en menos de un segundo."),
        Spacer(1, 6),
        flow([("Cliente", BLUE_T), ("Portero", PURPLE_T), ("Jefe de pedidos", GREEN_T), ("Papelito", ORANGE_T)], GREEN),
        step(1, "El portero revisa el carné", "Sin carné válido, no se pasa. El carné es un JWT: un texto firmado que dice quién eres y qué puedes hacer.", PURPLE),
        step(2, "El jefe de pedidos anota en su libreta", "Guarda el envío con estado <b>CREATED</b> y apunta la hora. Ese estado es la verdad oficial del pedido.", GREEN),
        step(3, "En la misma anotación, deja un papelito", "Escribe “he creado este pedido” en el tablón. Esto es clave: <b>si falla una cosa, falla la otra</b>. Nunca hay pedido sin aviso.", ORANGE),
        step(4, "El coordinador recoge el papelito", "Se entera de que hay un pedido nuevo y empieza a buscar repartidor. El jefe de pedidos ya se olvidó: sigue con lo suyo.", ORANGE),
        Spacer(1, 6),
        callout(
            "La palabra rara: OUTBOX",
            "Ese “anotar y dejar el papelito en la misma operación” se llama <b>outbox</b>. "
            "Es como escribir la carta y meterla en el sobre antes de levantarte de la silla: "
            "o haces las dos cosas, o no haces ninguna.",
            GREEN,
            GREEN_T,
        ),
        callout(
            "Ojo",
            "Si publicaras el papelito <b>antes</b> de guardar el pedido y algo fallara, el coordinador "
            "buscaría un pedido que no existe. Si lo publicaras <b>después</b> y el programa se "
            "apagara en medio, el pedido existiría pero nadie lo sabría. Por eso van juntos.",
            RED,
            RED_T,
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------- 5. viaje 2
    story += [
        section(5, "El viaje · parte 2: alguien lo acepta", ORANGE),
        p("El coordinador tiene que elegir a un repartidor… y evitar que dos acepten el mismo pedido."),
        Spacer(1, 6),
        step(1, "Mira quién está cerca", "Tiene un mapa en memoria (Redis) con la posición de los repartidores libres. Pregunta: “¿quién está a menos de 1,5 km?”.", ORANGE),
        step(2, "Ordena los candidatos", "70 % cercanía + 30 % calificación. El más cercano no siempre gana: uno con mejor nota puede adelantar.", ORANGE),
        step(3, "Manda una oferta con reloj", "La oferta dura <b>90 segundos</b>. Si nadie la toma, se la manda al siguiente. Tres candidatos por ronda, y tres rondas.", ORANGE),
        step(4, "El repartidor acepta… y aquí está el truco", "Tres candados evitan que dos personas se queden con el mismo pedido.", RED),
        Spacer(1, 8),
        p("<b>Los tres candados</b>", style("c3", 12, 15, RED, bold=True)),
        Table(
            [
                [
                    Paragraph("<b>1. Un candado en Redis</b><br/>“El primero que llega pone el candado; el resto se queda fuera.”", style("k1", 9.2, 12.5, INK)),
                    Paragraph("<b>2. Una condición en la base</b><br/>“Cámbialo a ACEPTADA <b>solo si sigue libre</b>.”", style("k2", 9.2, 12.5, INK)),
                    Paragraph("<b>3. Una regla en la tabla</b><br/>“Un pedido no puede tener dos asignaciones activas. Punto.”", style("k3", 9.2, 12.5, INK)),
                ]
            ],
            colWidths=[WIDTH / 3 - 4] * 3,
            style=TableStyle(
                [
                    ("VALIGN", (0, 0), (-1, -1), "TOP"),
                    ("BACKGROUND", (0, 0), (-1, -1), RED_T),
                    ("BOX", (0, 0), (-1, -1), 1, RED),
                    ("INNERGRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#fca5a5")),
                    ("LEFTPADDING", (0, 0), (-1, -1), 9),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 9),
                    ("TOPPADDING", (0, 0), (-1, -1), 9),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 9),
                ]
            ),
        ),
        Spacer(1, 8),
        callout(
            "Lo probamos de verdad",
            "Lanzamos <b>50 peticiones a la vez</b> aceptando la misma oferta. Resultado: "
            "<b>1 ganó y 49 recibieron “ya está tomada”</b>. Si quitas un candado, el test falla.",
            RED,
            RED_T,
        ),
        p(
            "Cuando alguien gana, el coordinador deja otro papelito: “asignado”. "
            "El jefe de pedidos lo lee y cambia el estado a <b>ASSIGNED</b>. "
            "El coordinador <b>no</b> tocó la libreta del jefe: solo avisó."
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------- 6. viaje 3
    story += [
        section(6, "El viaje · parte 3: el mapa y la entrega", CYAN),
        p("El repartidor ya tiene el pedido. Ahora hay que verlo moverse… y saber cuándo llega."),
        Spacer(1, 6),
        step(1, "El móvil manda su posición", "Cada 2 segundos, o cada 5 metros. El servidor responde <b>202</b> al instante: “recibido, sigue” (no espera a procesarlo).", CYAN),
        step(2, "El rastreador lo pinta", "Guarda la última posición y avisa por WebSocket. El mapa del cliente se mueve <b>sin recargar</b>.", CYAN),
        step(3, "La geocerca decide", "Si el repartidor está a menos de 100 m del origen <b>tres veces seguidas</b>, el pedido pasa a <b>PICKED_UP</b>. ¿Por qué tres? Porque el GPS tiembla ±30 m.", GREEN),
        step(4, "Se aleja y llega", "A más de 150 m del origen → <b>IN_TRANSIT</b>. A menos de 150 m del destino, tres veces → <b>DELIVERED</b>.", GREEN),
        step(5, "El cartero avisa", "El jefe de pedidos deja un papelito “manda el correo”. El cartero lo envía y lo apunta en su libreta.", AMBER),
        Spacer(1, 6),
        callout(
            "¿Por qué el estado no lo cambia una persona?",
            "Porque nadie tiene que “marcar entregado” a mano. El sistema mira el GPS, y cuando se cumplen "
            "las condiciones, el estado cambia solo. Si el GPS falla, el repartidor puede forzarlo, pero el "
            "camino normal es automático.",
            CYAN,
            CYAN_T,
        ),
        callout(
            "La histéresis, con plastilina",
            "Imagina que el GPS es un niño con un lápiz tembloroso: a veces dibuja dentro del círculo, a veces "
            "fuera. Si hicieras caso al primer trazo, el pedido cambiaría de estado diez veces. "
            "<b>Pedimos tres trazos seguidos dentro</b> y así estamos seguros.",
            PURPLE,
            PURPLE_T,
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------- 7. reglas
    story += [
        section(7, "Las 4 reglas de oro", RED),
        p("Si te aprendes esto, puedes defender el proyecto delante de cualquiera."),
        Spacer(1, 4),
        card(1, "Cada uno su libreta", "El jefe de pedidos no escribe en la libreta del coordinador. Ni al revés. Si necesitan algo, se lo piden o se avisan.", "Así nadie pisa lo del otro.", GREEN, GREEN_T),
        card(2, "Nunca se pierde un papelito", "El aviso se guarda en la misma operación que el cambio. Si el tablón está caído, se reintenta hasta publicarlo.", "Outbox + reintentos.", ORANGE, ORANGE_T),
        card(3, "Si llega dos veces, se hace una", "Cada papelito lleva un número único. Si el mismo número vuelve a llegar, se ignora. Y si el cliente reintenta su petición, se le devuelve la misma respuesta.", "Idempotencia.", BLUE, BLUE_T),
        card(4, "Solo uno puede aceptar", "Tres candados: Redis, la condición en la base y la regla de la tabla.", "Nunca dos repartidores al mismo pedido.", RED, RED_T),
        Spacer(1, 8),
        callout(
            "La frase que lo resume",
            "“Cada servicio es dueño de su dato, avisa de lo que pasa y aguanta que el aviso llegue dos veces.”",
            INK,
            colors.HexColor("#f1f5f9"),
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------- 8. fallos
    story += [
        section(8, "Cuando algo falla (y va a fallar)", PINK),
        p("Un proyecto real no es el que nunca falla: es el que sabe qué hacer cuando falla."),
        Spacer(1, 6),
        card("!", "El repartidor abandona", "El coordinador avisa “se cayó la asignación”. El jefe de pedidos devuelve el pedido a CREATED y el coordinador vuelve a buscar. Nadie se queda colgado.", "Compensación (saga).", ORANGE, ORANGE_T),
        card("!", "El correo no sale", "El cartero apunta el intento y reintenta a los 2 s, 10 s y 30 s. Si se agotan, lo deja en una bandeja de fallos para revisarlo a mano.", "Reintentos + DLQ.", AMBER, AMBER_T),
        card("!", "El calculador de rutas se cae", "En vez de dejar al cliente sin respuesta, responde una estimación en línea recta y lo dice: “esto es aproximado”.", "Fallback.", PINK, PINK_T),
        card("!", "El mismo aviso llega dos veces", "Se compara el número del papelito con los ya procesados. Si está repetido, se descarta.", "Deduplicación.", BLUE, BLUE_T),
        Spacer(1, 4),
        callout(
            "La palabra rara: SAGA",
            "Una saga es una historia con marcha atrás. Si un paso falla, se deshacen los anteriores "
            "con avisos: “oye, esto ya no”. No hay botón de “deshacer” mágico; hay mensajes que compensan.",
            ORANGE,
            ORANGE_T,
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------- 9. glosario
    story += [
        section(9, "Glosario con plastilina", PURPLE),
        p("Las palabras que vas a oír, traducidas a lenguaje normal."),
        Spacer(1, 6),
        Table(
            [
                [Paragraph("<b>Palabra</b>", WHITE), Paragraph("<b>Qué significa de verdad</b>", WHITE)],
                [Paragraph("Microservicio", BODY), Paragraph("Programa pequeño con un solo trabajo y su propia libreta.", BODY)],
                [Paragraph("Evento", BODY), Paragraph("Aviso de algo que ya pasó: “se creó el pedido”.", BODY)],
                [Paragraph("Kafka", BODY), Paragraph("El tablón de avisos. Guarda los papelitos en orden y no los borra al leerlos.", BODY)],
                [Paragraph("Outbox", BODY), Paragraph("Anotar el cambio y el aviso en la misma operación, para no perder ninguno.", BODY)],
                [Paragraph("Idempotencia", BODY), Paragraph("Hacer lo mismo dos veces tiene el mismo efecto que hacerlo una.", BODY)],
                [Paragraph("Saga", BODY), Paragraph("Serie de pasos con marcha atrás: si uno falla, los anteriores se compensan.", BODY)],
                [Paragraph("Caché", BODY), Paragraph("Guardar una respuesta reciente para no volver a calcularla.", BODY)],
                [Paragraph("Geocerca", BODY), Paragraph("Un círculo en el mapa. Si entras, pasa algo.", BODY)],
                [Paragraph("JWT", BODY), Paragraph("Tu carné digital: dice quién eres y caduca.", BODY)],
                [Paragraph("DLQ", BODY), Paragraph("La bandeja de los mensajes que fallaron demasiadas veces.", BODY)],
                [Paragraph("WebSocket", BODY), Paragraph("Una línea abierta para que el servidor te hable sin que preguntes.", BODY)],
            ],
            colWidths=[110, WIDTH - 110],
            style=TableStyle(
                [
                    ("BACKGROUND", (0, 0), (-1, 0), PURPLE),
                    ("TEXTCOLOR", (0, 0), (-1, 0), PAPER),
                    ("ROWBACKGROUNDS", (0, 1), (-1, -1), [PAPER, colors.HexColor("#faf5ff")]),
                    ("GRID", (0, 0), (-1, -1), 0.4, LINE),
                    ("VALIGN", (0, 0), (-1, -1), "TOP"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 8),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 8),
                    ("TOPPADDING", (0, 0), (-1, -1), 6),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
                ]
            ),
        ),
        PageBreak(),
    ]

    # ---------------------------------------------------------- 10. mapa + examen
    story += [
        section(10, "Mapa mental y mini examen", CYAN),
        p("<b>El mapa, de un vistazo</b>"),
        Spacer(1, 6),
        Table(
            [
                [Paragraph("<b>#</b>", WHITE), Paragraph("<b>Quién</b>", WHITE), Paragraph("<b>A quién</b>", WHITE), Paragraph("<b>Cómo</b>", WHITE)],
                [Paragraph("1", BODY), Paragraph("Cliente", BODY), Paragraph("Portero", BODY), Paragraph("HTTP", TINY)],
                [Paragraph("2", BODY), Paragraph("Portero", BODY), Paragraph("Jefe de pedidos", BODY), Paragraph("HTTP", TINY)],
                [Paragraph("3", BODY), Paragraph("Jefe de pedidos", BODY), Paragraph("Coordinador", BODY), Paragraph("papelito (Kafka)", TINY)],
                [Paragraph("4", BODY), Paragraph("Coordinador", BODY), Paragraph("Repartidor", BODY), Paragraph("oferta", TINY)],
                [Paragraph("5", BODY), Paragraph("Repartidor", BODY), Paragraph("Rastreador", BODY), Paragraph("GPS", TINY)],
                [Paragraph("6", BODY), Paragraph("Rastreador", BODY), Paragraph("Mapa del cliente", BODY), Paragraph("WebSocket", TINY)],
                [Paragraph("7", BODY), Paragraph("Jefe de pedidos", BODY), Paragraph("Cartero", BODY), Paragraph("papelito → correo", TINY)],
            ],
            colWidths=[26, 118, 150, WIDTH - 294],
            style=TableStyle(
                [
                    ("BACKGROUND", (0, 0), (-1, 0), CYAN),
                    ("TEXTCOLOR", (0, 0), (-1, 0), PAPER),
                    ("ROWBACKGROUNDS", (0, 1), (-1, -1), [PAPER, colors.HexColor("#ecfeff")]),
                    ("GRID", (0, 0), (-1, -1), 0.4, LINE),
                    ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 7),
                    ("TOPPADDING", (0, 0), (-1, -1), 5),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
                ]
            ),
        ),
        Spacer(1, 12),
        p("<b>Cinco preguntas para comprobar que lo entendiste</b>"),
        Spacer(1, 2),
        bullets(
            [
                "1. ¿Por qué el jefe de pedidos no llama directamente al coordinador?",
                "2. ¿Qué dos cosas se escriben juntas en la misma operación, y por qué?",
                "3. Un repartidor acepta y, un segundo después, otro acepta la misma oferta. ¿Qué pasa?",
                "4. ¿Por qué la geocerca pide tres pings y no uno?",
                "5. El correo falla. ¿Qué hace el sistema y cuándo se rinde?",
            ],
            CYAN,
        ),
        Spacer(1, 8),
        callout(
            "Respuestas",
            "1) Porque así no depende de que el otro esté disponible; le deja un aviso y sigue. · "
            "2) El cambio y el aviso (outbox), para que no exista uno sin el otro. · "
            "3) Solo uno gana: el candado de Redis y la condición de la base rechazan al segundo. · "
            "4) Porque el GPS tiembla; tres pings seguidos evitan falsos positivos. · "
            "5) Reintenta con espera creciente; tras tres intentos lo manda a la bandeja de fallos (DLQ).",
            GREEN,
            GREEN_T,
        ),
        Spacer(1, 10),
        p(
            "<i>Fin de la guía. Si puedes contar estos diez bloques con tus palabras, puedes explicar "
            "el proyecto sin abrir una terminal.</i>",
            style("fin", 10, 14, MUTED, align=TA_CENTER),
        ),
    ]

    doc.build(story)
    print(f"PDF generado: {OUTPUT}")


if __name__ == "__main__":
    build()
