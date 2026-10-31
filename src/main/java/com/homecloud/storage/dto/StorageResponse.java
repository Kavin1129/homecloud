package com.homecloud.storage.dto;

public class StorageResponse {

    private long usedBytes;
    private long limitBytes;
    private long availableBytes;
    private double usedPercentage;

    public StorageResponse(
            long usedBytes,
            long limitBytes,
            long availableBytes,
            double usedPercentage
    ) {
        this.usedBytes = usedBytes;
        this.limitBytes = limitBytes;
        this.availableBytes = availableBytes;
        this.usedPercentage = usedPercentage;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public long getLimitBytes() {
        return limitBytes;
    }

    public long getAvailableBytes() {
        return availableBytes;
    }

    public double getUsedPercentage() {
        return usedPercentage;
    }
}