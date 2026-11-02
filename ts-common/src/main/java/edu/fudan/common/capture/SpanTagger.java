package edu.fudan.common.capture;

/** Sink for key/value tags on the active entry span. */
public interface SpanTagger {
    void tag(String key, String value);
}
