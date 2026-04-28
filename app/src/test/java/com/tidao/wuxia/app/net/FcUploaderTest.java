package com.tidao.wuxia.app.net;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FcUploaderTest {

    @Test
    public void buildUploadBody_includesEmail() throws Exception {
        JSONObject roleParams = new JSONObject();
        roleParams.put("uin", "10001");

        JSONObject body = FcUploader.buildUploadBody(
                "角色",
                "cookie=value",
                roleParams,
                "SCT1234567890",
                "owner-1",
                "friend@qq.com");

        assertEquals("角色", body.getString("name"));
        assertEquals("cookie=value", body.getString("cookies"));
        assertEquals("SCT1234567890", body.getString("sckey"));
        assertEquals("owner-1", body.getString("owner"));
        assertEquals("friend@qq.com", body.getString("email"));
        assertEquals("10001", body.getJSONObject("role_params").getString("uin"));
    }

    @Test
    public void buildUploadBody_nullEmailSendsEmptyString() throws Exception {
        JSONObject body = FcUploader.buildUploadBody(
                "角色",
                "cookie=value",
                new JSONObject(),
                "SCT1234567890",
                "owner-1",
                null);

        assertEquals("", body.getString("email"));
    }
}
