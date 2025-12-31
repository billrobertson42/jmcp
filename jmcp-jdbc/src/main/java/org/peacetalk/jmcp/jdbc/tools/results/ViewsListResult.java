package org.peacetalk.jmcp.jdbc.tools.results;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
/**
 * Result of listing views
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ViewsListResult(
    List<ViewInfo> views,
    int count
) {
}
