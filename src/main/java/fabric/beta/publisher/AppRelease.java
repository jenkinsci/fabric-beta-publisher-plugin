package fabric.beta.publisher;

import java.util.Locale;

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

    String buildLink(String organization) {
        return String.format(Locale.US,
                "https://fabric.io/%1$s/android/apps/%2$s/beta/releases/" +
                        "%3$s?build_version=%4$s&display_version=%5$s",
                organization, packageName, instanceId, buildVersion, displayVersion);
    }

    @Override
    public String toString() {
        return "AppRelease{" +
                "appName='" + appName + '\'' +
                ", packageName='" + packageName + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", displayVersion='" + displayVersion + '\'' +
                ", buildVersion='" + buildVersion + '\'' +
                '}';
    }
}
