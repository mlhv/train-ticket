package edu.fudan.common.capture;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ValueCaptureFilterTest {

    private static class Recorder implements SpanTagger {
        final Map<String, String> tags = new LinkedHashMap<>();
        public void tag(String key, String value) { tags.put(key, value); }
    }

    private static void drain(InputStream in) throws IOException {
        byte[] buf = new byte[256];
        while (in.read(buf) != -1) { }
    }

    private static void write(HttpServletResponse resp, String type, String body) throws IOException {
        resp.setContentType(type);
        resp.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequest post() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/seatservice/seats");
        req.setQueryString("x=1");
        req.setContentType("application/json");
        req.setContent("{\"tripId\":\"G1236\"}".getBytes(StandardCharsets.UTF_8));
        return req;
    }

    private static final String JSON_RESP = "{\"status\":1,\"data\":{\"seatNo\":7}}";

    private static final FilterChain JSON_CHAIN = (rq, rs) -> {
        drain(rq.getInputStream());
        write((HttpServletResponse) rs, "application/json", JSON_RESP);
    };

    @Test
    public void postJsonTagsAllFour() throws Exception {
        Recorder r = new Recorder();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        new ValueCaptureFilter(r, true, 16384).doFilter(post(), resp, JSON_CHAIN);
        assertEquals("/api/v1/seatservice/seats", r.tags.get("req.path"));
        assertEquals("x=1", r.tags.get("req.query"));
        assertEquals("{\"tripId\":\"G1236\"}", r.tags.get("req.body"));
        assertEquals(JSON_RESP, r.tags.get("resp.body"));
        assertTrue(r.tags.get("resp.content_type").startsWith("application/json"));
    }

    @Test
    public void responseBytesIdenticalToClient() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        new ValueCaptureFilter(new Recorder(), true, 16384).doFilter(post(), resp, JSON_CHAIN);
        assertArrayEquals(JSON_RESP.getBytes(StandardCharsets.UTF_8), resp.getContentAsByteArray());
    }

    @Test
    public void disabledTagsNothing() throws Exception {
        Recorder r = new Recorder();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        new ValueCaptureFilter(r, false, 16384).doFilter(post(), resp, JSON_CHAIN);
        assertTrue(r.tags.isEmpty());
        assertArrayEquals(JSON_RESP.getBytes(StandardCharsets.UTF_8), resp.getContentAsByteArray());
    }

    @Test
    public void getWithoutBodyTagsNoReqBody() throws Exception {
        Recorder r = new Recorder();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
        new ValueCaptureFilter(r, true, 16384).doFilter(req, new MockHttpServletResponse(),
                (rq, rs) -> write((HttpServletResponse) rs, "application/json", "{}"));
        assertFalse(r.tags.containsKey("req.body"));
        assertFalse(r.tags.containsKey("req.query"));
        assertEquals("/api/v1/x", r.tags.get("req.path"));
    }

    @Test
    public void nonJsonResponseTagsTypeOnly() throws Exception {
        Recorder r = new Recorder();
        new ValueCaptureFilter(r, true, 16384).doFilter(new MockHttpServletRequest("GET", "/p"),
                new MockHttpServletResponse(),
                (rq, rs) -> write((HttpServletResponse) rs, "text/html", "<p>hi</p>"));
        assertEquals("text/html", r.tags.get("resp.content_type"));
        assertFalse(r.tags.containsKey("resp.body"));
    }

    @Test
    public void longBodyIsCappedWithMarker() throws Exception {
        Recorder r = new Recorder();
        String body = "abcdefghijklmnopqrstuvwxy";
        new ValueCaptureFilter(r, true, 10).doFilter(new MockHttpServletRequest("GET", "/p"),
                new MockHttpServletResponse(),
                (rq, rs) -> write((HttpServletResponse) rs, "application/json", body));
        assertEquals("abcdefghij…[truncated 15 bytes]", r.tags.get("resp.body"));
    }

    @Test
    public void capDoesNotSplitUtf8() {
        assertEquals("南…[truncated 9 bytes]",
                ValueCaptureFilter.cap("南京上海".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, 4));
    }

    @Test
    public void chainExceptionStillCopiesResponse() throws Exception {
        Recorder r = new Recorder();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        try {
            new ValueCaptureFilter(r, true, 16384).doFilter(new MockHttpServletRequest("GET", "/p"), resp,
                    (rq, rs) -> {
                        write((HttpServletResponse) rs, "application/json", "{\"par");
                        throw new ServletException("boom");
                    });
            fail("expected ServletException");
        } catch (ServletException e) {
            assertEquals("boom", e.getMessage());
        }
        assertEquals("{\"par", r.tags.get("resp.body"));
        assertEquals("{\"par", resp.getContentAsString());
        assertFalse("response must not be committed on the exception path", resp.isCommitted());
    }

    @Test
    public void jsonVariantsTagged() throws Exception {
        for (String type : new String[] {"application/problem+json", "application/json;charset=UTF-8"}) {
            Recorder r = new Recorder();
            new ValueCaptureFilter(r, true, 16384).doFilter(new MockHttpServletRequest("GET", "/p"),
                    new MockHttpServletResponse(), (rq, rs) -> write((HttpServletResponse) rs, type, "{\"a\":1}"));
            assertEquals(type, "{\"a\":1}", r.tags.get("resp.body"));
        }
    }

    @Test
    public void taggerThrowsRequestStillSucceeds() throws Exception {
        SpanTagger boom = (k, v) -> { throw new IllegalStateException("tag failed"); };
        MockHttpServletResponse resp = new MockHttpServletResponse();
        new ValueCaptureFilter(boom, true, 16384).doFilter(post(), resp, JSON_CHAIN);
        assertArrayEquals(JSON_RESP.getBytes(StandardCharsets.UTF_8), resp.getContentAsByteArray());
    }

    @Test
    public void nonJsonRequestBodyNotTagged() throws Exception {
        Recorder r = new Recorder();
        MockHttpServletRequest req = post();
        req.setContentType("text/plain");
        new ValueCaptureFilter(r, true, 16384).doFilter(req, new MockHttpServletResponse(), JSON_CHAIN);
        assertFalse(r.tags.containsKey("req.body"));
    }
}
