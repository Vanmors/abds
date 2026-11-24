package com.vanmors.abds;

import com.vanmors.abds.config.AppConfig;

public class Main {
    public static void main(String[] args) {
        App.create(AppConfig.fromEnv()).start();
    }
}
