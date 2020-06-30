/*
 * Copyright (c) 2020. Cisco Systems, Inc
 * All Rights reserved
 */

package com.brainodev.gnfr.esaop;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Utils {

    public static StackTraceElement[] getStackTraceElement() {
        StackTraceElement[] stackTrace;
        try {
            throw new Exception();
        } catch (Exception e) {
            stackTrace = e.getStackTrace();
        }
        return stackTrace;
    }

    public static String replaceLast(String string, String toReplace, String replacement) {
        int pos = string.lastIndexOf(toReplace);
        if (pos > -1) {
            return string.substring(0, pos)
                    + replacement
                    + string.substring(pos + toReplace.length(), string.length());
        } else {
            return string;
        }
    }


    public static Class classForName(String paramName) throws ClassNotFoundException {
        if ("boolean".equals(paramName)) {
            return boolean.class;
        } else if ("byte".equals(paramName)) {
            return byte.class;
        } else if ("char".equals(paramName)) {
            return char.class;
        } else if ("short".equals(paramName)) {
            return short.class;
        } else if ("int".equals(paramName)) {
            return int.class;
        } else if ("long".equals(paramName)) {
            return long.class;
        } else if ("float".equals(paramName)) {
            return float.class;
        } else if ("double".equals(paramName)) {
            return double.class;
        } else if ("void".equals(paramName)) {
            return void.class;
        }
        return Class.forName(paramName);
    }


    static final Set<Class> WRAPPER_TYPES = getWrapperTypes();
    static final Set<String> TYPES = getTypes();

    public static boolean isPrimitive(Class fieldType) {
        return fieldType.isPrimitive() || WRAPPER_TYPES.contains(fieldType);
    }

    static Set<String> getTypes() {
        Set<String> ret = new HashSet();
        ret.add("boolean");
        ret.add("char");
        ret.add("byte");
        ret.add("short");
        ret.add("int");
        ret.add("long");
        ret.add("float");
        ret.add("double");
        return ret;
    }

    static Set<Class> getWrapperTypes() {
        Set<Class> ret = new HashSet();
        ret.add(Boolean.class);
        ret.add(Date.class);
        ret.add(Character.class);
        ret.add(Byte.class);
        ret.add(Short.class);
        ret.add(Integer.class);
        ret.add(Long.class);
        ret.add(Float.class);
        ret.add(Double.class);
        ret.add(Void.class);
        ret.add(Class.class);
        ret.add(String.class);
        ret.add(UUID.class);
        return ret;
    }
}
