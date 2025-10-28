package com.flightsentinel.ports;

import com.flightsentinel.domain.Flight;

public interface FlightRepository {
    Flight findLatest(String flightId);
    void upsert(Flight flight);
}
