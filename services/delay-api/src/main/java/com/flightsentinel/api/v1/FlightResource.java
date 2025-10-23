package com.flightsentinel.api.v1;

import com.flightsentinel.dto.FlightStatusDto;
import com.flightsentinel.mapping.FlightMapper;
import com.flightsentinel.usecase.GetFlightStatus;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Path("/api/v1/flights")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "flights")
public class FlightResource {

    @Inject GetFlightStatus getFlightStatus;
    @Inject FlightMapper mapper;

    @GET
    @Path("/latest")
    @Operation(summary = "Get latest status for a flight",
        description = "Returns current status and delay for a given flight number (IATA/ICAO).")
    @APIResponse(responseCode = "200", description = "OK",
        content = @Content(schema = @Schema(implementation = FlightStatusDto.class)))
    public Response latest(@QueryParam("flight") String flight) {
        var domain = getFlightStatus.execute(flight);
        var dto = mapper.toDto(domain);
        return Response.ok(dto).build();
    }
}
