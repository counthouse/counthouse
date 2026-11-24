package io.github.counthouse.api.web;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Keeps {@code application/problem+json} out of the media types a handler produces. The generated interfaces list
 * every media type of an operation's responses in {@code produces}, the error type included: a client asking only
 * for it would get a success body labelled as a problem, and an operation whose only listed type is the error one
 * (a 204) would refuse {@code Accept: application/json}. Error responses are unaffected: the exception handling
 * writes them.
 */
@Configuration(proxyBeanMethods = false)
class ProducesConfiguration implements WebMvcRegistrations {

    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return new SuccessMediaTypesMapping();
    }

    private static final class SuccessMediaTypesMapping extends RequestMappingHandlerMapping {

        @Override
        protected @Nullable RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
            RequestMappingInfo info = super.getMappingForMethod(method, handlerType);
            if (info == null) {
                return null;
            }
            var produces = info.getProducesCondition().getProducibleMediaTypes();
            if (!produces.contains(MediaType.APPLICATION_PROBLEM_JSON)) {
                return info;
            }
            String[] success = produces.stream()
                    .filter(type -> !type.equals(MediaType.APPLICATION_PROBLEM_JSON))
                    .map(MediaType::toString)
                    .toArray(String[]::new);
            return info.mutate().produces(success).build();
        }
    }
}
