package com.homecloud.common;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@Component
public class HomeCloudErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(
            WebRequest webRequest,
            ErrorAttributeOptions options) {

        Map<String, Object> attributes =
                super.getErrorAttributes(
                        webRequest,
                        ErrorAttributeOptions.defaults()
                );

        int status = (int) attributes.getOrDefault("status", 500);

        String message = switch (status) {
            case 400 -> "Bad request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Resource not found";
            case 405 -> "Method not allowed";
            default -> "Internal server error";
        };

        return Map.of(
                "status", status,
                "message", message
        );
    }
}