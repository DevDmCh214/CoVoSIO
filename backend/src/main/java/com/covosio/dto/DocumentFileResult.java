package com.covosio.dto;

import org.springframework.core.io.Resource;

/**
 * Carries the file resource and its MIME type for secure file download responses.
 */
public record DocumentFileResult(Resource resource, String mimeType) {
}
