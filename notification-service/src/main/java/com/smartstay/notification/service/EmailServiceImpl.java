package com.smartstay.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.smartstay.notification.event.BookingCancelledEvent;
import com.smartstay.notification.event.BookingConfirmedEvent;

import jakarta.mail.MessagingException;
import org.springframework.mail.MailException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${notification.mail.from:noreply@smartstay.com}")
    private String fromAddress;

    @Override
    public void sendBookingConfirmation(BookingConfirmedEvent event) {
        log.info("Sending booking confirmation email — bookingId={}, to={}", event.bookingId(), event.guestEmail());

        Context ctx = new Context();
        ctx.setVariable("bookingId", event.bookingId());
        ctx.setVariable("hotelName", event.hotelName());
        ctx.setVariable("checkIn", event.checkIn());
        ctx.setVariable("checkOut", event.checkOut());

        final var html = templateEngine.process("booking-confirmed", ctx);
        sendHtml(event.guestEmail(), "Your booking is confirmed — " + event.hotelName(), html);
    }

    @Override
    public void sendBookingCancellation(BookingCancelledEvent event) {
        log.info("Sending booking cancellation email — bookingId={}, to={}", event.bookingId(), event.guestEmail());

        Context ctx = new Context();
        ctx.setVariable("bookingId", event.bookingId());
        ctx.setVariable("hotelName", event.hotelName());
        ctx.setVariable("checkIn", event.checkIn());
        ctx.setVariable("checkOut", event.checkOut());

        final var html = templateEngine.process("booking-cancelled", ctx);
        sendHtml(event.guestEmail(), "Your booking has been cancelled — " + event.hotelName(), html);
    }

    // ── private ───────────────────────────────────────────────────────────────

    private void sendHtml(String to,
                          String subject,
                          String htmlBody) {
        try {
            final var message = mailSender.createMimeMessage();
            final var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent — to={}, subject=\"{}\"", to, subject);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send email — to={}, subject=\"{}\"", to, subject, e);
            throw new RuntimeException("Email build failed", e);
        }
    }
}
