package com.hmdp.exception;

public class LuaScriptException extends RuntimeException{
    public LuaScriptException() {
        super("Lua脚本执行出错！");
    }
}
