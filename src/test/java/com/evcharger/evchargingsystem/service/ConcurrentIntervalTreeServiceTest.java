package com.evcharger.evchargingsystem.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentIntervalTreeServiceTest {

    private ConcurrentIntervalTreeService service;

    @BeforeEach
    void setUp() {
        service = new ConcurrentIntervalTreeService();
    }

    @Test
    void testConcurrentBookingsSameBayPreventDoubleBooking() throws InterruptedException {
        int numberOfThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successfulBookings = new AtomicInteger(0);

        Instant start = Instant.parse("2026-08-16T10:00:00Z");
        Instant end = Instant.parse("2026-08-16T12:00:00Z");

        for (int i = 0; i < numberOfThreads; i++) {
            final String resId = "RES-" + i;
            executor.submit(() -> {
                try {
                    latch.await();
                    boolean booked = service.reserveSlot(start, end, resId, "BAY-1");
                    if (booked) {
                        successfulBookings.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Exactly ONE thread should win the race on BAY-1
        assertEquals(1, successfulBookings.get(), "Zero double-booking guarantee breached!");
    }

    @Test
    void testConcurrentBookingsDifferentBaysSucceedSimultaneously() {
        Instant start = Instant.parse("2026-08-16T10:00:00Z");
        Instant end = Instant.parse("2026-08-16T12:00:00Z");

        // Both bays must be bookable for the same time window without blocking each other
        boolean bookedBay1 = service.reserveSlot(start, end, "RES-BAY1", "BAY-1");
        boolean bookedBay2 = service.reserveSlot(start, end, "RES-BAY2", "BAY-2");

        assertTrue(bookedBay1, "Bay 1 reservation should succeed");
        assertTrue(bookedBay2, "Bay 2 reservation should succeed simultaneously");
    }

    @Test
    void testCancelReservationFreesSlot() {
        Instant start = Instant.parse("2026-08-16T14:00:00Z");
        Instant end = Instant.parse("2026-08-16T16:00:00Z");

        assertTrue(service.reserveSlot(start, end, "RES-CANCEL", "BAY-1"));
        assertFalse(service.checkAvailability(start, end, "BAY-1"));

        // Cancel the booking
        assertTrue(service.cancelReservation("BAY-1", "RES-CANCEL"));

        // Slot should now be free
        assertTrue(service.checkAvailability(start, end, "BAY-1"));
    }
}