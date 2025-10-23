package com.flightsentinel.usecase;

import jakarta.enterprise.context.ApplicationScoped;
import com.flightsentinel.domain.Flight;

@ApplicationScoped
public class GetFlightStatus {

    public Flight execute(String flightId) {
        // TODO: wire repository/cache to fetch latest. For now, return dummy data.
        return new Flight(flightId, "CDG", "JFK", "DELAYED", 42);
    }
}
