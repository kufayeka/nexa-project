package nexa.framework.runtime.domain.deployment.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NexaClassLoader extends ClassLoader {
    private final Map<String, byte[]> classBytes = new ConcurrentHashMap<>();

    public NexaClassLoader(ClassLoader parent) {
        super(parent);
    }

    public void registerClass(String className, byte[] bytes) {
        classBytes.put(className.replace('/', '.'), bytes);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classBytes.get(name);
        if (bytes != null) {
            return defineClass(name, bytes, 0, bytes.length);
        }
        return super.findClass(name);
    }
}
