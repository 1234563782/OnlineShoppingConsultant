package com.onlineshopping.orchestrator.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Handles two related SSE failure modes:
 * <ol>
 *   <li>Body already committed — default error page / JSON error cannot be written.</li>
 *   <li>{@link HttpMessageNotWritableException} — MVC tries to serialize error attributes as
 *       {@code Map} while {@code Content-Type} is already {@code text/event-stream}.</li>
 * </ol>
 * Returning an empty {@link ModelAndView} marks the exception as handled without forwarding to {@code /error}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CommittedResponseExceptionResolver implements HandlerExceptionResolver {

    private static final Logger log = LoggerFactory.getLogger(CommittedResponseExceptionResolver.class);

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        if (isSseIncompatibleBodyWrite(ex)) {
            log.warn(
                    "Skipping error-body write for SSE ({} {}): {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    ex.getMessage()
            );
            log.debug("SSE-incompatible body write detail", ex);
            return new ModelAndView();
        }
        if (response.isCommitted()) {
            log.warn(
                    "Exception after response committed ({} {}); skipping error page. Cause: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    ex.toString()
            );
            log.debug("Committed-response exception detail", ex);
            return new ModelAndView();
        }
        return null;
    }

    /**
     * Spring's default error handling builds a {@link java.util.LinkedHashMap} body; no
     * {@code HttpMessageConverter} supports that for {@code text/event-stream}.
     */
    private static boolean isSseIncompatibleBodyWrite(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof HttpMessageNotWritableException hmwe) {
                String msg = hmwe.getMessage();
                if (msg != null && msg.contains("text/event-stream")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
