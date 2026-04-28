package com.palordersoftworks.luaj.sandboxing;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Main {
    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("\\bbase64\\b", Pattern.CASE_INSENSITIVE), "base64"),
            new Rule(Pattern.compile("\\bfrombase64\\b", Pattern.CASE_INSENSITIVE), "fromBase64"),
            new Rule(Pattern.compile("\\bunbase64\\b", Pattern.CASE_INSENSITIVE), "unBase64"),
            new Rule(Pattern.compile("\\bjava\\.util\\.base64\\b", Pattern.CASE_INSENSITIVE), "java.util.Base64"),
            new Rule(Pattern.compile("\\bdatatypeconverter\\b", Pattern.CASE_INSENSITIVE), "DatatypeConverter"),
            new Rule(Pattern.compile("\\bgetdecoder\\b", Pattern.CASE_INSENSITIVE), "getDecoder"),
            new Rule(Pattern.compile("\\bgetencoder\\b", Pattern.CASE_INSENSITIVE), "getEncoder"),
            new Rule(Pattern.compile("\\bdecode\\s*\\(", Pattern.CASE_INSENSITIVE), "decode(...)"),
            new Rule(Pattern.compile("\\bluajava\\b", Pattern.CASE_INSENSITIVE), "luajava"),
            new Rule(Pattern.compile("\\bffi\\b", Pattern.CASE_INSENSITIVE), "ffi"),
            new Rule(Pattern.compile("\\bpackage\\s*\\.\\s*loadlib\\b", Pattern.CASE_INSENSITIVE), "package.loadlib"),
            new Rule(Pattern.compile("\\bloadlib\\b", Pattern.CASE_INSENSITIVE), "loadlib"),
            new Rule(Pattern.compile("\\bjit\\b", Pattern.CASE_INSENSITIVE), "jit")
    );

    private Main() {
    }

    public static boolean isSuspicious(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String normalized = code.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    public static String suspiciousReason(String code) {
        if (code == null || code.isBlank()) {
            return "unknown";
        }
        String normalized = code.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(normalized).find()) {
                return rule.reason();
            }
        }
        return "unknown";
    }

    public static String sandboxPrelude() {
        return """
                local keep = {
                  assert = assert,
                  error = error,
                  ipairs = ipairs,
                  next = next,
                  pairs = pairs,
                  pcall = pcall,
                  select = select,
                  tonumber = tonumber,
                  tostring = tostring,
                  type = type,
                  xpcall = xpcall,
                  math = math,
                  string = string,
                  table = table
                }

                if utf8 ~= nil then
                  keep.utf8 = utf8
                end

                local function readonly(t)
                  return setmetatable({}, {
                    __index = t,
                    __newindex = function()
                      error("readonly table", 2)
                    end,
                    __metatable = false
                  })
                end

                keep.math = readonly(keep.math)

                local safe_string = {}
                for k, v in pairs(string) do
                  if k ~= "dump" then
                    safe_string[k] = v
                  end
                end
                keep.string = readonly(safe_string)
                keep.table = readonly(table)

                if keep.utf8 ~= nil then
                  keep.utf8 = readonly(keep.utf8)
                end

                local g = _G

                for k, v in pairs(keep) do
                  g[k] = v
                end

                g._G = g
                g.print = print
                g.warn = warn

                g.io = nil
                g.os = nil
                g.debug = nil
                g.package = nil
                g.dofile = nil
                g.collectgarbage = nil
                g.load = nil
                g.loadfile = nil
                g.loadstring = nil
                g.require = nil
                g.module = nil
                g.luajava = nil
                g.jit = nil
                g.setmetatable = nil
                g.getmetatable = nil
                g.rawset = nil
                g.rawget = nil
                g.rawequal = nil

                setmetatable(g, {
                  __index = function()
                    return nil
                  end,
                  __newindex = function()
                    error("attempt to create global", 2)
                  end,
                  __metatable = false
                })
                """;
    }

    private record Rule(Pattern pattern, String reason) {
    }
}