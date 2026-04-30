package com.palordersoftworks.luaj.sandboxing;

import party.iroiro.luajava.luajit.LuaJit;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public final class SandboxWorker {
    public static void main(String[] args) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(System.in));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(System.out))) {

            while (true) {
                final String code;
                try {
                    code = readString(in);
                } catch (EOFException eof) {
                    break;
                }

                Result result = runOnce(code);

                out.writeBoolean(result.success);
                out.writeBoolean(result.suspicious);
                out.writeBoolean(result.timeout);
                out.writeInt(result.exitCode);
                writeString(out, result.output);
                writeString(out, result.error);
                out.flush();
            }
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            System.err.flush();
        }
    }

    private static Result runOnce(String code) {
        if (Main.isSuspicious(code)) {
            return new Result(
                    false,
                    true,
                    false,
                    42,
                    "",
                    "suspicious behavior detected: " + Main.suspiciousReason(code)
            );
        }

        LuaJit lua = null;
        PrintStream previousOut = System.out;
        PrintStream scriptOut = null;
        ByteArrayOutputStream scriptBuffer = new ByteArrayOutputStream();

        try {
            scriptOut = new PrintStream(scriptBuffer, true, StandardCharsets.UTF_8.name());
            System.setOut(scriptOut);

            lua = new LuaJit();
            lua.openLibraries();
            lua.run(Main.sandboxPrelude());
            lua.run(code);

            scriptOut.flush();

            return new Result(
                    true,
                    false,
                    false,
                    0,
                    new String(scriptBuffer.toByteArray(), StandardCharsets.UTF_8),
                    null
            );
        } catch (Throwable t) {
            if (scriptOut != null) {
                scriptOut.flush();
            }
            return new Result(
                    false,
                    false,
                    false,
                    1,
                    new String(scriptBuffer.toByteArray(), StandardCharsets.UTF_8),
                    stackTraceToString(t)
            );
        } finally {
            System.setOut(previousOut);

            if (scriptOut != null) {
                scriptOut.close();
            }

            if (lua != null) {
                try {
                    lua.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String stackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            t.printStackTrace(pw);
        }
        return sw.toString();
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0) {
            throw new IOException("negative request length");
        }
        if (len == 0) {
            return "";
        }

        byte[] bytes = in.readNBytes(len);
        if (bytes.length != len) {
            throw new EOFException("truncated request");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private record Result(boolean success, boolean suspicious, boolean timeout, int exitCode, String output, String error) {
    }
}