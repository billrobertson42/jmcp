/*
 * Copyright 2024 the jmcp authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.peacetalk.jmcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * JSON-RPC 2.0 Error
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcError(
    @JsonProperty("code")
    int code,

    @JsonProperty("message")
    @NotBlank(message = "Error message is required")
    String message,

    @JsonProperty("data")
    Object data
) {
    // Standard JSON-RPC error codes
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    public static JsonRpcError parseError(String message) {
        return new JsonRpcError(PARSE_ERROR, message, null);
    }

    public static JsonRpcError methodNotFound(String method) {
        return new JsonRpcError(METHOD_NOT_FOUND, "Method not found: " + method, null);
    }

    public static JsonRpcError invalidParams(String message) {
        return new JsonRpcError(INVALID_PARAMS, message, null);
    }

    public static JsonRpcError internalError(String message) {
        return new JsonRpcError(INTERNAL_ERROR, message, null);
    }
}

