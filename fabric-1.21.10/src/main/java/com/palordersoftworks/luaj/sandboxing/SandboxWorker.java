package com.palordersoftworks.luaj.sandboxing;

import party.iroiro.luajava.luajit.LuaJit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class SandboxWorker {
    public static void main(String[] args) {
        LuaJit lua = null;
        try {
            String code = readAll();
            if (code == null) {
                System.exit(0);
                return;
            }

            if (Main.isSuspicious(code)) {
                System.out.println("suspicious behavior detected: " + Main.suspiciousReason(code));
                System.out.flush();
                System.exit(42);
                return;
            }

            lua = new LuaJit();
            lua.openLibraries();
            lua.run(Main.sandboxPrelude());
            lua.run(code);
            System.out.flush();
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            System.out.flush();
            System.exit(1);
        } finally {
            if (lua != null) {
                try {
                    lua.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String readAll() throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        StringBuilder code = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            code.append(line).append('\n');
        }
        if (code.length() == 0) {
            return null;
        }
        return code.toString();
    }
}