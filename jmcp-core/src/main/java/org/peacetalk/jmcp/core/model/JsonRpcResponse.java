package org.peacetalk.jmcp.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * JSON-RPC 2.0 Response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcResponse(
    @JsonProperty("jsonrpc")
    @NotNull(message = "JSON-RPC version is required")
    @Pattern(regexp = "2\\.0", message = "JSON-RPC version must be '2.0'")
    String jsonrpc,  // TypeScript literal: "2.0"

    @JsonProperty("id")
    Object id,

    @JsonProperty("result")
    Object result,

    @JsonProperty("error")
    @Valid
    JsonRpcError error
) {
    public JsonRpcResponse {
        if (jsonrpc == null) {
            jsonrpc = "2.0";
        }
        // Ensure exactly one of result or error is present
        boolean hasResult = result != null;
        boolean hasError = error != null;
        if (hasResult == hasError) {
            throw new IllegalArgumentException(
                "Exactly one of result or error must be present, not both or neither"
            );
        }
    }

    @JsonIgnore
    @AssertTrue(message = "Exactly one of result or error must be present")
    public boolean isValid() {
        return (result != null) ^ (error != null);
    }

    public static JsonRpcResponse success(Object id, Object result) {
        return new JsonRpcResponse("2.0", id, result, null);
    }

    public static JsonRpcResponse error(Object id, JsonRpcError error) {
        return new JsonRpcResponse("2.0", id, null, error);
    }
}

