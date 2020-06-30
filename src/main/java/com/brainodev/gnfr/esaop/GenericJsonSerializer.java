/*
 * Copyright (c) 2020. Cisco Systems, Inc
 * All Rights reserved
 */

package com.brainodev.gnfr.esaop;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GenericJsonSerializer extends JsonSerializer<Object> {

    public static final String IMPLEMENTATION_TYPE_SUFFIX = "Impl";

    static ObjectMapper mapper = new ObjectMapper();

    static List<Integer> listAddedHash = new ArrayList<>();

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Object.class, new GenericJsonSerializer());
        mapper.registerModule(module);
    }

    public static void reset() {
        listAddedHash.clear();
    }

    @Override
    public void serialize(Object objToSerialize, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject(objToSerialize);

        boolean primitiveObjWritten = writePrimitiveFieldValue(jgen, "primitiveValue", objToSerialize);

        if (!primitiveObjWritten) {
            for (Field field : getAllDeclaredFields(objToSerialize.getClass())) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    try {
                        field.setAccessible(true);

                        String jsonPropertyName = field.getName();
                        if (field.isAnnotationPresent(JsonProperty.class)) {
                            JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
                            jsonPropertyName = jsonProperty.value();
                        }

                        Object fieldVal = field.get(objToSerialize);

                        if (fieldVal == null && getMethod(field) != null) {
                            fieldVal = get(objToSerialize, field.getName());
                        }

                        if (fieldVal == null) {
                            jgen.writeNullField(jsonPropertyName);
                            continue;
                        }

                        if (field.getType().isEnum()) {
                            jgen.writeStringField(jsonPropertyName, fieldVal.toString());
                        } else if (field.getType().isPrimitive()) {
                            writePrimitiveFieldValue(jgen, jsonPropertyName, fieldVal);
                        } else if (field.getType().isArray() || fieldVal instanceof List) {

                            jgen.writeArrayFieldStart(jsonPropertyName);

                            try {
                                if (fieldVal.getClass().getComponentType() != null && fieldVal.getClass().getComponentType().isPrimitive()) {
                                    log.error("Primitive array : " + objToSerialize.getClass().getCanonicalName() + " : " + jsonPropertyName);
                                } else {
                                    Object[] vals = field.getType().isArray() ? (Object[]) fieldVal : ((List) fieldVal).toArray();
                                    for (Object obj : vals) {
                                        try {
                                            if (obj instanceof Map) {
                                                jgen.writeStartObject();
                                                Map objVal = (Map) obj;
                                                objVal.forEach((k, v) -> {
                                                    try {
                                                        jgen.writeObjectField(k.toString(), v);
//                                                        jgen.writeObjectField("K", 'V');
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                });
                                                jgen.writeEndObject();
                                            } else {
                                                boolean primitiveObjectWritten = writePrimitiveValue(jgen, obj);
                                                if (!primitiveObjectWritten) {

                                                    int hash = -1;

                                                    try {
                                                        hash = obj.hashCode();
                                                    } catch (NullPointerException e) {
                                                        // case of org.eclipse.jetty.http.HttpFields.hashCode
                                                    }

                                                    if (hash != -1) {
                                                        if (!listAddedHash.contains(hash)) {
                                                            listAddedHash.add(hash);
                                                            jgen.writeObject(obj);
                                                        } else {
                                                            // jgen.writeString("HashAlreadyExist" + hash);
                                                        }
                                                    }
                                                    // log.info("INGORE SERIALIZATION  : " + obj);
                                                }
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            jgen.writeEndArray();

//                            if (((Object[]) fieldVal).length > 0) {
//                                jgen.writeArrayFieldStart(jsonPropertyName);
//                                jgen.writeStartArray();
////                            jgen.writeStartArray(fieldVal);
//                                for (Object obj : (Object[]) fieldVal) {
//                                    jgen.writeObject(obj);
//                                }
//                                jgen.writeEndArray();
//                                // jgen.writeObjectField(jsonPropertyName, fieldVal);
//                            }

                        } else if (fieldVal instanceof Map) {
                            jgen.writeFieldName(jsonPropertyName);
                            jgen.writeStartObject(jsonPropertyName);
                            Map mapVal = (Map) fieldVal;
                            mapVal.forEach((k, v) -> {
                                try {
                                    jgen.writeObjectField(k.toString(), v);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                            jgen.writeEndObject();
                        } else {
                            /**********************/
                            /** Business object ***/
                            /**********************/

                            int hash = -1;

                            try {
                                hash = fieldVal.hashCode();
                            } catch (NullPointerException e) {
                                // case of org.eclipse.jetty.http.HttpFields.hashCode
                            }

                            if (hash != -1) {
                                if (!listAddedHash.contains(hash)) {
                                    listAddedHash.add(hash);
                                    jgen.writeObjectField(field.getName(), fieldVal);
                                } else {
                                    // jgen.writeNumberField(field.getName() + "HashAlreadyExist", hash);
                                }
                            }
                        }
                    } catch (InaccessibleObjectException e) {
                        // Unable to make field private final java.lang.String java.lang.module.ModuleDescriptor.name accessible: module java.base does not "opens java.lang.module" to unnamed module @5f31385e
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        // jgen.writeNumberField("id", null);
        // jgen.writeStringField("itemName", null);
        // jgen.writeNumberField("owner", null);
        if (jgen.getOutputContext().inObject()) {
            jgen.writeEndObject();
        } else {
            jgen.writeEndArray();
        }

    }

    private boolean writePrimitiveFieldValue(JsonGenerator jgen, String jsonPropertyName, Object fieldVal) throws IOException {
        if (fieldVal == null) {
            jgen.writeNullField(jsonPropertyName);
        } else if (fieldVal instanceof String) {
            jgen.writeStringField(jsonPropertyName, (String) fieldVal);
            return true;
        } else if (fieldVal instanceof Class) {
            jgen.writeStringField(jsonPropertyName, ((Class) fieldVal).getCanonicalName());
            return true;
        } else if (fieldVal instanceof ClassLoader) {
            jgen.writeStringField(jsonPropertyName, ((ClassLoader) fieldVal).getName());
            return true;
        } else if (fieldVal instanceof Boolean) {
            jgen.writeBooleanField(jsonPropertyName, (Boolean) fieldVal);
            return true;
        } else if (fieldVal instanceof Integer) {
            jgen.writeNumberField(jsonPropertyName, (Integer) fieldVal);
            return true;
        } else if (fieldVal instanceof Long) {
            jgen.writeNumberField(jsonPropertyName, (Long) fieldVal);
            return true;
        } else if (fieldVal instanceof Double) {
            jgen.writeNumberField(jsonPropertyName, (Double) fieldVal);
            return true;
        } else if (fieldVal instanceof Float) {
            jgen.writeNumberField(jsonPropertyName, (Float) fieldVal);
            return true;
        } else if (fieldVal instanceof BigDecimal) {
            jgen.writeNumberField(jsonPropertyName, (BigDecimal) fieldVal);
            return true;
        } else if (fieldVal instanceof UUID) {
            jgen.writeStringField(jsonPropertyName, fieldVal.toString());
            return true;
        }
        return false;
    }

    private boolean writePrimitiveValue(JsonGenerator jgen, Object fieldVal) throws IOException {
        if (fieldVal == null) {
            jgen.writeNull();
        } else if (fieldVal instanceof String) {
            jgen.writeString((String) fieldVal);
            return true;
        } else if (fieldVal instanceof Class) {
            jgen.writeString(((Class) fieldVal).getCanonicalName());
            return true;
        } else if (fieldVal instanceof ClassLoader) {
            jgen.writeString(((ClassLoader) fieldVal).getName());
            return true;
        } else if (fieldVal instanceof Boolean) {
            jgen.writeBoolean((Boolean) fieldVal);
            return true;
        } else if (fieldVal instanceof Integer) {
            jgen.writeNumber((Integer) fieldVal);
            return true;
        } else if (fieldVal instanceof Long) {
            jgen.writeNumber((Long) fieldVal);
            return true;
        } else if (fieldVal instanceof Double) {
            jgen.writeNumber((Double) fieldVal);
            return true;
        } else if (fieldVal instanceof Float) {
            jgen.writeNumber((Float) fieldVal);
            return true;
        } else if (fieldVal instanceof BigDecimal) {
            jgen.writeNumber((BigDecimal) fieldVal);
            return true;
        } else if (fieldVal instanceof UUID) {
            jgen.writeString(fieldVal.toString());
            return true;
        }
        return false;
    }

    private static List<Field> getAllDeclaredFields(Class returnTypeClass) {
        ArrayList<Field> listField = new ArrayList<Field>();
        if (returnTypeClass != null) {
            listField.addAll(Arrays.asList(returnTypeClass.getDeclaredFields()));
            listField.addAll(getAllDeclaredFields(returnTypeClass.getSuperclass()));
        }
        // listField.addAll(Arrays.asList(returnTypeClass.getSuperclass().getDeclaredFields()));
        return listField;
    }


    private static HashMap<Field, Method> methods = new HashMap<Field, Method>();

    public static Object get(Object instance, String fieldName/* , Object... args */) {
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            Method method = getMethod(field);
            Object val = method.invoke(/* instance */null, instance/* args */);
            return val;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Method getMethod(Field field) {
        return methods.get(field);
    }

}