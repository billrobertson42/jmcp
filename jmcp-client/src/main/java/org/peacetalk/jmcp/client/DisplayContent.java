package org.peacetalk.jmcp.client;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Wrapper class for displaying Content with decoded JSON.
 * This allows the text field to be an Object instead of a String,
 * enabling JSON objects to be displayed inline instead of as escaped strings.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DisplayContent(String type, Object text) {
}

