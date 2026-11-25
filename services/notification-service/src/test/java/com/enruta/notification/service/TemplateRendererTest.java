package com.enruta.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer(new ObjectMapper());

    @Test
    void deliveryTemplateUsesTheAddressAndShipment() {
        TemplateRenderer.Rendered rendered =
                renderer.render(
                        "SHIPMENT_DELIVERED",
                        "{\"shipmentId\":\"abc-123\",\"dropoffAddress\":\"Terminal de Transportes\"}");

        assertThat(rendered.subject()).isEqualTo("Tu envio fue entregado");
        assertThat(rendered.body()).contains("Terminal de Transportes").contains("abc-123");
    }

    @Test
    void testTemplateIsFixed() {
        TemplateRenderer.Rendered rendered = renderer.render("TEST", "{}");

        assertThat(rendered.subject()).contains("prueba");
    }

    @Test
    void unknownTemplateFallsBackGracefully() {
        TemplateRenderer.Rendered rendered = renderer.render("ALGO_RARO", "{\"shipmentId\":\"x\"}");

        assertThat(rendered.subject()).contains("ALGO_RARO");
        assertThat(rendered.body()).contains("x");
    }

    @Test
    void invalidJsonDoesNotBreakTheEmail() {
        TemplateRenderer.Rendered rendered = renderer.render("SHIPMENT_DELIVERED", "no-es-json");

        assertThat(rendered.subject()).isNotBlank();
        assertThat(rendered.body()).contains("tu direccion");
    }

    @Test
    void variablesAreSerializedForStorage() {
        assertThat(renderer.toJson(java.util.Map.of("shipmentId", "abc")))
                .contains("shipmentId")
                .contains("abc");
    }
}
