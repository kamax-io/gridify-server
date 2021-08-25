package io.kamax.gridify.server;

import org.apache.commons.lang3.StringUtils;

public class App {

    private App() {
    }

    private static String name;
    private static String version;
    private static String userAgent;

    public static String getName() {
        if (StringUtils.isBlank(name)) {
            name = StringUtils.defaultIfBlank(App.class.getPackage().getImplementationTitle(), "gridifyd");
        }

        return name;
    }

    public static String getVersion() {
        if (StringUtils.isBlank(version)) {
            version = StringUtils.defaultIfBlank(App.class.getPackage().getImplementationVersion(), "0.0.0");
        }

        return version;
    }

    public static String getUserAgent() {
        if (StringUtils.isBlank(userAgent)) {
            userAgent = getName() + "/" + getVersion();
        }
        return userAgent;
    }

}
