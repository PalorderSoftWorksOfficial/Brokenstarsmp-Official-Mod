package com.palordersoftworks.economycraft.util;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

public final class IdentifierCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Class<?> ID_CLASS;
    private static final Constructor<?> ID_CONSTRUCTOR_TWO;
    private static final Constructor<?> ID_CONSTRUCTOR_ONE;
    private static final Method ID_FACTORY_ONE;
    private static final Method ID_FACTORY_TWO;
    private static final Method REGISTRY_CONTAINS_ID;
    private static final Method REGISTRY_GET_OPTIONAL_VALUE;
    private static final Method RESOURCE_KEY_CREATE;
    private static final Method RESOURCE_KEY_IDENTIFIER;
    private static final Method HOLDER_VALUE;
    private static final Method EITHER_LEFT;
    private static final Method EITHER_RIGHT;

    static {
        Class<?> idClass = null;
        Constructor<?> idConstructorTwo = null;
        Constructor<?> idConstructorOne = null;
        Method idFactoryOne = null;
        Method idFactoryTwo = null;
        Method registryContainsId = null;
        Method registryGetOptionalValue = null;
        Method resourceKeyCreate = null;
        Method resourceKeyIdentifier = null;
        Method holderValue = null;
        Method eitherLeft = null;
        Method eitherRight = null;
        // 1.21+: Registry.getKey(T) returns Optional<RegistryKey<T>>; Identifier constructors are private.
        // Use a vanilla id sample so we resolve Identifier.of / tryParse via reflection reliably.
        Object idSample = Identifier.of("minecraft", "air");
        idClass = idSample.getClass();
        for (Constructor<?> constructor : idClass.getConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length == 2 && params[0] == String.class && params[1] == String.class) {
                idConstructorTwo = constructor;
            } else if (params.length == 1 && params[0] == String.class) {
                idConstructorOne = constructor;
            }
        }
        for (Method method : idClass.getMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!idClass.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1 && params[0] == String.class) {
                idFactoryOne = method;
            } else if (params.length == 2 && params[0] == String.class && params[1] == String.class) {
                idFactoryTwo = method;
            }
        }
        if (idConstructorTwo == null && idConstructorOne == null && idFactoryOne == null && idFactoryTwo == null) {
            throw new ExceptionInInitializerError("Identifier constructor not found");
        }

        registryContainsId = findRegistryContainsId(idClass);
        registryGetOptionalValue = findRegistryGetOptionalValue(idClass);
        resourceKeyCreate = findResourceKeyCreate(idClass);
        resourceKeyIdentifier = findResourceKeyIdentifier(idClass);
        holderValue = findHolderValue();
        Method[] eitherMethods = findEitherMethods();
        if (eitherMethods != null) {
            eitherLeft = eitherMethods[0];
            eitherRight = eitherMethods[1];
        }

        ID_CLASS = idClass;
        ID_CONSTRUCTOR_TWO = idConstructorTwo;
        ID_CONSTRUCTOR_ONE = idConstructorOne;
        ID_FACTORY_ONE = idFactoryOne;
        ID_FACTORY_TWO = idFactoryTwo;
        REGISTRY_CONTAINS_ID = registryContainsId;
        REGISTRY_GET_OPTIONAL_VALUE = registryGetOptionalValue;
        RESOURCE_KEY_CREATE = resourceKeyCreate;
        RESOURCE_KEY_IDENTIFIER = resourceKeyIdentifier;
        HOLDER_VALUE = holderValue;
        EITHER_LEFT = eitherLeft;
        EITHER_RIGHT = eitherRight;
    }

    private IdentifierCompat() {}

    public static Id tryParse(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String namespace;
        String path;
        int idx = trimmed.indexOf(':');
        if (idx >= 0) {
            namespace = trimmed.substring(0, idx);
            path = trimmed.substring(idx + 1);
        } else {
            namespace = "minecraft";
            path = trimmed;
        }
        if (!isValidNamespace(namespace) || !isValidPath(path)) {
            return null;
        }
        return new Id(construct(namespace, path), namespace, path);
    }

    public static Id withDefaultNamespace(String path) {
        if (!isValidPath(path)) {
            return null;
        }
        return new Id(construct("minecraft", path), "minecraft", path);
    }

    public static Id fromNamespaceAndPath(String namespace, String path) {
        if (!isValidNamespace(namespace) || !isValidPath(path)) {
            return null;
        }
        return new Id(construct(namespace, path), namespace, path);
    }

    public static Id wrap(Object value) {
        if (value == null) {
            return null;
        }
        return parseFromString(value.toString(), value);
    }

    @SuppressWarnings("unchecked")
    public static <T> T unwrap(Id id) {
        return id == null ? null : (T) id.handle();
    }

    public static boolean registryContainsKey(Registry<?> registry, Id id) {
        if (id == null) {
            return false;
        }
        // Fast path for modern Yarn: avoid reflection overload mistakes.
        if (id.handle() instanceof net.minecraft.util.Identifier ident) {
            try {
                return registry.containsId(ident);
            } catch (Throwable ignored) {
                // fall back to reflection below
            }
        }
        return (boolean) invoke(REGISTRY_CONTAINS_ID, registry, id.handle());
    }

    public static <T> Optional<T> registryGetOptional(Registry<T> registry, Id id) {
        if (id == null) {
            return Optional.empty();
        }
        // Fast path for modern Yarn: avoid accidentally calling getKey(T) (which returns Optional<RegistryKey<T>>).
        if (id.handle() instanceof net.minecraft.util.Identifier ident) {
            try {
                return registry.getOptionalValue(ident);
            } catch (Throwable ignored) {
                // fall back to reflection below
            }
        }
        @SuppressWarnings("unchecked")
        Optional<Object> result = (Optional<Object>) invoke(REGISTRY_GET_OPTIONAL_VALUE, registry, id.handle());
        if (result.isEmpty()) {
            return Optional.empty();
        }
        Object value = unwrapRegistryValue(result.get());
        if (value == null) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        T direct;
        try {
            direct = (T) value;
        } catch (ClassCastException e) {
            LOGGER.error("[EconomyCraft] Registry lookup for {} returned unexpected value {} (class {}) from {}",
                    id.asString(), value, value.getClass().getName(), registry, e);
            return Optional.empty();
        }
        return Optional.of(direct);
    }

    public static <T> RegistryKey<T> createResourceKey(RegistryKey<? extends Registry<T>> registryKey, Id id) {
        if (id == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        RegistryKey<T> result = (RegistryKey<T>) invokeStatic(RESOURCE_KEY_CREATE, registryKey, id.handle());
        return result;
    }

    public static Id fromResourceKey(RegistryKey<?> key) {
        if (key == null) {
            return null;
        }
        Object value = invoke(RESOURCE_KEY_IDENTIFIER, key);
        return wrap(value);
    }

    private static Id parseFromString(String raw, Object handle) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String namespace;
        String path;
        int idx = raw.indexOf(':');
        if (idx >= 0) {
            namespace = raw.substring(0, idx);
            path = raw.substring(idx + 1);
        } else {
            namespace = "minecraft";
            path = raw;
        }
        if (!isValidNamespace(namespace) || !isValidPath(path)) {
            return null;
        }
        return new Id(handle, namespace, path);
    }

    private static Object construct(String namespace, String path) {
        String combined = namespace + ":" + path;
        try {
            if (ID_CONSTRUCTOR_TWO != null) {
                return ID_CONSTRUCTOR_TWO.newInstance(namespace, path);
            }
            if (ID_FACTORY_TWO != null) {
                return ID_FACTORY_TWO.invoke(null, namespace, path);
            }
            if (ID_CONSTRUCTOR_ONE != null) {
                return ID_CONSTRUCTOR_ONE.newInstance(combined);
            }
            if (ID_FACTORY_ONE != null) {
                return ID_FACTORY_ONE.invoke(null, combined);
            }
            throw new IllegalStateException("Identifier constructor not found");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object unwrapRegistryValue(Object value) {
        if (value == null) return null;
        Object v = value;

        while (v instanceof RegistryEntry<?> re) {
            com.mojang.datafixers.util.Either<?, ?> either = re.getKeyOrValue();
            if (either.right().isPresent()) {
                v = either.right().get();
            } else {
                break;
            }
            if (v == null) return null;
        }

        if (EITHER_LEFT != null && EITHER_RIGHT != null && isEither(v)) {
            Optional<?> left = invokeEitherOptional(EITHER_LEFT, v);
            if (left != null && left.isPresent()) return unwrapRegistryValue(left.get());

            Optional<?> right = invokeEitherOptional(EITHER_RIGHT, v);
            if (right != null && right.isPresent()) return unwrapRegistryValue(right.get());

            return null;
        }

        return v;
    }

    private static boolean isEither(Object value) {
        return value != null && value.getClass().getName().equals("com.mojang.datafixers.util.Either");
    }

    @SuppressWarnings("unchecked")
    private static Optional<?> invokeEitherOptional(Method method, Object target) {
        if (method == null || target == null) {
            return null;
        }
        Object result = invoke(method, target);
        if (result instanceof Optional<?> optional) {
            return optional;
        }
        return null;
    }

    private static boolean isValidNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return false;
        }
        for (int i = 0; i < namespace.length(); i++) {
            char c = namespace.charAt(i);
            if (!(c == '_' || c == '-' || c == '.' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!(c == '_' || c == '-' || c == '.' || c == '/' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    private static Method findRegistryMethod(Class<?> registryClass, Class<?> returnType, Class<?> idClass) {
        Method assignableMatch = null;
        for (Method method : registryClass.getMethods()) {
            if (method.getParameterCount() != 1 || !returnType.equals(method.getReturnType())) {
                continue;
            }
            Class<?> param = method.getParameterTypes()[0];
            if (param.equals(idClass)) {
                return method;
            }
            if (param.isAssignableFrom(idClass)) {
                assignableMatch = method;
            }
        }
        if (assignableMatch != null) {
            return assignableMatch;
        }
        throw new ExceptionInInitializerError("Registry method not found");
    }

    private static Method findRegistryContainsId(Class<?> idClass) {
        // Prefer the 1.21+ containsId(Identifier) method so we don't accidentally match contains(RegistryKey).
        for (Method method : Registry.class.getMethods()) {
            if (!method.getName().equals("containsId")) continue;
            if (method.getParameterCount() != 1) continue;
            if (!boolean.class.equals(method.getReturnType())) continue;
            if (method.getParameterTypes()[0].equals(idClass)) {
                return method;
            }
        }
        // Fallback to generic search.
        return findRegistryMethod(Registry.class, boolean.class, idClass);
    }

    private static Method findRegistryGetOptionalValue(Class<?> idClass) {
        // Prefer the 1.21+ getOptionalValue(Identifier) method so we don't accidentally match getKey(T) -> Optional<RegistryKey<T>>.
        for (Method method : Registry.class.getMethods()) {
            if (!method.getName().equals("getOptionalValue")) continue;
            if (method.getParameterCount() != 1) continue;
            if (!Optional.class.equals(method.getReturnType())) continue;
            if (method.getParameterTypes()[0].equals(idClass)) {
                return method;
            }
        }
        // Fallback to generic search.
        return findRegistryMethod(Registry.class, Optional.class, idClass);
    }

    private static Method findNoArgMethod(Class<?> type, String... names) {
        for (String name : names) {
            Method method = null;
            try {
                method = type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // try declared method instead
            }
            if (method == null) {
                try {
                    method = type.getDeclaredMethod(name);
                    method.setAccessible(true);
                } catch (NoSuchMethodException ignored) {
                    // try next name
                } catch (RuntimeException ignored) {
                    // access failure; try next name
                }
            }
            if (method == null) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType.equals(void.class)
                    || returnType.equals(boolean.class)
                    || Optional.class.isAssignableFrom(returnType)
                    || RegistryKey.class.isAssignableFrom(returnType)) {
                continue;
            }
            return method;
        }
        return null;
    }

    private static Method findResourceKeyCreate(Class<?> idClass) {
        for (Method method : RegistryKey.class.getMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getParameterCount() == 2 && RegistryKey.class.equals(method.getReturnType())) {
                Class<?>[] params = method.getParameterTypes();
                if (RegistryKey.class.equals(params[0]) && params[1].isAssignableFrom(idClass)) {
                    return method;
                }
            }
        }
        throw new ExceptionInInitializerError("RegistryKey.of method not found");
    }

    private static Method findResourceKeyIdentifier(Class<?> idClass) {
        for (Method method : RegistryKey.class.getMethods()) {
            if (method.getParameterCount() == 0 && idClass.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        }
        throw new ExceptionInInitializerError("RegistryKey identifier method not found");
    }

    private static Method findHolderValue() {
        return findNoArgMethod(RegistryEntry.class, "value", "get");
    }

    private static Method[] findEitherMethods() {
        try {
            Class<?> eitherClass = Class.forName("com.mojang.datafixers.util.Either");
            Method left = eitherClass.getMethod("left");
            Method right = eitherClass.getMethod("right");
            return new Method[] { left, right };
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return null;
        }
    }

    private static Object invokeStatic(Method method, Object... args) {
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Id(Object handle, String namespace, String path) {
        public String asString() {
            return namespace + ":" + path;
        }
    }
}
