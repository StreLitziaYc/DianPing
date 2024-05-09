package com.hmdp.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class BeanHelper implements ApplicationContextAware {
    private static ApplicationContext context;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    /**
     * 根据name获取Bean
     * @param name Bean的名字
     * @return Bean
     */
    public static Object getBean(String name) {
        return context.getBean(name);
    }

    /**
     * 根据class获取bean
     * @param clazz 类型
     * @return bean
     * @param <T> bean的类型
     */
    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }

    /**
     * 根据name和class获取bean
     * @param name 名字
     * @param clazz 类型
     * @return bean
     * @param <T> bean的类型
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return context.getBean(name, clazz);
    }
}
