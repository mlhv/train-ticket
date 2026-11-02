package edu.fudan.common.capture;

import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;

/** Tags the active SkyWalking span (a no-op when the agent is not attached). */
public class SkyWalkingSpanTagger implements SpanTagger {
    @Override
    public void tag(String key, String value) {
        ActiveSpan.tag(key, value);
    }
}
