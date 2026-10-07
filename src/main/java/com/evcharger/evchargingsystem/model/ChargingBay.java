package com.evcharger.evchargingsystem.model;

public class ChargingBay {
    private final String bayId;
    private final double maxPowerKw; // e.g., 50.0 kW Fast DC or 22.0 kW AC
    private volatile BayStatus status;

    public ChargingBay(String bayId, double maxPowerKw) {
        this.bayId = bayId;
        this.maxPowerKw = maxPowerKw;
        this.status = BayStatus.AVAILABLE;
    }

    public String getBayId() { return bayId; }
    public double getMaxPowerKw() { return maxPowerKw; }
    public BayStatus getStatus() { return status; }
    public void setStatus(BayStatus status) { this.status = status; }
}