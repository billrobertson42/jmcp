package org.peacetalk.jmcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * JSON-RPC 2.0 Request
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcRequest(
    @JsonProperty("jsonrpc")
    @NotNull(message = "JSON-RPC version is required")
    @Pattern(regexp = "2\\.0", message = "JSON-RPC version must be '2.0'")
    String jsonrpc,  // TypeScript literal: "2.0"

    @JsonProperty("id")
    Object id,

    @JsonProperty("method")
    @NotBlank(message = "Method name is required")
    String method,

    @JsonProperty("params")
    Object params
) {
    public JsonRpcRequest {
        if (jsonrpc == null) {
            jsonrpc = "2.0";
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Method name cannot be null or blank");
        }
    }
}

