/**
 * JSON Schema builder classes for constructing MCP tool input schemas.
 *
 * <p>These classes provide type-safe builders for creating JSON Schema structures
 * that are serialized to {@link tools.jackson.databind.JsonNode} and used in
 * {@link org.peacetalk.jmcp.core.model.Tool#inputSchema()} fields.
 *
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * // Build schema using type-safe records
 * ObjectSchema schema = new ObjectSchema(
 *     Map.of(
 *         "query", new StringProperty("The query to execute"),
 *         "limit", new IntegerProperty("Result limit", 1, 1000),
 *         "options", new ArrayProperty(
 *             "Query options",
 *             new StringProperty("Option value")
 *         )
 *     ),
 *     List.of("query")  // required fields
 * );
 *
 * // Convert to JsonNode for MCP Tool
 * ObjectMapper mapper = new ObjectMapper();
 * JsonNode schemaNode = mapper.valueToTree(schema);
 * }</pre>
 *
 * <p>This produces JSON conforming to JSON Schema specification:
 * <pre>{@code
 * {
 *   "type": "object",
 *   "properties": {
 *     "query": {
 *       "type": "string",
 *       "description": "The query to execute"
 *     },
 *     "limit": {
 *       "type": "integer",
 *       "description": "Result limit",
 *       "minimum": 1,
 *       "maximum": 1000
 *     },
 *     "options": {
 *       "type": "array",
 *       "description": "Query options",
 *       "items": {
 *         "type": "string",
 *         "description": "Option value"
 *       }
 *     }
 *   },
 *   "required": ["query"]
 * }
 * }</pre>
 *
 * <h2>Available Builders</h2>
 * <ul>
 *   <li>{@link org.peacetalk.jmcp.core.schema.ObjectSchema} - Object schema with properties and required fields</li>
 *   <li>{@link org.peacetalk.jmcp.core.schema.StringProperty} - String property with optional min/max length</li>
 *   <li>{@link org.peacetalk.jmcp.core.schema.IntegerProperty} - Integer property with optional min/max values</li>
 *   <li>{@link org.peacetalk.jmcp.core.schema.ArrayProperty} - Array property with item schema</li>
 * </ul>
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li><b>Server-side:</b> Define tool input schemas in any MCP server implementation</li>
 *   <li><b>Client-side:</b> Parse schemas to generate appropriate UI controls</li>
 *   <li><b>Validation:</b> Type-safe schema construction prevents invalid schemas</li>
 * </ul>
 *
 * @see org.peacetalk.jmcp.core.model.Tool
 * @see <a href="https://json-schema.org/">JSON Schema Specification</a>
 * @see <a href="https://spec.modelcontextprotocol.io/">Model Context Protocol Specification</a>
 */
package org.peacetalk.jmcp.core.schema;

