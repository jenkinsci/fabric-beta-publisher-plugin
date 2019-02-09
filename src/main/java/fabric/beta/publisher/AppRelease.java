package fabric.beta.publisher;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

import static fabric.beta.publisher.FileUtils.createTempDirectory;
import static fabric.beta.publisher.FileUtils.unzip;

class AppRelease {
    private final String packageName;
    private final String instanceId;
    private final String displayVersion;
    private final String buildVersion;

    private AppRelease(String packageName, String instanceId, String displayVersion, String buildVersion) {
        this.packageName = packageName;
        this.instanceId = instanceId;
        this.displayVersion = displayVersion;
        this.buildVersion = buildVersion;
    }

    static AppRelease from(File apkFile) throws IOException {
        File dest = createTempDirectory();
        unzip(apkFile.getAbsolutePath(), dest);
        File propertiesFile = new File(dest, "assets/crashlytics-build.properties");
        try (FileInputStream inputStream = new FileInputStream(propertiesFile)) {
            Properties buildProperties = new Properties();
            buildProperties.load(inputStream);
            if (!buildProperties.isEmpty()) {
                String packageName = buildProperties.getProperty("package_name");
                String instanceId = buildProperties.getProperty("build_id");
                String displayVersion = buildProperties.getProperty("version_name");
                String buildVersion = buildProperties.getProperty("version_code");
                return new AppRelease(packageName, instanceId, displayVersion, buildVersion);
            }
            return null;
        }
    }

    String buildLink(String organization) {
        return String.format(Locale.US,
                "https://fabric.io/%1$s/android/apps/%2$s/beta/releases/" +
                        "%3$s?build_version=%4$s&display_version=%5$s",
                organization, packageName, instanceId, buildVersion, displayVersion);
    }
}
