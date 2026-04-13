package org.peacetalk.jmcp.core;
import java.util.Collections;
import java.util.List;
import java.util.Map;
/**
 * SPI interface for MCP capability providers.
 * A provider can supply tools, resources, and (in future) prompts
 * from a single domain.
 *
 * This interface replaces the former ToolProvider interface and serves
 * as both the SPI entry point for ServiceLoader discovery and the
 * internal registration contract for ToolsHandler.
 *
 * Implementations are discovered via java.util.ServiceLoader.
 *
 * To register a provider, add to module-info.java:
 *   provides org.peacetalk.jmcp.core.McpProvider
 *       with com.example.MyMcpProvider;
 *
 * <h2>Configuration</h2>
 * The server reads a centralized config file and passes each provider
 * its configuration section (keyed by JPMS module name) as a
 * {@code Map<String, Object>}. The config parameter may be null if
 * no section exists for this provider's module. Whether null config
 * is an error is determined by the provider — providers that require
 * configuration must throw.
 *
 * The centralized config mechanism does not prohi * The centralfrom
 * supp * supp * supp * supp * supp * supp * supp ces (system properties,
 * environment variables, etc.).
 *
 * <h2>Erro * <h2>Erro * <h * Implementations MUST throw if initialization cannot complete
 * successfully. A provider that initializes without error is expected
 * to be fully functional. Do not swallow errors or fall back to a
 * degraded state silently. Use standard JDK exception types
 * (e.g., {@link IllegalStateException}, {@link java.io.IOException}).
 */
public interface McpProvider {
    /** Human-readable name for this provider */
    String getName();
    /**
     * Initialize the provider with the given configuration.
     *
     * @param config Provider-specific configuration, extracted from the
     *               server config file using the provider's JPMS module
     *               name as the key. May be null if no configuration
     *               section exists for this provider. Providers that
     *               require configuration must throw an appropriate
     *               exception (e.g., IllegalStateException) when config
     *               is null or insufficient.
     * @throws Exception if initialization fails for any reason. The
     *                   server will log the exception, print the full
     *                   stack trace, and terminate.
     */
    void initialize(Map<String, Object> config) throws Exception;
    /**
     * Get all tools from this provider.
     * Returns empty list if this provider has no tools.
     */
    default List<Tool> getTools() {
        return Collections.emptyList();
    }
    /**
     * Get the resource provider, if this provider supports resources.
     * Returns null if this provider has no resources.
     */
    default ResourceProvider getResourceProvider() {
        return null;
    }
    // Future: default PromptProvider getPromptProvider() { return null; }
    /** Clean up resources used by this provider */
    void shutdown();
}
