package com.flightsentinel.dto;

public record ErrorResponse(String code, String message, String correlationId) {}
