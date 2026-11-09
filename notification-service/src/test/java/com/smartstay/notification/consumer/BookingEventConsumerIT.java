package com.smartstay.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import com.smartstay.notification.event.BookingCancelledEvent;
import com.smartstay.notification.event.BookingConfirmedEvent;
import com.smartstay.notification.service.EmailService;

/**
 * Full-stack integration test for the Kafka consumer layer.
 *
 * <p>Starts the full Spring context with:
 * <ul>
 *   <li>{@code @EmbeddedKafka} — in-process Kafka broker</li>
 *   <li>{@code @MockBean EmailService} — no real SMTP connection needed</li>
 * </ul>
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"booking.confirmed", "booking.cancelled"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@ActiveProfiles("test")
class BookingEventConsumerIT {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private EmailService emailService;

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void onBookingConfirmed_consumesEvent_callsEmailService() throws InterruptedException {
        var latch = new CountDownLatch(1);
        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(emailService)
          .sendBookingConfirmation(any());

        var event = confirmedEvent();
        kafkaTemplate.send("booking.confirmed", event.bookingId()
                                                     .toString(), event);

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        verify(emailService).sendBookingConfirmation(event);
    }

    @Test
    void onBookingCancelled_consumesEvent_callsEmailService() throws InterruptedException {
        var latch = new CountDownLatch(1);
        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(emailService)
          .sendBookingCancellation(any());

        var event = cancelledEvent();
        kafkaTemplate.send("booking.cancelled", event.bookingId()
                                                     .toString(), event);

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        verify(emailService).sendBookingCancellation(event);
    }

    // ── retry ─────────────────────────────────────────────────────────────────

    @Test
    void onBookingConfirmed_emailFailsOnce_retriesAndSucceeds() throws InterruptedException {
        var latch = new CountDownLatch(1);
        var callCount = new AtomicInteger(0);

        doAnswer(inv -> {
            if (callCount.incrementAndGet() == 1) {
                throw new RuntimeException("SMTP temporarily down");
            }
            latch.countDown();
            return null;
        }).when(emailService)
          .sendBookingConfirmation(any());

        var event = confirmedEvent();
        kafkaTemplate.send("booking.confirmed", event.bookingId()
                                                     .toString(), event);

        // Allow enough time for the retry (2s backoff configured in @RetryableTopic)
        assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
        verify(emailService, times(2)).sendBookingConfirmation(any());
    }

    @Test
    void onBookingCancelled_emailFailsOnce_retriesAndSucceeds() throws InterruptedException {
        var latch = new CountDownLatch(1);
        var callCount = new AtomicInteger(0);

        doAnswer(inv -> {
            if (callCount.incrementAndGet() == 1) {
                throw new RuntimeException("SMTP temporarily down");
            }
            latch.countDown();
            return null;
        }).when(emailService)
          .sendBookingCancellation(any());

        var event = cancelledEvent();
        kafkaTemplate.send("booking.cancelled", event.bookingId()
                                                     .toString(), event);

        assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
        verify(emailService, times(2)).sendBookingCancellation(any());
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
