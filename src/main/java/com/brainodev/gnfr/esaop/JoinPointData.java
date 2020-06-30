/*
 * Copyright (c) 2020. Cisco Systems, Inc
 * All Rights reserved
 */

package com.brainodev.gnfr.esaop;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.mockito.invocation.InvocationOnMock;

@Slf4j
@Data
public class JoinPointData {

    public static final String IGNORED_PARAM_TYPE = "IGNORED_PARAM_TYPE";
    public static final String IGNORED_RETURN_TYPE = "IGNORED_RETURN_TYPE";

//    private static final DateTimeFormatter formatter =
//            DateTimeFormatter.ofPattern("yyyyMMdd’T’HHmmss.SSSZ")
//                    .withZone(ZoneOffset.UTC);

    static ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false);
        mapper.configure(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS, false);
    }
    // public static List<Class> PARAM_TYPE_TO_IGNORE = new ArrayList<>();

    private long creationTime = System.currentTimeMillis(); //formatter.format(Instant.now()) ;

    // Modifiers
    private int modifiers;

    // Return
    private Map<String, Object> result = new HashMap<>();
    private String sigReturnGenericTypeCanonicalName;
    private String sigReturnTypeCanonicalName; // From return type signature (it can be Object.class then use resultTypeCanonicalName to get the real implementation type)
    private String resultTypeCanonicalName; // From return type the return object
    private String sigReturnTypeSimpleName; // TODO delete and get from sigReturnTypeCanonicalName : use JoinPointData.getSimpleName
    private String sigReturnTypeName;
    private boolean sigReturnTypeIsPrimitive;
    private boolean sigReturnTypeIsCollection;
    private String throwableSimpleName; // TODO get from throwableCanonicalName
    private String throwableCanonicalName;
    private String throwableMessage;

    // Class
//    private Class sigDeclaringType;
    private String sigDeclaringTypePackageName;
    private String sigDeclaringTypeName;
    private String sigMethodName;
    private String sigDeclaringTypeCanonicalName;
    private String sigDeclaringTypeSimpleName; // TODO delete and get from sigDeclaringTypeCanonicalName : use JoinPointData.getSimpleName
    private String[] sigDeclaringTypeAnnotation;

    // Attributes
    private Map<String, Object> primitiveAttrs = new HashMap<>(); // attr name : value

    // Method
    private String jpLongString;
//    private Class[] exceptionTypes;

    // Params
    private String[] paramTypeCanonicalNames;
    private String[] paramTypeSimpleNames;
    private String[] paramTypeNames;
    private Boolean[] paramTypeArePrimitive;
    private Boolean[] paramTypeAreCollection;
    private Boolean[] paramTypeAreEnum;
    private String[] paramTypeGenericCanonicalNames;

    private String[] parameterNames; // or call this.getParamNames()
    private List<Map<String, Object>> mapParamsTypeValue = new ArrayList<>();

    // Meta data
    private int jpHash;
    private long threadId;
    private AspectPhase aspectPhase;
    private StackTraceElement[] stackTraceElements;

    @JsonIgnore
    public String[] getParamNames() {
        try {
            Class clazz = Class.forName(sigDeclaringTypeCanonicalName);
            List<Class> parameterTypes = Arrays.stream(paramTypeNames).map(paramName -> {
                try {
                    return Utils.classForName(paramName);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                return null;
            }).collect(Collectors.toList());

            Method method = clazz.getMethod(sigMethodName, parameterTypes.toArray(Class[]::new));
            return getParameterNames(method).toArray(String[]::new);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            log.error(e.getMessage());
//            e.printStackTrace();
            return null;
        }
    }


    @JsonIgnore
    private static List<String> getParameterNames(Method method) {
        Parameter[] parameters = method.getParameters();
        List<String> parameterNames = new ArrayList<>();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            parameterNames.add(parameter.isNamePresent() ? parameter.getName() : "arg" + i);
        }
        return parameterNames;
    }

    @JsonIgnore
    public boolean areAllParamsPrimitive() {

        String[] calledParamsTypCanonicalName = getParamTypeCanonicalNames();

        boolean allParamsArePrimitive = true;
        for (int i = 0; i < calledParamsTypCanonicalName.length; i++) {
            try {
                String calledParamTypCanonicalName = calledParamsTypCanonicalName[i];
                Class paramType = Utils.classForName(calledParamTypCanonicalName);
                if (!Utils.isPrimitive(paramType)) {
                    allParamsArePrimitive = false;
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return allParamsArePrimitive;
    }

    @JsonIgnore
    public String getReturnTypeCanonicalName() {
        return resultTypeCanonicalName != null ? resultTypeCanonicalName : sigReturnTypeCanonicalName;
    }

    public enum AspectPhase {
        Before, AfterReturn, AfterThrowing,
    }

    public JoinPointData() {
    }

    private JoinPointData(JoinPoint joinPoint, AspectPhase aspectPhase) {

        this.jpHash = joinPoint.hashCode();
        this.threadId = Thread.currentThread().getId();

        MethodSignature codeSignature = (MethodSignature) joinPoint.getSignature();
        this.modifiers = codeSignature.getModifiers();

//        this.sigDeclaringType = joinPoint.getSignature().getDeclaringType();
        this.sigMethodName = joinPoint.getSignature().getName();
        this.sigDeclaringTypeAnnotation = Arrays.stream(joinPoint.getSignature().getDeclaringType().getAnnotations()).map(a -> a.annotationType().getCanonicalName()).toArray(String[]::new);
        this.sigDeclaringTypeCanonicalName = joinPoint.getSignature().getDeclaringType().getCanonicalName();
        this.sigDeclaringTypeSimpleName = joinPoint.getSignature().getDeclaringType().getSimpleName();
        this.sigDeclaringTypeName = joinPoint.getSignature().getDeclaringTypeName();
        this.jpLongString = joinPoint.toLongString();

        Object target = joinPoint.getTarget();
//        Object jpThis = joinPoint.getThis();

        Class testedClass = joinPoint.getSignature().getDeclaringType();
        fillPrimitiveAttrs(testedClass, target);
        fillSigReturnType(codeSignature.getReturnType());
        fillSigReturnGenericTypeCanonicalName(codeSignature.getReturnType(), codeSignature.getMethod());

        this.paramTypeCanonicalNames = Arrays.stream(codeSignature.getParameterTypes()).map(t -> t.getCanonicalName()).toArray(String[]::new);

        this.parameterNames = codeSignature.getParameterNames();
//        this.paramValues = joinPoint.getArgs();
        if (codeSignature.getParameterNames() != null) { // null example : org.springframework.data.repository.CrudRepository
            fillMapParamsTypeValue(joinPoint.getArgs());
        }
        fillParameterTypes(codeSignature.getParameterTypes());
        fillParamTypeGenericCanonicalNames(((MethodSignature) joinPoint.getSignature()).getMethod());

        // this.exceptionTypes = codeSignature.getExceptionTypes();
        this.aspectPhase = aspectPhase;
        this.stackTraceElements = Utils.getStackTraceElement();
        this.sigDeclaringTypePackageName = joinPoint.getSignature().getDeclaringType().getPackage().getName();
    }

    private JoinPointData(InvocationOnMock invocationOnMock, AspectPhase aspectPhase) {

        Method method = invocationOnMock.getMethod();
        Object[] args = invocationOnMock.getArguments();
        Object target = invocationOnMock.getMock();

        this.jpHash = invocationOnMock.hashCode();
        this.threadId = Thread.currentThread().getId();
        this.modifiers = method.getModifiers();

//        this.sigDeclaringType = joinPoint.getSignature().getDeclaringType();
        this.sigMethodName = method.getName();
        this.sigDeclaringTypeAnnotation = Arrays.stream(target.getClass().getAnnotations()).map(a -> a.annotationType().getCanonicalName()).toArray(String[]::new);
        this.sigDeclaringTypeCanonicalName = target.getClass().getCanonicalName();
        this.sigDeclaringTypeSimpleName = target.getClass().getSimpleName();
        this.sigDeclaringTypeName = null; // TODO ?
        this.jpLongString = method.toGenericString();


        Class testedClass = target.getClass();
        fillPrimitiveAttrs(testedClass, target);
        fillSigReturnType(method.getReturnType());
        fillSigReturnGenericTypeCanonicalName(method.getReturnType(), method);

        this.paramTypeCanonicalNames = Arrays.stream(method.getParameterTypes()).map(t -> t.getCanonicalName()).toArray(String[]::new);

//        Since Java 1.8, this can be done as long as the parameter names are in the class files. Using javac this is done passing the -parameters flag. From the javac help
//        -parameters    Generate metadata for reflection on method parameters
        this.parameterNames = Arrays.stream(method.getParameters()).map(param -> param.getName()).collect(Collectors.toList()).toArray(new String[0]);
//        this.paramValues = joinPoint.getArgs();
        fillMapParamsTypeValue(args);

        fillParameterTypes(method.getParameterTypes());
        fillParamTypeGenericCanonicalNames(method);

        // this.exceptionTypes = codeSignature.getExceptionTypes();
        this.aspectPhase = aspectPhase;
        this.stackTraceElements = Utils.getStackTraceElement();
        this.sigDeclaringTypePackageName = target.getClass().getPackage().getName();
    }


    public JoinPointData(JoinPoint joinPoint) {
        this(joinPoint, AspectPhase.Before);
    }


    public JoinPointData(Object result, JoinPoint joinPoint) {
        this(joinPoint, AspectPhase.AfterReturn);
        Class returnType = ((MethodSignature) joinPoint.getSignature()).getReturnType();
        fillReturnValue(returnType, result);
    }

    public JoinPointData(Object result, InvocationOnMock invocationOnMock) {
        this(invocationOnMock, AspectPhase.AfterReturn);
        Class returnType = invocationOnMock.getMethod().getReturnType();
        fillReturnValue(returnType, result);
    }

    public JoinPointData(JoinPoint joinPoint, Throwable throwable) {
        this(joinPoint, AspectPhase.AfterThrowing);
        this.throwableSimpleName = throwable.getClass().getSimpleName();
        this.throwableCanonicalName = throwable.getClass().getCanonicalName();
        this.throwableMessage = throwable.getMessage();
    }


    private void fillParamTypeGenericCanonicalNames(Method method) {
        this.paramTypeGenericCanonicalNames = Arrays.stream(method.getGenericParameterTypes())
                .map(genericParamsType -> {
                    if (genericParamsType instanceof Class) {
                        Class parametterType = (Class) genericParamsType;
                        String castCanonicalName = parametterType.getCanonicalName();
                        String castSimpleName = parametterType.getSimpleName();
                        if (parametterType.isPrimitive()) {
                            return castCanonicalName;
                        } else if (castCanonicalName.equals("java.util.Arrays.ArrayList")) {
                            // castName = "java.util.List";
                            castCanonicalName = "List";
                        } else if (parametterType.isEnum()) {
                            castCanonicalName = Utils.replaceLast(castCanonicalName, ".", "$");
                            // return parametterType.getCanonicalName();
                        }
                    }
                    return genericParamsType.getTypeName();

                })
                .toArray(String[]::new);
    }


    private void fillReturnValue(Class returnType, Object result) {
        try {
            if (result != null) {
                // Test serialization
                mapper/*.writerWithDefaultPrettyPrinter()*/.writeValueAsString(result);
            }
            this.result.put(returnType.getCanonicalName(), result);
        } catch (Exception e) { // Function serialization error ?
            log.error("Cannot serialize result type : " + returnType.getCanonicalName() + " : ");
            this.result.put(returnType.getCanonicalName(), IGNORED_RETURN_TYPE);
        }

        if (result != null && sigReturnGenericTypeCanonicalName != null) {
            resultTypeCanonicalName = result.getClass().getCanonicalName();
            if (Collection.class.isAssignableFrom(result.getClass())) {
                Collection collection = (Collection) result;
                if (!collection.isEmpty()) {
                    sigReturnGenericTypeCanonicalName = collection.toArray()[0].getClass().getCanonicalName();
                }
            }
        }

        // Optional.class
        if (returnType.equals(Optional.class)) {
            Optional optional = (Optional) result;
            if (optional.isPresent()) {
                this.result.put(returnType.getCanonicalName() + ".get", optional.get());
                sigReturnGenericTypeCanonicalName = optional.get().getClass().getCanonicalName();
            }
        }
    }


    private void fillParameterTypes(Class[] parameterTypes) {
        this.paramTypeSimpleNames = Arrays.stream(parameterTypes).map(t -> t.getSimpleName()).toArray(String[]::new);
        this.paramTypeNames = Arrays.stream(parameterTypes).map(t -> t.getName()).toArray(String[]::new);
        this.paramTypeArePrimitive = Arrays.stream(parameterTypes).map(t -> t.isPrimitive()).toArray(Boolean[]::new);
        this.paramTypeAreEnum = Arrays.stream(parameterTypes).map(t -> t.isEnum()).toArray(Boolean[]::new);
        this.paramTypeAreCollection = Arrays.stream(parameterTypes).map(t -> Collection.class.isAssignableFrom(t)).toArray(Boolean[]::new);
    }

    private void fillSigReturnGenericTypeCanonicalName(Class returnType, Method method) {

        if (Collection.class.isAssignableFrom(returnType)) {
            ParameterizedType typeImpl = (ParameterizedType) method.getGenericReturnType();
            Type genericCollectionType = typeImpl.getActualTypeArguments()[0];
            try {
                Class genericClass = null;
                if (genericCollectionType.getTypeName().contains("<")) {
                    // java.util.List<java.util.Map<java.lang.String, java.lang.Object>>
                    genericClass = Class.forName(genericCollectionType.getTypeName().split("<")[0]);
                } else {
                    genericClass = Class.forName(genericCollectionType.getTypeName());
                }
                this.sigReturnGenericTypeCanonicalName = genericClass.getCanonicalName();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

    }

    private void fillMapParamsTypeValue(Object[] args) {
        for (int i = 0; i < parameterNames.length; i++) {
            Map<String, Object> type_value = new HashMap<>();
            if (!"javax.servlet.http.HttpServletRequest".equals(paramTypeCanonicalNames[i])) {
                try {
                    if (args[i] != null) {
                        // Test serialization
                        mapper/*.writerWithDefaultPrettyPrinter()*/.writeValueAsString(args[i]);
                    }
                    type_value.put(paramTypeCanonicalNames[i], args[i]);
                } catch (Exception e) { // Function serialization error ?
                    log.error("Cannot serialize param type : " + paramTypeCanonicalNames[i] + " : ");
                    type_value.put(paramTypeCanonicalNames[i], IGNORED_PARAM_TYPE);
                }
            } else {
                type_value.put(paramTypeCanonicalNames[i], IGNORED_PARAM_TYPE);
            }
            mapParamsTypeValue.add(type_value);
        }
    }

    private void fillPrimitiveAttrs(Class testedClass, Object target) {
        for (Field field : testedClass.getDeclaredFields()) {
            try {
                if (Utils.isPrimitive(field.getType()) && !Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    this.primitiveAttrs.put(field.getName(), field.get(target));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void fillSigReturnType(Class<?> returnType) {
        this.sigReturnTypeCanonicalName = returnType.getCanonicalName();
        this.sigReturnTypeSimpleName = returnType.getSimpleName();
        this.sigReturnTypeName = returnType.getName();
        this.sigReturnTypeIsPrimitive = Utils.isPrimitive(returnType);
        this.sigReturnTypeIsCollection = Collection.class.isAssignableFrom(returnType);
    }

    public boolean isAnnotationPresent(Class annotation) {
        return Arrays.stream(sigDeclaringTypeAnnotation).filter(a -> annotation.getCanonicalName().equals(a)).findAny().isPresent();
    }

    @JsonIgnore
    public List<Object> getParamValues() {
        return mapParamsTypeValue.stream()
                .map(m -> m.values().contains(null) ? null : m.values().stream().findFirst().get())
                .collect(Collectors.toList());
    }

    public Map<String, Object> getResult() {
        if (aspectPhase == AspectPhase.Before || aspectPhase == AspectPhase.AfterThrowing) {
            // Before & AfterThrowing does not have a result reference
            return null;
        }
        return result;
    }

    @Override
    public String toString() {
        return jpLongString;
    }


    ////// OLD
    private JoinPointData(JoinPoint joinPoint, AspectPhase aspectPhase, boolean old) {

        this.jpHash = joinPoint.hashCode();
        this.threadId = Thread.currentThread().getId();

        this.modifiers = joinPoint.getSignature().getModifiers();

//        this.sigDeclaringType = joinPoint.getSignature().getDeclaringType();
        this.sigMethodName = joinPoint.getSignature().getName();
        this.sigDeclaringTypeAnnotation = Arrays.stream(joinPoint.getSignature().getDeclaringType().getAnnotations())
                .map(a -> a.annotationType().getCanonicalName())
                .toArray(String[]::new);
        this.sigDeclaringTypeCanonicalName = joinPoint.getSignature().getDeclaringType().getCanonicalName();
        this.sigDeclaringTypeSimpleName = joinPoint.getSignature().getDeclaringType().getSimpleName();
        this.sigDeclaringTypeName = joinPoint.getSignature().getDeclaringTypeName();
        this.jpLongString = joinPoint.toLongString();

        Object target = joinPoint.getTarget();
//        Object jpThis = joinPoint.getThis();

        Class testedClass = joinPoint.getSignature().getDeclaringType();
        for (Field field : testedClass.getDeclaredFields()) {
            try {
                if (Utils.isPrimitive(field.getType()) && !Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    this.primitiveAttrs.put(field.getName(), field.get(target));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        MethodSignature codeSignature = (MethodSignature) joinPoint.getSignature();
//        this.className = sigDeclaringType.getCanonicalName();
        this.sigReturnTypeCanonicalName = codeSignature.getReturnType().getCanonicalName();
        this.sigReturnTypeSimpleName = codeSignature.getReturnType().getSimpleName();
        this.sigReturnTypeName = codeSignature.getReturnType().getName();
        this.sigReturnTypeIsPrimitive = Utils.isPrimitive(codeSignature.getReturnType());
        this.sigReturnTypeIsCollection = Collection.class.isAssignableFrom(codeSignature.getReturnType());

//        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
//        Method method = signature.getMethod();
//        Type returnType = method.getGenericReturnType();
//        if (returnType instanceof ParameterizedType) {
//            ParameterizedType type = (ParameterizedType) returnType;
//            Type[] typeArguments = type.getActualTypeArguments();
//            Class typeArgClass = (Class) typeArguments[0];
//            sigReturnGenericTypeCanonicalName = typeArgClass.getCanonicalName();
////            for(Type typeArgument : typeArguments){
////                Class typeArgClass = (Class) typeArgument;
////                System.out.println("typeArgClass = " + typeArgClass);
////            }
//        }

        if (Collection.class.isAssignableFrom(codeSignature.getReturnType())) {
            ParameterizedType typeImpl = (ParameterizedType) codeSignature.getMethod().getGenericReturnType();
            Type genericCollectionType = typeImpl.getActualTypeArguments()[0];
            try {
                Class genericClass = null;
                if (genericCollectionType.getTypeName().contains("<")) {
                    // java.util.List<java.util.Map<java.lang.String, java.lang.Object>>
                    genericClass = Class.forName(genericCollectionType.getTypeName().split("<")[0]);
                } else {
                    genericClass = Class.forName(genericCollectionType.getTypeName());
                }
                sigReturnGenericTypeCanonicalName = genericClass.getCanonicalName();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }


        this.paramTypeCanonicalNames = Arrays.stream(codeSignature.getParameterTypes())
                .map(t -> t.getCanonicalName())
                .toArray(String[]::new);

        this.parameterNames = codeSignature.getParameterNames();
//        this.paramValues = joinPoint.getArgs();
        if (codeSignature.getParameterNames() != null) { // null example : org.springframework.data.repository.CrudRepository
            for (int i = 0; i < codeSignature.getParameterNames().length; i++) {
                Map<String, Object> type_value = new HashMap<>();
                try {
                    if (joinPoint.getArgs()[i] != null) {
                        // Test serialization
                        mapper/*.writerWithDefaultPrettyPrinter()*/.writeValueAsString(joinPoint.getArgs()[i]);
                    }
                    type_value.put(paramTypeCanonicalNames[i], joinPoint.getArgs()[i]);
                } catch (Exception e) { // Function serialization error ?
                    log.error("Cannot serialize param type : " + paramTypeCanonicalNames[i] + " : ");
                    type_value.put(paramTypeCanonicalNames[i], IGNORED_PARAM_TYPE);
                }
                mapParamsTypeValue.add(type_value);
            }
        }
        this.paramTypeSimpleNames = Arrays.stream(codeSignature.getParameterTypes())
                .map(t -> t.getSimpleName())
                .toArray(String[]::new);
        this.paramTypeNames = Arrays.stream(codeSignature.getParameterTypes())
                .map(t -> t.getName())
                .toArray(String[]::new);
        this.paramTypeArePrimitive = Arrays.stream(codeSignature.getParameterTypes())
                .map(t -> t.isPrimitive())
                .toArray(Boolean[]::new);
        this.paramTypeAreEnum = Arrays.stream(codeSignature.getParameterTypes())
                .map(t -> t.isEnum())
                .toArray(Boolean[]::new);
        this.paramTypeAreCollection = Arrays.stream(codeSignature.getParameterTypes())
                .map(t -> Collection.class.isAssignableFrom(t))
                .toArray(Boolean[]::new);
        this.paramTypeGenericCanonicalNames = Arrays.stream(((MethodSignature) joinPoint.getSignature()).getMethod().getGenericParameterTypes())
                .map(genericParamsType -> {
//                    if (genericParamsType instanceof ParameterizedType) {
//                        ParameterizedType typeImpl = (ParameterizedType) genericParamsType;
//                        String genericCollectionType = typeImpl.getActualTypeArguments()[0].getTypeName();
//                        try {
//                            Class genericClass = Class.forName(genericCollectionType);
//                            return genericClass.getCanonicalName();
//                        } catch (ClassNotFoundException e) {
//                            e.printStackTrace();
//                        }
//                    } else
                    if (genericParamsType instanceof Class) {
                        Class parametterType = (Class) genericParamsType;
                        String castCanonicalName = parametterType.getCanonicalName();
                        String castSimpleName = parametterType.getSimpleName();
                        if (parametterType.isPrimitive()) {
                            return castCanonicalName;
                        } else if (castCanonicalName.equals("java.util.Arrays.ArrayList")) {
                            // castName = "java.util.List";
                            castCanonicalName = "List";
                        } else if (parametterType.isEnum()) {
                            castCanonicalName = Utils.replaceLast(castCanonicalName, ".", "$");
                            // return parametterType.getCanonicalName();
                        }
                    }
                    return genericParamsType.getTypeName();

//                    Class parametterType = (Class) genericParamsType;
//                    String castCanonicalName = parametterType.getCanonicalName();
//                    String castSimpleName = parametterType.getSimpleName();
//                    if (castCanonicalName.equals("java.util.Arrays.ArrayList")) {
////                    castName = "java.util.List";
//                        castCanonicalName = "List";
//                    } else if (parametterType.isEnum()) {
//                        castCanonicalName = replaceLast(castCanonicalName, ".", "$");
//                    }
//                    try {
//                        if (Collection.class.isAssignableFrom(Class.forName(castCanonicalName))) {
//                            ParameterizedType typeImpl = (ParameterizedType) genericParamsType;
//                            String genericCollectionType = typeImpl.getActualTypeArguments()[0].getTypeName();
//                            try {
//                                Class genericClass = Class.forName(genericCollectionType);
//                                return genericClass.getCanonicalName();
//                            } catch (ClassNotFoundException e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    } catch (ClassNotFoundException e) {
//                        e.printStackTrace();
//                    }
//                    return null;
                })
                .toArray(String[]::new);

        // this.exceptionTypes = codeSignature.getExceptionTypes();
        this.aspectPhase = aspectPhase;
        this.stackTraceElements = Utils.getStackTraceElement();
        this.sigDeclaringTypePackageName = joinPoint.getSignature().getDeclaringType().getPackage().getName();
    }
}
