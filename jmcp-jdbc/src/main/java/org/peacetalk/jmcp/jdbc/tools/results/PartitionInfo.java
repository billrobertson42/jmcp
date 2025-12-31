package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Information about table partitioning
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartitionInfo(
    String partitionType,  // RANGE, LIST, HASH, KEY
    List<String> partitionKeys,
    List<PartitionDetail> partitions
) {
    /**
     * Details of a single partition
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PartitionDetail(
        String name,
        String expression,
        Long rowCount
    ) {}
}

