package com.flightsentinel.dto;

public record FlightStatusDto(String flight,
                              String origin,
                              String destination,
                              String status,
                              Integer delayMinutes) {}
