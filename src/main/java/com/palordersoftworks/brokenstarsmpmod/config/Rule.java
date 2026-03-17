package com.palordersoftworks.brokenstarsmpmod.config;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Rule {
    String desc() default "";
    String[] options() default {};
    boolean strict() default false;
}