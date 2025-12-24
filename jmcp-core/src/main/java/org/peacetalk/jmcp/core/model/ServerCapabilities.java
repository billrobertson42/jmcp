package org.peacetalk.jmcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;

/**
 * Server capabilities in the Model Context Protocol.
 *
 * Corresponds to TypeScript definition:
 * <pre>
 * interface ServerCapabilities {
 *   experimental?: { [key: string]: unknown };
 *   logging?: {};
 *   prompts?: {
 *     listChanged?: boolean;
 *   };
 *   resources?: {
 *     subscribe?: boolean;
 *     listChanged?: boolean;
 *   };
 *   tools?: {
 *     listChanged?: boolean;
 *   };
 * }
 * </pre>
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/basic/lifecycle/">MCP Lifecycle Specification</a>
 *
 * @param experimental Optional experimental capabilities
 * @param logging Optional logging capability marker
 * @param prompts Optional prompts capabilities
 * @param resources Optional resources capabilities
 * @param tools Optional tools capabilities
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerCapabilities(
    @JsonProperty("experimental")
    Object experimental,

    @JsonProperty("logging")
    Object logging,

    @JsonProperty("prompts")
    @Valid
    PromptsCapability prompts,

    @JsonProperty("resources")
    @Valid
    ResourcesCapability resources,

    @JsonProperty("tools")
    @Valid
    ToolsCapability tools
) {
    /**
     * Prompts capability
     *
     * @param listChanged Whether the server supports prompts/list notifications
     */
    public record PromptsCapability(
        @JsonProperty("listChanged") Boolean listChanged
    ) {}

    /**
     * Resources capability
     *
     * @param subscribe Whether the server supports resource subscriptions
     * @param listChanged Whether the server supports resources/list notifications
     */
    public record ResourcesCapability(
        @JsonProperty("subscribe") Boolean subscribe,
        @JsonProperty("listChanged") Boolean listChanged
    ) {}

    /**
     * Tools capability
     *
     * @param listChanged Whether the server supports tools/list notifications
     */
    public record ToolsCapability(
        @JsonProperty("listChanged") Boolean listChanged
    ) {}
}

