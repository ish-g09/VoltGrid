package com.evcharger.evchargingsystem.controller;

import com.evcharger.evchargingsystem.dto.ReservationRequest;
import com.evcharger.evchargingsystem.model.BayStatus;
import com.evcharger.evchargingsystem.model.ChargingBay;
import com.evcharger.evchargingsystem.model.ChargingSession;
import com.evcharger.evchargingsystem.observer.UserNotificationObserver;
import com.evcharger.evchargingsystem.service.ConcurrentIntervalTreeService;
import com.evcharger.evchargingsystem.service.GridLoadBalancerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/v1/ev")
public class EVStationController {

    private final ConcurrentIntervalTreeService treeService;
    private final GridLoadBalancerService loadBalancerService;

    // Dedicated worker pool for background charging simulations
    private final ExecutorService chargingWorkerPool = Executors.newFixedThreadPool(5);

    // Track station charging bays (OOP domain state)
    private final Map<String, ChargingBay> bays = new ConcurrentHashMap<>();

    // Track active charging sessions: sessionId -> ChargingSession
    private final Map<String, ChargingSession> activeSessionsMap = new ConcurrentHashMap<>();

    public EVStationController(ConcurrentIntervalTreeService treeService, GridLoadBalancerService loadBalancerService) {
        this.treeService = treeService;
        this.loadBalancerService = loadBalancerService;

        // Initialize default station bays
        bays.put("BAY-1", new ChargingBay("BAY-1", 50.0)); // 50 kW Fast DC
        bays.put("BAY-2", new ChargingBay("BAY-2", 50.0)); // 50 kW Fast DC
        bays.put("BAY-3", new ChargingBay("BAY-3", 22.0)); // 22 kW AC
    }

    @PostMapping("/reserve")
    public ResponseEntity<String> reserveSlot(@RequestBody ReservationRequest request) {
        Instant start = Instant.parse(request.getStartTimeIso());
        Instant end = Instant.parse(request.getEndTimeIso());

        boolean booked = treeService.reserveSlot(start, end, request.getReservationId(), request.getBayId());

        if (!booked) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("[CONFLICT] Slot booking failed: Requested interval overlaps with an existing reservation on " + request.getBayId());
        }

        return ResponseEntity.ok("[SUCCESS] Slot reserved for ID: " + request.getReservationId() + " on " + request.getBayId());
    }

    @PostMapping("/cancel")
    public ResponseEntity<String> cancelReservation(@RequestParam String bayId, @RequestParam String reservationId) {
        boolean cancelled = treeService.cancelReservation(bayId, reservationId);

        if (!cancelled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("[NOT FOUND] No reservation found with ID: " + reservationId + " on " + bayId);
        }

        return ResponseEntity.ok("[SUCCESS] Reservation " + reservationId + " cancelled and slot liberated on " + bayId);
    }

    @PostMapping("/start-session")
    public ResponseEntity<String> startSession(@RequestParam String sessionId, @RequestParam String bayId, @RequestParam int initialSoc) {
        ChargingBay bay = bays.get(bayId);
        if (bay != null) {
            bay.setStatus(BayStatus.CHARGING);
        }

        ChargingSession session = new ChargingSession(sessionId, initialSoc, List.of(new UserNotificationObserver()));
        activeSessionsMap.put(sessionId, session);

        // Submit to thread pool (non-blocking)
        chargingWorkerPool.submit(session);

        return ResponseEntity.ok("[ASYNC] Session " + sessionId + " started on " + bayId + " in worker pool.");
    }

    @GetMapping("/bays")
    public ResponseEntity<Collection<ChargingBay>> getBays() {
        return ResponseEntity.ok(bays.values());
    }

    @GetMapping("/grid-status")
    public ResponseEntity<Map<String, Double>> getGridStatus() {
        Map<String, Integer> currentSocMap = new HashMap<>();

        for (Map.Entry<String, ChargingSession> entry : activeSessionsMap.entrySet()) {
            if (entry.getValue().isActive()) {
                currentSocMap.put(entry.getKey(), entry.getValue().getBatterySoc());
            }
        }

        return ResponseEntity.ok(loadBalancerService.calculatePowerAllocation(currentSocMap));
    }
}