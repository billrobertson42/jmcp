package org.peacetalk.jmcp.client;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Wrapper class for displaying CallToolResult with decoded JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DisplayResult(List<Object> content, Boolean isError) {
}
