package com.evcharger.evchargingsystem.service;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class ConcurrentIntervalTreeService {

    // Dedicated IntervalTree per charging bay (prevents bay contention & false cross-bay overlaps)
    private final ConcurrentHashMap<String, IntervalTree> bayTrees = new ConcurrentHashMap<>();
    
    // Lock Striping: Dedicated ReentrantReadWriteLock per bay
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> bayLocks = new ConcurrentHashMap<>();

    private IntervalTree getTreeForBay(String bayId) {
        return bayTrees.computeIfAbsent(bayId, k -> new IntervalTree());
    }

    private ReentrantReadWriteLock getLockForBay(String bayId) {
        return bayLocks.computeIfAbsent(bayId, k -> new ReentrantReadWriteLock());
    }

    /**
     * Reserve a slot safely for a specific bay.
     * Only locks the targeted bay, allowing parallel bookings across different bays.
     */
    public boolean reserveSlot(Instant start, Instant end, String reservationId, String bayId) {
        ReentrantReadWriteLock lock = getLockForBay(bayId);
        lock.writeLock().lock();
        try {
            IntervalTree tree = getTreeForBay(bayId);
            if (tree.isOverlapping(start, end)) {
                return false; // Overlap detected on this bay
            }
            tree.insert(start, end, reservationId, bayId);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Read-only operation: multiple threads can query bay availability concurrently.
     */
    public boolean checkAvailability(Instant start, Instant end, String bayId) {
        ReentrantReadWriteLock lock = getLockForBay(bayId);
        lock.readLock().lock();
        try {
            IntervalTree tree = getTreeForBay(bayId);
            return !tree.isOverlapping(start, end);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Cancel an existing reservation to liberate the slot.
     */
    public boolean cancelReservation(String bayId, String reservationId) {
        ReentrantReadWriteLock lock = getLockForBay(bayId);
        lock.writeLock().lock();
        try {
            IntervalTree tree = getTreeForBay(bayId);
            return tree.delete(reservationId);
        } finally {
            lock.writeLock().unlock();
        }
    }
}