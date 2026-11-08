package com.victory.apartment.repository;

/** A bill as reports read it, without the slip and scanned document stored on the entity. */
public record BillReportRow(
    String id,
    String leaseId,
    String roomId,
    String roomNumber,
    String tenantName,
    String billingMonth,
    Double rentAmount,
    Double waterAmount,
    Double electricAmount,
    Double commonFee,
    Double totalAmount,
    String status,
    String leaseTenantId,
    String leaseTenantName,
    String buildingId
) {}
