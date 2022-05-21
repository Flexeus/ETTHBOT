package com.uospd.annotations;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Callback{
    String value() default "";
    int timeout() default 0;
    boolean globalTimeout() default false;
    boolean commutatorCallback() default false;
}
