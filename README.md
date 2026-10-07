# ⚡ Smart EV Charging & Grid Load Management System

A high-performance, concurrent EV Charging Station scheduling and load balancing engine built with **Java 17** and **Spring Boot**.

The system solves two core challenges in EV infrastructure:
1. **Zero Double-Booking Guarantee:** Prevents race conditions during simultaneous slot bookings using an **Augmented Interval Tree** and fine-grained **Lock Striping**.
2. **Grid-Aware Power Distribution:** Dynamically allocates station power using a **two-pass greedy load-balancing algorithm** to prevent substation overloading.

---

## 🏛️ System Architecture

```text
               +----------------------------------+
               |       EVStationController        |
               +-----------------+----------------+
                                 |
         +-----------------------+-----------------------+
         |                                               |
         v                                               v
+-------------------------------+             +-------------------------------+
| ConcurrentIntervalTreeService |             |    GridLoadBalancerService    |
| (Lock Striping per Bay)       |             | (Two-Pass Greedy Allocator)   |
+---------------+---------------+             +---------------+---------------+
                |                                             |
                v                                             v
+-------------------------------+             +-------------------------------+
|     IntervalTree (O(log N))   |             |   Active Charging Sessions    |
|  - Overlap Check: O(log N)    |             |   - Throttling (SoC >= 80%)   |
|  - Reservation Deletion       |             |   - Background Worker Pool    |
+-------------------------------+             +---------------+---------------+
                                                              |
                                                              v
                                              +-------------------------------+
                                              |       ChargingObserver        |
                                              | (Event-driven notifications)  |
                                              +-------------------------------+
```

---

## 🧠 Data Structures & Algorithms (DSA)

### Why an Augmented Interval Tree?
In standard reservation systems, checking whether a time window $[t_{start}, t_{end}]$ overlaps with existing bookings requires an $O(N)$ linear scan across all reservations.

This project implements an **Augmented Interval Tree** (`IntervalNode`):
- **Node Structure:** Each node stores $[start, end]$, along with `maxEnd` (the maximum end time of any interval in its subtree).
- **Subtree Pruning:** During overlap searches, if the left subtree's `maxEnd` is earlier than the query's start time, the entire left branch is pruned in $O(1)$.
- **Time Complexity:**
  - **Overlap Detection:** $O(\log N)$ average case.
  - **Insertion:** $O(\log N)$ with bottom-up `maxEnd` maintenance.
  - **Cancellation (Deletion):** $O(\log N)$ node removal with subtree invariant restoration.

---

## ⚡ Concurrency & Multithreading

- **Lock Striping:** Instead of a single bottleneck lock across the entire station, each charging bay maintains its own `ReentrantReadWriteLock`. Parallel bookings for different bays execute concurrently without contention.
- **Reader-Writer Separation:** Non-blocking availability checks acquire `readLock()`, allowing unlimited simultaneous read queries. Slot reservations acquire `writeLock()`.
- **Stress-Tested Thread Safety:** Verified through automated unit tests simulating 50 threads racing concurrently using `CountDownLatch(1)`—guaranteeing zero double-bookings.
- **Background Worker Threads:** Active charging simulations execute in an asynchronous `ExecutorService` pool without blocking HTTP request threads.

---

## 🎯 Design Patterns & OOP

- **Observer Pattern (`ChargingObserver`):** Decouples battery progression from user alerts. As charging sessions run in worker threads, events trigger when vehicles reach 80% (trickle charging) or 100% completion.
- **Domain Modeling (`ChargingBay` & `BayStatus`):** Clean separation of station infrastructure, tracking power capacity (e.g., 50 kW Fast DC vs. 22 kW AC) and operational states (`AVAILABLE`, `RESERVED`, `CHARGING`, `MAINTENANCE`).

---

## 🔌 REST API Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/ev/reserve` | Reserve a time slot on a specific bay ($O(\log N)$ overlap check) |
| `POST` | `/api/v1/ev/cancel` | Cancel an existing reservation and free the slot |
| `POST` | `/api/v1/ev/start-session` | Launch an asynchronous charging session on a bay |
| `GET` | `/api/v1/ev/bays` | Inspect live status of all charging bays |
| `GET` | `/api/v1/ev/grid-status` | View real-time dynamic kW power allocation across vehicles |

---

## 🛠️ Build & Run

### Run Unit & Concurrency Tests:
```bash
./mvnw test
```

### Start the Application:
```bash
./mvnw spring-boot:run
```
The application will start on `http://localhost:8080`.
