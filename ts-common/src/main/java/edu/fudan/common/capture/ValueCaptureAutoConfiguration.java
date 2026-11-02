package edu.fudan.common.capture;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ValueCaptureAutoConfiguration {

    private static final int DEFAULT_MAX_BYTES = 16384;

    @Bean
    public FilterRegistrationBean<ValueCaptureFilter> valueCaptureFilter() {
        boolean enabled = "on".equals(System.getenv("TT_VALUE_CAPTURE"));
        FilterRegistrationBean<ValueCaptureFilter> bean = new FilterRegistrationBean<>(
                new ValueCaptureFilter(new SkyWalkingSpanTagger(), enabled, maxBytes()));
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }

    private static int maxBytes() {
        String v = System.getenv("TT_VALUE_CAPTURE_MAX_BYTES");
        if (v != null) {
            try {
                int n = Integer.parseInt(v.trim());
                if (n > 0) {
                    return n;
                }
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return DEFAULT_MAX_BYTES;
    }
}
