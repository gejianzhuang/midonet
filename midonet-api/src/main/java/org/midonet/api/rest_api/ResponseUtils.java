/*
 * Copyright 2012 Midokura PTE LTD.
 */
package org.midonet.api.rest_api;

import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.midonet.api.HttpSupport;
import org.midonet.api.VendorMediaType;
import org.midonet.api.error.ErrorEntity;
import org.midonet.api.validation.ValidationErrorEntity;

import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Utility methods for Response class.
 */
public class ResponseUtils {

    private static ObjectMapper objectMapper = new ObjectMapper();
    private static JsonFactory jsonFactory = new JsonFactory(objectMapper);

    /**
     * Generate a Response object with the body set to ErrorEntity.
     *
     * @param status
     *            HTTP status to set
     * @param message
     *            Error message
     * @return Response object.
     */
    public static Response buildErrorResponse(int status, String message) {
        ErrorEntity error = new ErrorEntity();
        error.setCode(status);
        error.setMessage(message);
        return Response.status(status).entity(error)
                .type(VendorMediaType.APPLICATION_ERROR_JSON).build();
    }

    /**
     * Generate a validation error message.
     *
     * @param violations
     *            Set of violations.
     * @return Concatenated validation error message.
     */
    public static <T> Response buildValidationErrorResponse(
            Set<ConstraintViolation<T>> violations) {
        ValidationErrorEntity errors = new ValidationErrorEntity();
        errors.setMessage("Validation error(s) found");
        errors.setCode(Response.Status.BAD_REQUEST.getStatusCode());
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>(
                violations.size());
        for (ConstraintViolation<T> c : violations) {
            Map<String, String> msg = new HashMap<String, String>(2);
            msg.put("property", c.getPropertyPath().toString());
            msg.put("message", c.getMessage());
            messages.add(msg);
        }
        errors.setViolations(messages);
        return Response.status(Response.Status.BAD_REQUEST).entity(errors)
                .type(VendorMediaType.APPLICATION_ERROR_JSON).build();
    }

    public static String generateJsonError(int code, String msg)
            throws IOException {
        ErrorEntity err = new ErrorEntity();
        err.setCode(code);
        err.setMessage(msg);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JsonGenerator jsonGenerator = jsonFactory.createJsonGenerator(bos,
                JsonEncoding.UTF8);
        jsonGenerator.writeObject(err);
        bos.close();
        return bos.toString(HttpSupport.UTF8_ENC);
    }

    public static void setErrorResponse(HttpServletResponse resp, int code,
                                        String msg) throws IOException {
        resp.setContentType(VendorMediaType.APPLICATION_ERROR_JSON);
        resp.setCharacterEncoding(HttpSupport.UTF8_ENC);
        resp.setStatus(code);
        resp.getWriter().write(generateJsonError(code, msg));
    }

    public static void setAuthErrorResponse(HttpServletResponse resp,
                                        String msg) throws IOException {
        setErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, msg);
        resp.setHeader(HttpSupport.WWW_AUTHENTICATE,
                HttpSupport.BASIC_AUTH_REALM);
    }

    public static void setCookie(HttpServletResponse resp, String key,
                                 Date expires) {

        String expiresGmt = null;
        if (expires != null) {
            DateFormat df = new SimpleDateFormat(
                    HttpSupport.SET_COOKIE_EXPIRES_FORMAT);
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
            expiresGmt = df.format(expires);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(HttpSupport.SET_COOKIE_SESSION_KEY);
        sb.append("=");
        sb.append(key);
        if (expiresGmt != null) {
            sb.append("; ");
            sb.append(HttpSupport.SET_COOKIE_EXPIRES);
            sb.append("=");
            sb.append(expiresGmt);
        }

        resp.setHeader(HttpSupport.SET_COOKIE, sb.toString());
    }
}