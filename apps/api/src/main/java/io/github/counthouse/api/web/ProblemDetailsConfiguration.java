package io.github.counthouse.api.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ProblemDetailsConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class ProblemDetailsConfiguration implements WebMvcConfigurer {

    /**
     * Every exception no {@code @ExceptionHandler} claims becomes a problem: Spring's own keep their status, any other
     * is a 500 without its message, logged once and recorded on the request's observation.
     */
    @Override
    public void configureProblemDetails(ProblemDetailsConfigurer configurer) {
        configurer.renderUnhandledExceptions(true);
    }
}
