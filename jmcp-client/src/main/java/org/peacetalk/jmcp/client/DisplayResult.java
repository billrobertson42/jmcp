package org.peacetalk.jmcp.client;

import java.util.List;

/**
 * Wrapper class for displaying CallToolResult with decoded JSON.
 */
public record DisplayResult(List<Object> content, Boolean isError) {
}
