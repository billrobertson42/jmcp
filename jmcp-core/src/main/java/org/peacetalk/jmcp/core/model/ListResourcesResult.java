package org.peacetalk.jmcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Result of listing available resources in the Model Context Protocol.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * interface ListResourcesResult {
 *   resources: Resource[];
 *   nextCursor?: string;
 * }
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/resources/">MCP Resources Specification</a>
 *
 * @param resources List of available resources
 * @param nextCursor Optional pagination cursor for fetching more resources
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListResourcesResult(
    @JsonProperty("resources")
    @NotNull(message = "Resources list is required")
    @Valid
    List<ResourceDescriptor> resources,

    @JsonProperty("nextCursor")
    String nextCursor
) {
    public ListResourcesResult {
        if (resources == null) {
            resources = List.of();
        }
    }

    /**
     * Create a result with no pagination cursor
     */
    public static ListResourcesResult of(List<ResourceDescriptor> resources) {
        return new ListResourcesResult(resources, null);
    }
}

