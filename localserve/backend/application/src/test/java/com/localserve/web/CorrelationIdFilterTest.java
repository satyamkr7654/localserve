package com.localserve.web;

import com.localserve.shared.identity.PublicId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CorrelationIdFilterTest {
    @Test void preservesValidPublicCorrelationId() throws Exception {
        String id = PublicId.generate().toString();
        var request = new MockHttpServletRequest("GET", "/api/v1/public/platform-status");
        request.addHeader(CorrelationIdFilter.HEADER, id);
        var response = new MockHttpServletResponse();
        new CorrelationIdFilter().doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });
        assertEquals(id, response.getHeader(CorrelationIdFilter.HEADER));
    }

    @Test void replacesMalformedCorrelationId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/");
        request.addHeader(CorrelationIdFilter.HEADER, "../../log-injection\nvalue");
        var response = new MockHttpServletResponse();
        new CorrelationIdFilter().doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });
        assertDoesNotThrow(() -> PublicId.parse(response.getHeader(CorrelationIdFilter.HEADER)));
    }
}
