package com.example.myecowatch1.utils;

public class UsernameValidator {
    public static boolean isValid(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        // Example: Min 3, Max 20 characters, alphanumeric and underscore
        return username.matches("^[a-zA-Z0-9_]{3,20}$");
    }
}