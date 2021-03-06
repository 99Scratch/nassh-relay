/*
 * nassh-relay - Relay Server for tunneling ssh through a http endpoint
 * 
 * Website: https://github.com/zyclonite/nassh-relay
 *
 * Copyright 2014-2016   zyclonite    networx
 *                       http://zyclonite.net
 * Developer: Lukas Prettenthaler
 */
package net.zyclonite.nassh.handler;

import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import net.zyclonite.nassh.model.AuthSession;
import net.zyclonite.nassh.util.AuthSessionManager;
import net.zyclonite.nassh.util.Constants;
import net.zyclonite.nassh.util.WebHelper;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Scanner;

/**
 *
 * @author zyclonite
 */
public class CookieHandler implements Handler<RoutingContext> {

    private static Logger logger = LoggerFactory.getLogger(CookieHandler.class);
    private static final String STATIC_FILE = "/webroot/auth.html";
    private final boolean authentication;
    private final int sessionTTL;
    private final JsonObject auth;

    public CookieHandler(final JsonObject config) {
        this.authentication = config.getBoolean("authentication", true);
        this.sessionTTL = config.getInteger("auth-session-timeout", 600);
        this.auth = config.getJsonObject("auth");
    }

    @Override
    public void handle(final RoutingContext context) {
        logger.debug("got request");
        final HttpServerRequest request = context.request();
        request.response().putHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        request.response().putHeader("Pragma", "no-cache");
        if (request.params().contains("ext") && request.params().contains("path")) {
            final String ext = request.params().get("ext");
            final String path = request.params().get("path");
            if(!authentication){
                request.response().putHeader("location", "chrome-extension://" + ext + "/" + path + "#anonymous@" + request.host());
                request.response().setStatusCode(302);
                request.response().end();
                return;
            }
            final AuthSession authSession = WebHelper.validateCookie(context);
            if (authSession != null) {
                final String gplusid = authSession.get("id");
                request.response().putHeader("location", "chrome-extension://" + ext + "/" + path + "#" + gplusid + "@" + request.host());
                request.response().setStatusCode(302);
                request.response().end();
            } else {
                request.response().setStatusCode(200);
                final String state = new BigInteger(130, new SecureRandom()).toString(32);
                final AuthSession session = AuthSessionManager.createSession(sessionTTL);
                session.put("state", state);
                request.response().putHeader("Set-Cookie", ServerCookieEncoder.LAX.encode(Constants.SESSIONCOOKIE, session.getId().toString()));
                final String auth_html = new Scanner(this.getClass().getResourceAsStream(STATIC_FILE), "UTF-8")
                        .useDelimiter("\\A").next()
                        .replaceAll("[{]{2}\\s*CLIENT_ID\\s*[}]{2}", auth.getString("client-id"))
                        .replaceAll("[{]{2}\\s*STATE\\s*[}]{2}", state)
                        .replaceAll("[{]{2}\\s*APPLICATION_NAME\\s*[}]{2}", auth.getString("title"));
                request.response().end(auth_html);
            }
        } else {
            request.response().setStatusCode(401);
            request.response().end("unauthorized");
        }
    }
}