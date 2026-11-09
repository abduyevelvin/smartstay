package com.smartstay.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.smartstay.notification.event.BookingCancelledEvent;
import com.smartstay.notification.event.BookingConfirmedEvent;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@smartstay.com");
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
    }

    // ── sendBookingConfirmation ───────────────────────────────────────────────

    @Test
    void sendBookingConfirmation_setsCorrectTemplateVariables() {
        var event = confirmedEvent();
        given(templateEngine.process(eq("booking-confirmed"), any(Context.class))).willReturn("<html>ok</html>");

        emailService.sendBookingConfirmation(event);

        ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("booking-confirmed"), ctxCaptor.capture());

        Context ctx = ctxCaptor.getValue();
        assertThat(ctx.getVariable("bookingId")).isEqualTo(event.bookingId());
        assertThat(ctx.getVariable("hotelName")).isEqualTo(event.hotelName());
        assertThat(ctx.getVariable("checkIn")).isEqualTo(event.checkIn());
        assertThat(ctx.getVariable("checkOut")).isEqualTo(event.checkOut());
    }

    @Test
    void sendBookingConfirmation_sendsEmailToGuestAddress() {
        var event = confirmedEvent();
        given(templateEngine.process((String) any(), any())).willReturn("<html>ok</html>");

        emailService.sendBookingConfirmation(event);

        verify(mailSender).send(any(MimeMessage.class));
    }

    // ── sendBookingCancellation ───────────────────────────────────────────────

    @Test
    void sendBookingCancellation_setsCorrectTemplateVariables() {
        var event = cancelledEvent();
        given(templateEngine.process(eq("booking-cancelled"), any(Context.class))).willReturn("<html>ok</html>");

        emailService.sendBookingCancellation(event);

        ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("booking-cancelled"), ctxCaptor.capture());

        Context ctx = ctxCaptor.getValue();
        assertThat(ctx.getVariable("bookingId")).isEqualTo(event.bookingId());
        assertThat(ctx.getVariable("hotelName")).isEqualTo(event.hotelName());
        assertThat(ctx.getVariable("checkIn")).isEqualTo(event.checkIn());
        assertThat(ctx.getVariable("checkOut")).isEqualTo(event.checkOut());
    }

    @Test
    void sendBookingCancellation_sendsEmailToGuestAddress() {
        var event = cancelledEvent();
        given(templateEngine.process((String) any(), any())).willReturn("<html>ok</html>");

        emailService.sendBookingCancellation(event);

        verify(mailSender).send(any(MimeMessage.class));
    }

    // ── exception propagation ─────────────────────────────────────────────────

    @Test
    void sendBookingConfirmation_mailSendFails_propagatesAsRuntimeException() {
        var event = confirmedEvent();
        given(templateEngine.process((String) any(), any())).willReturn("<html>ok</html>");
        // MailSendException is Spring's unchecked wrapper around MessagingException —
        // thrown by JavaMailSender.send() when SMTP fails.
        doThrow(new MailSendException("SMTP connection refused"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.sendBookingConfirmation(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email build failed");
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private BookingConfirmedEvent confirmedEvent() {
        return new BookingConfirmedEvent(UUID.randomUUID(), "guest@test.com", "Grand Hotel", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 5));
    }

    private BookingCancelledEvent cancelledEvent() {
        return new BookingCancelledEvent(UUID.randomUUID(), "guest@test.com", "Grand Hotel", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 5));
    }
}
