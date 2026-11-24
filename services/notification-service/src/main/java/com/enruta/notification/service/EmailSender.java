package com.enruta.notification.service;

import com.enruta.notification.domain.Notification;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Envio real por SMTP (MailHog en local). */
@Component
public class EmailSender {

    private final JavaMailSender mailSender;
    private final TemplateRenderer templates;

    public EmailSender(JavaMailSender mailSender, TemplateRenderer templates) {
        this.mailSender = mailSender;
        this.templates = templates;
    }

    public void send(Notification notification) throws Exception {
        if (!"EMAIL".equals(notification.getChannel())) {
            throw new IllegalArgumentException("Canal no soportado: " + notification.getChannel());
        }
        TemplateRenderer.Rendered rendered =
                templates.render(notification.getTemplate(), notification.getVariables());

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        helper.setTo(notification.getRecipient());
        helper.setFrom("no-reply@enruta.test");
        helper.setSubject(rendered.subject());
        helper.setText(rendered.body(), false);
        mailSender.send(message);
    }
}
