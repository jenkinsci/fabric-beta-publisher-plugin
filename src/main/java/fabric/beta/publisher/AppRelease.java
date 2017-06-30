package fabric.beta.publisher;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

class AppRelease {
    final String appName;
    final String packageName;
    final String instanceId;
    final String displayVersion;
    final String buildVersion;

    AppRelease(String appName, String packageName, String instanceId, String displayVersion, String buildVersion) {
        this.appName = appName;
        this.packageName = packageName;
        this.instanceId = instanceId;
        this.displayVersion = displayVersion;
        this.buildVersion = buildVersion;
    }

    static AppRelease from(File apkFile) throws IOException, ZipException {
        ZipFile zipFile = new ZipFile(apkFile);
        FileHeader fileHeader = zipFile.getFileHeader("assets/crashlytics-build.properties");
        try (ZipInputStream zin = zipFile.getInputStream(fileHeader)) {
            Properties buildProperties = new Properties();
            buildProperties.load(zin);
            if (!buildProperties.isEmpty()) {
                String appName = buildProperties.getProperty("app_name");
                String packageName = buildProperties.getProperty("package_name");
                String instanceId = buildProperties.getProperty("build_id");
                String displayVersion = buildProperties.getProperty("version_name");
                String buildVersion = buildProperties.getProperty("version_code");
                return new AppRelease(appName, packageName, instanceId, displayVersion, buildVersion);
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
