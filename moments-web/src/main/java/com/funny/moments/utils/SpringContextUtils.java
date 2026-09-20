package com.funny.moments.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;


/**
 * @Author lvzihao
 * @Description //TODO
 * @Date 10:41 2022/1/1
 * @Param
 * @return
 **/
@Component
public class SpringContextUtils implements ApplicationContextAware {

    private static ApplicationContext applicationContext = null;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
        SpringContextUtils.applicationContext = applicationContext;
    }

    public static Object getBean(String beanName) {
        Object obj = null;
        if (null != applicationContext) {
            obj = applicationContext.getBean(beanName);
        }
        return obj;
    }

    public static Object getBean(String beanName, Object... args) {
        Object obj = null;
        if (null != applicationContext) {
            obj = applicationContext.getBean(beanName, args);
        }
        return obj;
    }

    public static <T> T getBean(Class<T> beanClass) {
        T obj = null;
        if (null != applicationContext) {
            obj = applicationContext.getBean(beanClass);
        }
        return obj;
    }

    public static <T> T getBean(String beanName, Class<T> clsType) {
        T obj = null;
        if (null != applicationContext) {
            obj = applicationContext.getBean(beanName, clsType);
        }
        return obj;
    }

    public static <T> T getBean(Class<T> requiredType, Object... args) {
        T obj = null;
        if (null != applicationContext) {
            obj = applicationContext.getBean(requiredType, args);
        }
        return obj;
    }
}
