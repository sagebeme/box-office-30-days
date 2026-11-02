package com.boxoffice.desktop;

import javafx.application.Application;

/**
 * Starts the app. It's a separate class from BoxOfficeApp on purpose: when the
 * class named in the jar extends Application, Java refuses to start it from a
 * plain jar ("JavaFX runtime components are missing"). A main that merely calls
 * Application.launch doesn't trip that check.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Application.launch(BoxOfficeApp.class, args);
    }
}
