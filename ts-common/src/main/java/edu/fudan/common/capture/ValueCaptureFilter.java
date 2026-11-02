package edu.fudan.common.capture;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tags the active entry span with the request path/query/body and the JSON response body.
 * When disabled it is a pure passthrough.
 */
public class ValueCaptureFilter extends OncePerRequestFilter {

    private static final Pattern CHARSET = Pattern.compile("charset=\"?([^;\"\\s]+)", Pattern.CASE_INSENSITIVE);

    private final SpanTagger tagger;
    private final boolean enabled;
    private final int maxBytes;

    public ValueCaptureFilter(SpanTagger tagger, boolean enabled, int maxBytes) {
        this.tagger = tagger;
        this.enabled = enabled;
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }
        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper resp = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(req, resp);
        } finally {
            try {
                capture(req, resp);
            } catch (Throwable ignored) {
                // tagging must never break a request
            }
            resp.copyBodyToResponse();
        }
    }

    private void capture(ContentCachingRequestWrapper req, ContentCachingResponseWrapper resp) {
        tag("req.path", req.getRequestURI());
        if (req.getQueryString() != null) {
            tag("req.query", req.getQueryString());
        }
        byte[] reqBody = req.getContentAsByteArray();
        if (reqBody.length > 0) {
            tag("req.body", cap(reqBody, charsetOf(req.getContentType(), req.getCharacterEncoding()), maxBytes));
        }
        String type = resp.getContentType();
        if (type != null) {
            tag("resp.content_type", type);
            byte[] respBody = resp.getContentAsByteArray();
            if (isJson(type) && respBody.length > 0) {
                tag("resp.body", cap(respBody, charsetOf(type, null), maxBytes));
            }
        }
    }

    private void tag(String key, String value) {
        try {
            tagger.tag(key, value);
        } catch (Throwable ignored) {
            // tagging must never break a request
        }
    }

    private static boolean isJson(String contentType) {
        String mime = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return mime.equals("application/json") || mime.endsWith("+json");
    }

    private static Charset charsetOf(String contentType, String explicit) {
        String name = explicit;
        if (name == null && contentType != null) {
            Matcher m = CHARSET.matcher(contentType);
            if (m.find()) {
                name = m.group(1);
            }
        }
        try {
            return name == null ? StandardCharsets.UTF_8 : Charset.forName(name);
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }

    /** Decodes at most maxBytes of body without splitting a character; marks what was dropped. */
    static String cap(byte[] body, Charset cs, int maxBytes) {
        if (body.length <= maxBytes) {
            return new String(body, cs);
        }
        String kept;
        try {
            kept = cs.newDecoder()
                    .onMalformedInput(CodingErrorAction.IGNORE)
                    .onUnmappableCharacter(CodingErrorAction.IGNORE)
                    .decode(ByteBuffer.wrap(body, 0, maxBytes))
                    .toString();
        } catch (CharacterCodingException e) {
            kept = new String(body, 0, maxBytes, cs);
        }
        int dropped = body.length - kept.getBytes(cs).length;
        return kept + "…[truncated " + dropped + " bytes]";
    }
}
