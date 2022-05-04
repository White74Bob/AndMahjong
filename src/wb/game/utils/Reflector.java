package wb.game.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import android.text.TextUtils;
import android.util.Log;

public final class Reflector {
    private static final String FILE_TAG = "Reflector: ";

    public static Object callDefaultConstructor(Class<?> clazz) throws Exception {
        boolean changed = false;
        Constructor<?> constructor = null;
        try {
            constructor = clazz.getDeclaredConstructor();
            if (!constructor.isAccessible()) {
                constructor.setAccessible(true);
                changed = true;
            }
            return constructor.newInstance();
        } finally {
            if (changed) {
                constructor.setAccessible(false);
            }
        }
    }


    /**
     * Sets object member
     *
     * @param instance
     * @param member
     * @param value
     */
    public static void setMember(Object instance, String member, Object value) {
        boolean changed = false;
        Field field = null;
        try {
            field = instance.getClass().getDeclaredField(member);
            if (!field.isAccessible()) {
                field.setAccessible(true);
                changed = true;
            }
            field.set(instance, value);
        } catch (Exception e) {
            Log.e(FILE_TAG,  "Fail to set member:" + member, e);
        } finally {
            if (changed && field != null) {
                field.setAccessible(false);
            }
        }
    }

    /**
     * Gets object member
     *
     * @param instance
     * @param member
     * @return
     */
    public static Object getMember(Object instance, String member) {
        return getMember(instance, instance.getClass(), member);
    }

    /**
     * Gets object member
     *
     * @param instance
     * @param clazz
     * @param member
     * @return
     */
    public static Object getMember(Object instance, Class<?> clazz, String member) {
        if (clazz.equals(Object.class)) {
            return null;
        }
        Object obj = null;
        Field field = null;
        boolean changed = false;
        try {
            field = clazz.getDeclaredField(member);
            if (!field.isAccessible()) {
                field.setAccessible(true);
                changed = true;
            }
            obj = field.get(instance);
        } catch (Exception e) {
            obj = getMember(instance, clazz.getSuperclass(), member);
        } finally {
            if (changed && field != null) {
                field.setAccessible(false);
            }
        }
        return obj;
    }

    public static Object getStaticField(Class<?> clazz, String fieldName)
            throws NoSuchFieldException, IllegalAccessException, IllegalArgumentException {
        if (clazz.equals(Object.class)) {
            return null;
        }
        Object obj = null;
        Field field = null;
        boolean changed = false;
        try {
            field = clazz.getField(fieldName);
            if (!field.isAccessible()) {
                field.setAccessible(true);
                changed = true;
            }
            obj = field.getInt(clazz);
        } finally {
            if (changed && field != null) {
                field.setAccessible(false);
            }
        }
        return obj;
    }

    public static Method getPublicMethod(Class<?> clazz, String methodName, Class<?>[] classTypes) {
        try {
            if (clazz == null) return null;
            return clazz.getMethod(methodName, classTypes);
        } catch (NoSuchMethodException nsme) {
            return null;
        }
    }

    public static String getAnnotationString(final Method method) {
        StringBuilder sb = new StringBuilder();
        Annotation[] array = method.getAnnotations();
        if (array != null && array.length > 0) {
            for (Annotation annotation : array) {
                sb.append(annotation);
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    public static String getParameterTypesString(final Method method) {
        StringBuilder sb = new StringBuilder();
        Class<?>[] array = method.getParameterTypes();
        if (array != null && array.length > 0) {
            for (Class<?> clazz : array) {
                sb.append(clazz);
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    public static Object callStaticMethod(final Class<?> clazz, String methodName,
            Class<?>[] argTypes, Object[] args) throws Exception {
        boolean changed = false;
        Method method = null;
        try {
            method = clazz.getDeclaredMethod(methodName, argTypes);
            if (!method.isAccessible()) {
                method.setAccessible(true);
                changed = true;
            }
            return method.invoke(null, args);
        } catch (NoSuchMethodException e) {
            Class<?> superClazz = clazz.getSuperclass();
            if (superClazz == null) {
                throw new NoSuchMethodException("Class[" + clazz + "] has no method [" + methodName
                        + "]");
            }
            return callStaticMethod(superClazz, methodName, argTypes, args);
        } finally {
            if (changed) {
                method.setAccessible(false);
            }
        }
    }

    public static final String NO_METHOD_TO_DO_THIS = "No method can do this!";

    public static String getMethodName(Object target, String[] methodNames) {
        if (methodNames == null || methodNames.length <= 0) {
            return null;
        }
        for (String methodName : methodNames) {
            if (hasMethod(target, methodName)) {
                return methodName;
            }
        }
        return null;
    }

    public static boolean hasMethod(Object target, String methodName) {
        final Class<?> clazz = target.getClass();
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    public static <T> Object callMethod(Object instance, String methodName, Class<?>[] argTypes,
            Object[] args) throws Exception {
        final Class<?> clazz = instance.getClass();
        return callMethod(clazz, instance, methodName, argTypes, args);
    }

    public static <T> Object callMethod(final Class<?> clazz, final Object instance,
            final String methodName, Class<?>[] argTypes, Object[] args) throws Exception {
        Method method = null;
        boolean changed = false;
        try {
            method = clazz.getDeclaredMethod(methodName, argTypes);
            if (!method.isAccessible()) {
                method.setAccessible(true);
                changed = true;
            }
            return method.invoke(instance, args);
        } catch (NoSuchMethodException nsme) {
            Class<?> superClazz = clazz.getSuperclass();
            if (superClazz == null) {
                throw new NoSuchMethodException("Class[" + clazz + "] has no method [" + methodName
                        + "]");
            }
            return callMethod(superClazz, instance, methodName, argTypes, args);
        } finally {
            if (changed) {
                method.setAccessible(false);
            }
        }
    }

    public static class StaticMethodInfo {
        // For example: Environment.getDataDirectory()
        // Attention: the method has no parameter(s).
        private static final String FORMAT_NAME = "%s.%s()";
        // 举例:
        // Environment.getDataDirectory() - name
        // getDataDirectory - methodName
        // /data - returnValue
        public final String name;
        public final String methodName;
        public final Object returnValue;

        public StaticMethodInfo(final String className, final String methodName, Object returnValue) {
            this.name = String.format(FORMAT_NAME, className, methodName);
            this.methodName = methodName;
            this.returnValue = returnValue;
        }
    }

    private static boolean isSame(Class<?>[] types, Class<?>[] argTypes) {
        if (types == null) {
            return argTypes == null || argTypes.length == 0;
        }
        if (argTypes == null) {
            return types == null || types.length == 0;
        }
        if (types.length != argTypes.length) return false;

        for (int i = 0; i < types.length; i++) {
            if (types[i].equals(argTypes[i])) return false;
        }
        return true;
    }

    private static void appendTypes(StringBuilder sb, Class<?>[] types) {
        if (types == null) {
            sb.append("null");
        } else if (types.length <= 0) {
            sb.append('~');
        } else {
            for (Class<?> clazz : types) {
                sb.append(clazz);
                sb.append(',');
            }
        }
    }

    private static String getParameterTypes(Method method, Class<?>[] argTypes) {
        StringBuilder sb = new StringBuilder();

        sb.append("Method parameter types:\n");
        Class<?>[] types = method.getParameterTypes();
        appendTypes(sb, types);

        sb.append("\nInput types:\n");
        appendTypes(sb, argTypes);

        return sb.toString();
    }

    private static boolean isPublicStatic(final int modifiers) {
        final int mask = Modifier.PUBLIC | Modifier.STATIC;
        return (modifiers & mask) == mask;
    }

    public static StaticMethodInfo[] callPublicStaticMethodsWithPrefix(final Class<?> clazz,
            String methodNamePrefix, Class<?>[] argTypes, Object[] args) {
        final String className = getClassName(clazz);
        String methodName;

        boolean changed = false;
        Method[] methods = clazz.getDeclaredMethods();
        ArrayList<StaticMethodInfo> list = new ArrayList<StaticMethodInfo>();
        Object value = null;
        for (Method method : methods) {
            methodName = method.getName();
            if (!methodName.startsWith(methodNamePrefix)) {
                continue;
            }
            if (!isPublicStatic(method.getModifiers())) {
                continue;
            }
            if (!isSame(method.getParameterTypes(), argTypes)) {
                continue;
            }
            changed = false;
            try {
                if (!method.isAccessible()) {
                    method.setAccessible(true);
                    changed = true;
                }
                value = method.invoke(null, args);
            } catch (Exception e) {
                value = "Failed to invoke method[" + methodName + "] in Class[" + clazz + "]\n" + getParameterTypes(method, argTypes);
            } finally {
                if (changed) {
                    method.setAccessible(false);
                }
            }
            list.add(new StaticMethodInfo(className, methodName, value));
        }
        StaticMethodInfo[] infos = new StaticMethodInfo[list.size()];
        list.toArray(infos);
        return infos;
    }

    public static final String SEPARATOR_DOT = ".";

    public static class StaticFieldInfo {
        // For example: Environment.DIRECTORY_ALARM
        private static final String FORMAT_NAME = "%s.%s";
        // 举例:
        // Environment.DIRECTORY_ALARM - name
        // DIRECTORY_ALARM             - fieldName
        // alarm                       - value
        public final String name;
        public final String fieldName;
        public final Object value;

        public StaticFieldInfo(final String className, final String fieldName, Object value) {
            this.name = String.format(FORMAT_NAME, className, fieldName);
            this.fieldName = fieldName;
            this.value = value;
        }
    }

    public static String getClassName(final Class<?> clazz) {
        String className = clazz.getName();

        // 去掉package name;
        int lastSeparatorIndex = className.lastIndexOf(SEPARATOR_DOT);
        className = className.substring(lastSeparatorIndex + 1);

        return className;
    }

    // 根据field name前缀获得static fields信息
    // 比如，可以获得Environment.DIRECTORY_XXXXX的信息
    public static StaticFieldInfo[] getStaticFieldsByPrefix(final Class<?> clazz,
            final String fieldNamePrefix) {
        final String className = getClassName(clazz);
        String fieldName;

        boolean changed = false;
        Field[] fields = clazz.getDeclaredFields();
        ArrayList<StaticFieldInfo> list = new ArrayList<StaticFieldInfo>();
        Object value = null;
        for (Field field : fields) {
            fieldName = field.getName();
            if (!TextUtils.isEmpty(fieldNamePrefix) && !fieldName.startsWith(fieldNamePrefix)) {
                continue;
            }
            // 如果不是static field, ignore it.
            if ((field.getModifiers() & Modifier.STATIC) != Modifier.STATIC) {
                continue;
            }
            changed = false;
            try {
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                    changed = true;
                }
                value = field.get(null);
            } catch (Exception e) {
                value = "Failed to get value for " + fieldName + "in Class[" + clazz + "]\n" + e;
            } finally {
                if (changed) {
                    field.setAccessible(false);
                }
            }
            list.add(new StaticFieldInfo(className, fieldName, value));
        }
        StaticFieldInfo[] infos = new StaticFieldInfo[list.size()];
        list.toArray(infos);
        return infos;
    }

    // 根据field name后缀获得static fields信息
    // 比如，可以获得Context.XXXXX_SERVICE的信息
    public static StaticFieldInfo[] getStaticFieldsBySuffix(final Class<?> clazz,
            final String fieldNameSuffix) {
        final String className = getClassName(clazz);
        String fieldName;

        boolean changed = false;
        Field[] fields = clazz.getDeclaredFields();
        ArrayList<StaticFieldInfo> list = new ArrayList<StaticFieldInfo>();
        Object value = null;
        for (Field field : fields) {
            fieldName = field.getName();
            if (!TextUtils.isEmpty(fieldNameSuffix) && !fieldName.endsWith(fieldNameSuffix)) {
                continue;
            }
            // 如果不是static field, ignore it.
            if ((field.getModifiers() & Modifier.STATIC) != Modifier.STATIC) {
                continue;
            }
            changed = false;
            try {
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                    changed = true;
                }
                value = field.get(null);
            } catch (Exception e) {
                value = "Failed to get value for " + fieldName + "in Class[" + clazz + "]\n" + e;
            } finally {
                if (changed) {
                    field.setAccessible(false);
                }
            }
            list.add(new StaticFieldInfo(className, fieldName, value));
        }
        StaticFieldInfo[] infos = new StaticFieldInfo[list.size()];
        list.toArray(infos);
        return infos;
    }

    public static StaticFieldInfo[] getPublicStaticFields(final Class<?> clazz) {
        final String className = getClassName(clazz);
        String fieldName;

        boolean changed = false;
        Field[] fields = clazz.getDeclaredFields();
        ArrayList<StaticFieldInfo> list = new ArrayList<StaticFieldInfo>();
        Object value = null;
        for (Field field : fields) {
            if (!isPublicStatic(field.getModifiers())) {
                continue;
            }
            fieldName = field.getName();
            changed = false;
            try {
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                    changed = true;
                }
                value = field.get(null);
            } catch (Exception e) {
                value = "Failed to get value for " + fieldName + "in Class[" + clazz + "]\n" + e;
            } finally {
                if (changed) {
                    field.setAccessible(false);
                }
            }
            list.add(new StaticFieldInfo(className, fieldName, value));
        }
        StaticFieldInfo[] infos = new StaticFieldInfo[list.size()];
        list.toArray(infos);
        return infos;
    }

    // The class stores the label and value of a "static final int" field in a class.
    public static class StaticFinalInt {
        public final String label;
        public final int value;

        public StaticFinalInt(String label, int value) {
            this.label = label;
            this.value = value;
        }
    }

    public static class IntField {
        public final String label;
        public final int value;

        public IntField(String label, int value) {
            this.label = label;
            this.value = value;
        }
    }

    private static boolean isStringInArray(final String[] array, final String input) {
        if (input == null || array == null || array.length <= 0) return false;
        for (String str : array) {
            if (str.equals(input)) return true;
        }
        return false;
    }

    private static int getStaticIntFieldValue(final Class<?> clazz, final Field field) {
        boolean fieldAccessChanged = false;
        try {
            if (!field.isAccessible()) {
                field.setAccessible(true);
                fieldAccessChanged = true;
            }
            return field.getInt(null);
        } catch (IllegalAccessException iae) {
        } catch (IllegalArgumentException iage) {
            //Log.d(TAG, "IllegalArgumentException:" + field.getName());
        } finally {
            if (fieldAccessChanged) {
                field.setAccessible(false);
            }
        }
        return 0;
    }

    public static StaticFinalInt[] getStaticFinalIntFields(final Class<?> clazz,
            final String prefix, final String[] excludedArray) {
        ArrayList<StaticFinalInt> list = new ArrayList<StaticFinalInt>();

        Field[] fields = clazz.getDeclaredFields();
        for(Field field : fields) {
            int modifiers = field.getModifiers();
            String fieldName = field.getName();
            if (Modifier.isStatic(modifiers) &&  Modifier.isFinal(modifiers) &&
                    field.getType() == int.class &&
                    fieldName.startsWith(prefix) && !isStringInArray(excludedArray, fieldName)) {
                int fieldValue = getStaticIntFieldValue(clazz, field);
                list.add(new StaticFinalInt(fieldName, fieldValue));
            }
        }

        StaticFinalInt[] array = new StaticFinalInt[list.size()];
        list.toArray(array);
        return array;
    }

    public static IntField[] getIntFields(final Class<?> clazz) {
        ArrayList<IntField> list = new ArrayList<IntField>();

        Field[] fields = clazz.getDeclaredFields();
        for(Field field : fields) {
            String fieldName = field.getName();
            if (field.getType() == int.class) {
                int fieldValue = getStaticIntFieldValue(clazz, field);
                list.add(new IntField(fieldName, fieldValue));
            }
        }

        IntField[] array = new IntField[list.size()];
        list.toArray(array);
        return array;
    }
}