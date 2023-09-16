package webmvc.org.springframework.web.servlet.mvc.tobe;

import context.org.springframework.stereotype.Controller;
import jakarta.servlet.http.HttpServletRequest;
import org.reflections.Reflections;
import web.org.springframework.web.bind.annotation.CustomRequestMappings;
import web.org.springframework.web.bind.annotation.RequestMapping;
import web.org.springframework.web.bind.annotation.RequestMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RequestMappingHandlerMapping implements HandlerMapping {

    private final Reflections reflections;
    private final Map<HandlerKey, HandlerExecution> handlerExecutions;

    public RequestMappingHandlerMapping(final Object... basePackage) {
        this.reflections = new Reflections(basePackage);
        this.handlerExecutions = new HashMap<>();

        initialize();
    }

    private void initialize() {
        final Set<Class<?>> handlerClasses = reflections.getTypesAnnotatedWith(Controller.class);
        for (Class<?> handlerClass : handlerClasses) {
            final String prefix = extractPathPrefix(handlerClass);
            scanHandlerMethods(prefix, handlerClass);
        }
    }

    private String extractPathPrefix(final Class<?> handlerClass) {
        if (handlerClass.isAnnotationPresent(RequestMapping.class)) {
            final RequestMapping requestMapping = handlerClass.getDeclaredAnnotation(RequestMapping.class);
            return requestMapping.value();
        }
        return "";
    }

    private void scanHandlerMethods(final String prefix, final Class<?> controllerClass) {
        final Method[] methods = controllerClass.getDeclaredMethods();
        for (Method method : methods) {
            scanRequestMapping(prefix, method);
        }
    }

    private void scanRequestMapping(final String prefix, final Method method) {
        if (method.isAnnotationPresent(RequestMapping.class)) {
            final RequestMapping requestMapping = method.getDeclaredAnnotation(RequestMapping.class);
            addHandlerExecutionWhenRequestMapping(prefix, requestMapping, method);
            return;
        }
        scanCustomRequestMapping(prefix, method);
    }

    private void addHandlerExecutionWhenRequestMapping(final String prefix, final Annotation annotation,
                                                       final Method method) {
        final String requestPath = prefix + extractValueField(annotation);
        final RequestMethod[] requestMethods = extractMethodField(annotation);

        addHandlerExecution(method, requestPath, requestMethods);
    }

    private void scanCustomRequestMapping(final String prefix, final Method method) {
        Arrays.stream(method.getDeclaredAnnotations())
                .filter(CustomRequestMappings::isAnyMatch)
                .findFirst()
                .ifPresent(annotation -> addHandlerExecutionWhenCustomMapping(prefix, annotation, method));
    }

    private void addHandlerExecutionWhenCustomMapping(final String prefix, final Annotation annotation,
                                                      final Method method) {
        final RequestMapping requestMapping = annotation.annotationType()
                .getDeclaredAnnotation(RequestMapping.class);

        final String requestPath = prefix + extractValueField(annotation);
        final RequestMethod[] requestMethods = extractMethodField(requestMapping);

        addHandlerExecution(method, requestPath, requestMethods);
    }

    private void addHandlerExecution(final Method method, final String requestPath,
                                     final RequestMethod[] requestMethods) {
        final Object bean = instantiate(method.getDeclaringClass());
        final HandlerExecution handlerExecution = new HandlerExecution(bean, method);

        for (RequestMethod requestMethod : requestMethods) {
            HandlerKey handlerKey = new HandlerKey(requestPath, requestMethod);
            handlerExecutions.put(handlerKey, handlerExecution);
        }
    }

    private RequestMethod[] extractMethodField(final Annotation annotation) {
        try {
            return (RequestMethod[]) annotation.annotationType()
                    .getDeclaredMethod("method")
                    .invoke(annotation);
        } catch (IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            throw new AnnotationMethodInvokeException("어노테이션의 메소드를 실행시키는 도중 예외가 발생했습니다.", e);
        }
    }

    private String extractValueField(final Annotation annotation) {
        try {
            return (String) annotation.annotationType()
                    .getDeclaredMethod("value")
                    .invoke(annotation);
        } catch (IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            throw new AnnotationMethodInvokeException("어노테이션의 메소드를 실행시키는 도중 예외가 발생했습니다.", e);
        }
    }

    private Object instantiate(final Class<?> clazz) {
        try {
            return clazz.getConstructor()
                    .newInstance();
        } catch (InstantiationException | IllegalAccessException |
                 InvocationTargetException | NoSuchMethodException e) {
            throw new InstantiationFailedException("인스턴스화를 하는 도중 예외가 발생했습니다.", e);
        }
    }

    @Override
    public HandlerExecution getHandler(final HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        RequestMethod requestMethod = RequestMethod.valueOf(request.getMethod());
        HandlerKey handlerKey = new HandlerKey(requestURI, requestMethod);

        return handlerExecutions.get(handlerKey);
    }
}
