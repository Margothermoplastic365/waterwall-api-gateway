package com.gateway.notification.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    @Test
    void sendEmail_sendsMessageWithCorrectDetails() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/welcome"), any(Context.class)))
                .thenReturn("<html>Welcome!</html>");

        Map<String, Object> variables = Map.of("name", "John");

        emailService.sendEmail("john@example.com", "welcome", variables);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendEmail_usesSubjectFromVariablesIfPresent() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class)))
                .thenReturn("<html>Body</html>");

        Map<String, Object> variables = Map.of("subject", "Custom Subject");

        emailService.sendEmail("test@example.com", "generic", variables);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendEmail_usesDefaultSubjectByTemplateName() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/email-verification"), any(Context.class)))
                .thenReturn("<html>Verify</html>");

        emailService.sendEmail("test@example.com", "email-verification", null);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendEmail_nullVariables_handledGracefully() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/password-reset"), any(Context.class)))
                .thenReturn("<html>Reset</html>");

        emailService.sendEmail("test@example.com", "password-reset", null);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendEmail_messagingException_doesNotPropagate() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class)))
                .thenReturn("<html>Body</html>");
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        // Should not throw
        emailService.sendEmail("test@example.com", "welcome", Map.of());

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendEmail_templateEngineThrows_doesNotPropagate() {
        when(templateEngine.process(anyString(), any(Context.class)))
                .thenThrow(new RuntimeException("Template not found"));

        // Should not throw
        emailService.sendEmail("test@example.com", "nonexistent", Map.of());

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendEmail_passesVariablesToTemplateContext() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("email/welcome"), ctxCaptor.capture()))
                .thenReturn("<html>Hello</html>");

        Map<String, Object> vars = Map.of("name", "Alice", "code", "12345");

        emailService.sendEmail("alice@example.com", "welcome", vars);

        Context capturedCtx = ctxCaptor.getValue();
        assertThat(capturedCtx.getVariable("name")).isEqualTo("Alice");
        assertThat(capturedCtx.getVariable("code")).isEqualTo("12345");
    }

    @Test
    void sendEmail_unknownTemplate_usesGenericSubject() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/some-unknown"), any(Context.class)))
                .thenReturn("<html>Unknown</html>");

        emailService.sendEmail("test@example.com", "some-unknown", Map.of());

        verify(mailSender).send(mimeMessage);
    }
}
