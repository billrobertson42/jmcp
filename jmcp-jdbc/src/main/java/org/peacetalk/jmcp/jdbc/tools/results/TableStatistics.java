package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information about table statistics
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TableStatistics(
    Long rowEstimate,
    String diskSize,
    String indexSize,
    String totalSize
) {
}

