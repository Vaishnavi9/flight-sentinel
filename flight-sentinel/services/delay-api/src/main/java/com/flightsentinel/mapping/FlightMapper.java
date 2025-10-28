package com.flightsentinel.mapping;

import jakarta.enterprise.context.ApplicationScoped;
import com.flightsentinel.domain.Flight;
import com.flightsentinel.dto.FlightStatusDto;

@ApplicationScoped
public class FlightMapper {
    public FlightStatusDto toDto(Flight f) {
        if (f == null) return null;
        return new FlightStatusDto(f.flight(), f.origin(), f.destination(), f.status(), f.delayMinutes());
    }
}
