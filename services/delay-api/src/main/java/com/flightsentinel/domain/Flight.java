package com.flightsentinel.domain;

public record Flight(String flight,
                     String origin,
                     String destination,
                     String status,
                     Integer delayMinutes) {}
