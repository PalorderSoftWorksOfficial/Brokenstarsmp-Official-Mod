package com.palordersoftworks.brokenstarsmpmod.helpers;

public class CreativeContext {
    public static final ThreadLocal<Boolean> IN_CREATIVE = ThreadLocal.withInitial(() -> false);
}