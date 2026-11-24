package com.enruta.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Plantillas de correo.
 *
 * <p>Se resuelven en codigo a proposito: para tres plantillas, un motor de plantillas solo añade
 * superficie de ataque y dependencias. Si el catalogo crece, se migra a Thymeleaf.
 */
@Component
public class TemplateRenderer {

    public record Rendered(String subject, String body) {}

    private final ObjectMapper objectMapper;

    public TemplateRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Rendered render(String template, String variablesJson) {
        JsonNode variables = read(variablesJson);
        return switch (template) {
            case "SHIPMENT_DELIVERED" ->
                    new Rendered(
                            "Tu envio fue entregado",
                            """
              Hola,

              Tu envio llego a destino: %s
              Envio: %s

              Gracias por usar EnRuta.
              """
                                    .formatted(
                                            variables.path("dropoffAddress").asText("tu direccion"),
                                            variables.path("shipmentId").asText("-")));
            case "TEST" ->
                    new Rendered(
                            "Notificacion de prueba",
                            "Este es un correo de prueba enviado por EnRuta para verificar la configuracion.");
            default ->
                    new Rendered(
                            "EnRuta: " + template,
                            "Evento "
                                    + template
                                    + " sobre el envio "
                                    + variables.path("shipmentId").asText());
        };
    }

    public String toJson(Map<String, Object> variables) {
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }
}
