package com.flightsentinel.api.v1;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import com.flightsentinel.dto.ErrorResponse;

@Provider
public class ErrorMapper implements ExceptionMapper<Exception> {
    @Override
    public Response toResponse(Exception e) {
        var err = new ErrorResponse("INTERNAL_ERROR", e.getMessage(), null);
        return Response.status(500).entity(err).build();
    }
}
