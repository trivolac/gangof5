package com.example.util;

import org.json.simple.JSONObject;

public class ResponseUtil {
    public static JSONObject generateSuccessJsonObject(String msg){
        final JSONObject jsonObject = new JSONObject();
        jsonObject.put("status", "success");
        jsonObject.put("message", msg);
        return jsonObject;
    }

    public static JSONObject generateErrorJsonObject(String msg){
        final JSONObject jsonObject = new JSONObject();
        jsonObject.put("status", "error");
        jsonObject.put("message", msg);
        return jsonObject;
    }
}
