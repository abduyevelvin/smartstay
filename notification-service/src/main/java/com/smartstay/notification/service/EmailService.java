package com.smartstay.notification.service;

import com.smartstay.notification.event.BookingCancelledEvent;
import com.smartstay.notification.event.BookingConfirmedEvent;

public interface EmailService {

    void sendBookingConfirmation(BookingConfirmedEvent event);

    void sendBookingCancellation(BookingCancelledEvent event);
}
