/*
 * Copyright (c) 2020. Cisco Systems, Inc
 * All Rights reserved
 */

package com.brainodev.gnfr.esaop;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

@Slf4j
public class ESAOPRecorder {

    static ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false);
        mapper.configure(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS, false);
        SimpleModule module = new SimpleModule();
        module.addSerializer(Object.class, new GenericJsonSerializer());
//        mapper.registerModule(module);
    }

    public static Object around(ProceedingJoinPoint joinPoint) throws Throwable {

        log.info("\nAOP : Around " + joinPoint.toLongString() + " ThreadId=" + Thread.currentThread().getId());


        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        try {
            // JavaCompiler compiler = ToolProvider.getSystemJavaCompiler(); // https://dzone.com/articles/java-reflection-but-faster
            Object serviceTarget = joinPoint.getTarget(); // class@1234
            Object serviceThis = joinPoint.getThis(); // class$$EnhancerBySpringCGLIB$$azer345@67989
            Object mock = mock(serviceTarget);
//            Object res = method.invoke(mock, joinPoint.getArgs());
            Object res = joinPoint.proceed(); // to call the real method.
            try {
                JoinPointData joinPointData = new JoinPointData(res, joinPoint);
                GenericJsonSerializer.reset();
                String json = mapper/*.writerWithDefaultPrettyPrinter()*/.writeValueAsString(joinPointData);
//                ElasticSearchUtils.index(json);
            } catch (Exception e) {
                log.error("AOP Error : Around " + joinPoint.toLongString() + " ThreadId=" + Thread.currentThread().getId());
                e.printStackTrace();
            }
            return res;
        } finally {
            // Do something useful.
        }
    }


    private static Object mock(Object target) {
        if (target == null) {
            return null;
        } else if (Modifier.isFinal(target.getClass().getModifiers())) {
            return target;
        }
//        Object mock = Mockito.mock(target.getClass(), (Answer) invocationOnMock -> {
//            Method method = invocationOnMock.getMethod();
//            log.info("AOP Mockito : " + method);
//            Object res = invocationOnMock.callRealMethod();
//            return mock(res);
//        });
        log.info("\nAOP Mockito mocking fields of : " + target.getClass().getCanonicalName());
        for (Field targetField : target.getClass().getDeclaredFields()) {
            try {
                if (!Utils.isPrimitive(targetField.getType()) && !Modifier.isStatic(targetField.getType().getModifiers())) {
//                if (field.getType().equals(RestClient.class)) {
                    targetField.setAccessible(true);
                    Object targetFieldValue = targetField.get(target);
                    // not already mocked
                    if (targetFieldValue != null
                            && !Modifier.isAbstract(targetField.getType().getModifiers())
                            && !targetFieldValue.getClass().getCanonicalName().contains("$$EnhancerBySpringCGLIB$$") // already handled by AOP ?
                            && !targetFieldValue.getClass().getCanonicalName().contains("$MockitoMock$")) {

                        log.info("\nAOP Mockito mocking field : " + targetField.getType().getCanonicalName());
                        Object mockFieldValue = Mockito.mock(targetField.getType(), (Answer) invocationOnMock -> {
                            Method method = invocationOnMock.getMethod();
                            log.info("\nAOP Mockito call method: " + method);
                            Object res = invocationOnMock.callRealMethod();
                            try {
                                if (!"toString".equals(method.getName())) {

                                    JoinPointData joinPointData = new JoinPointData(res, invocationOnMock);
                                    GenericJsonSerializer.reset();
                                    String json = mapper/*.writerWithDefaultPrettyPrinter()*/.writeValueAsString(joinPointData);
//                                ElasticSearchUtils.index(json);
                                }
                            } catch (Exception e) {
                                log.error("Mockito Error : callRealMethod " + method + " ThreadId=" + Thread.currentThread().getId());
                                e.printStackTrace();
                            }
                            return mock(res);
                        });

                        Object sourceValue = targetField.get(target); // mockito field are by default null, so NPE when callRealMethod solution => copy field value from source to mocked object
                        for (Field fieldSource : targetField.getType().getDeclaredFields()) { // was wrong sourceValue.getClass().getDeclaredFields()
                            try {
                                if (!Modifier.isStatic(fieldSource.getType().getModifiers())) {
                                    fieldSource.setAccessible(true);
                                    fieldSource.set(mockFieldValue, fieldSource.get(sourceValue));
                                }
                            } catch (Exception e) {
                                // e.printStackTrace();
                            }
                        }
                        // field.set(mock, mockField);
                        log.info("\nAOP Mockito setting : " + targetField.getType() + " value: " + mockFieldValue);
                        targetField.set(target, mockFieldValue);
                    }
                }
            } catch (Exception e) {
                // e.printStackTrace();
            }
        }
        return target;
//        return mock;
    }

    public static void before(JoinPoint joinPoint) {

        log.info("AOP : Before " + joinPoint.toLongString() + " ThreadId=" + Thread.currentThread().getId());
        // new com.google.gson.Gson().toJson(joinPoint)
//        UnitTestGeneratorService.addUnitTestInputs(joinPoint);


//        try {
//            JoinPointData joinPointData = new JoinPointData(joinPoint);
//            GenericJsonSerializer.reset();
//            String json = mapper/*.writerWithDefaultPrettyPrinter()*/.writeValueAsString(joinPointData);
//            ElasticSearchUtils.index(json);
//        } catch (Exception e) {
//            log.error("AOP Error : Before " + joinPoint.toLongString() + " ThreadId=" + Thread.currentThread().getId());
//            e.printStackTrace();
//        }


//        new Thread(() -> {
//            try {
//                GenericJsonSerializer.reset();
////                String json = mapper.writeValueAsString(joinPointData);
//                String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(joinPointData);
//                String path = "/tmp/test/" + joinPoint.getSignature().toShortString().replace("(", "").replace(")", "");
//                Utils.setFileContent(new File(path + ".before." + System.currentTimeMillis() + ".json"), json);
////                System.err.println(joinPoint.toLongString() + " json before : " + json);
////                ESUTGeneratorService.addUnitTestInputs(joinPointData);
//            } catch (Exception e) {
//                log.info("Exception AOP : Before " + joinPoint.toLongString());
//                e.printStackTrace();
//            }
////        }
//        }).start();
    }

    public static void afterReturning(JoinPoint joinPoint, Object result) {

        long start = System.currentTimeMillis();
        log.info("AOP : afterReturning " + joinPoint.toLongString() + " jpHash=" + joinPoint.hashCode() + " ThreadId=" + Thread.currentThread().getId());

//        UnitTestGeneratorService.generatedUnitTest(joinPoint, result, null);

//        try {
//            JoinPointData joinPointData = new JoinPointData(result, joinPoint);
//            GenericJsonSerializer.reset();
//            String json = mapper/*.writerWithDefaultPrettyPrinter()*/.writeValueAsString(joinPointData);
//            ElasticSearchUtils.index(json);
//        } catch (Exception e) {
//            log.error("AOP Error : afterReturning " + joinPoint.toLongString() + " jpHash=" + joinPoint.hashCode() + " ThreadId=" + Thread.currentThread().getId());
//            e.printStackTrace();
//        }

        long time = System.currentTimeMillis() - start;
        log.info("AOP : afterReturning " + joinPoint.toLongString() + " jpHash=" + joinPoint.hashCode() + " ThreadId=" + Thread.currentThread().getId() + " Time=" + time);


//        new Thread(() -> {
//
////        if (joinPoint.getSignature().getDeclaringType().getCanonicalName().startsWith("com...")) {
////        if (joinPoint.getSignature().getDeclaringType().getCanonicalName().startsWith("com...")) {
//            try {
//
////            UnitTestGeneratorService.generatedUnitTest(joinPoint, result, null);
//                GenericJsonSerializer.reset();
//                // String jsonResult = TestUtils.om.writeValueAsString(result);
//
////            String json = TestUtils.om.writeValueAsString(joinPointData);
//                String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(joinPointData);
//                String path = "/tmp/test/" + joinPoint.getSignature().toShortString().replace("(", "").replace(")", "");
//                Utils.setFileContent(new File(path + ".after." + System.currentTimeMillis() + ".json"), json);
////                String json = mapper.writeValueAsString(joinPointData);
////                System.err.println(joinPoint.toLongString() + " json after : " + json);
////            System.err.println(joinPoint.toLongString() + " jsonResult after : " + jsonResult);
////                ESUTGeneratorService.generatedUnitTest(joinPointData);
//            } catch (Exception e) {
//                log.info("Exception AOP : After " + joinPoint.toLongString());
//                e.printStackTrace();
//            }
////        }
////          UnitTestGeneratorService.generatedUnitTest(joinPoint, result, null);
//        }).start();

    }

    public static void afterThrowing(JoinPoint joinPoint, Throwable throwable) {

        log.info("AOP : afterThrowing " + joinPoint.toLongString() + " jpHash=" + joinPoint.hashCode() + " ThreadId=" + Thread.currentThread().getId());

//        try {
//            JoinPointData joinPointData = new JoinPointData(joinPoint, throwable);
//            GenericJsonSerializer.reset();
//            String json = mapper/*.writerWithDefaultPrettyPrinter()*/.writeValueAsString(joinPointData);
//            ElasticSearchUtils.index(json);
//        } catch (Exception e) {
//            e.printStackTrace();
//            log.error("AOP Error : afterThrowing " + joinPoint.toLongString() + " jpHash=" + joinPoint.hashCode() + " ThreadId=" + Thread.currentThread().getId());
//        }

//        try {
//            GenericJsonSerializer.reset();
//            JoinPointData joinPointData = new JoinPointData(joinPoint, throwable);
//            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(joinPointData);
//            System.err.println(joinPoint.toLongString() + " json after : " + json);
////            ESUTGeneratorService.generatedUnitTest(joinPointData);
//        } catch (Exception e) {
//            log.info("Exception AOP : Exception " + joinPoint.toLongString());
//            e.printStackTrace();
//        }
//         UnitTestGeneratorService.generatedUnitTest(joinPoint, null, throwable);
    }
}
