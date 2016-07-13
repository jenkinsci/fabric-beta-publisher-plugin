package fabric.beta.publisher;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static fabric.beta.publisher.FileUtils.getManifestFile;

@SuppressWarnings({"unused", "WeakerAccess"})
public class FabricBetaPublisher extends Recorder {
    private static final String RELEASE_NOTES_FORMAT = "text";
    private static final String APP_TYPE = "android_app";
    private static final String ARCHITECTURE = "java";
    private static final String MULTIPART_FORM_DATA = "multipart/form-data";

    private static final String RELEASE_NOTES_TYPE_PARAMETER = "RELEASE_NOTES_PARAMETER";
    private static final String RELEASE_NOTES_TYPE_CHANGELOG = "RELEASE_NOTES_FROM_CHANGELOG";
    private static final String RELEASE_NOTES_TYPE_NONE = "RELEASE_NOTES_NONE";

    private static final String NOTIFY_TESTERS_TYPE_EMAILS = "NOTIFY_TESTERS_EMAILS";
    private static final String NOTIFY_TESTERS_TYPE_GROUP = "NOTIFY_TESTERS_GROUP";
    private static final String NOTIFY_TESTERS_TYPE_NONE = "NOTIFY_TESTERS_NONE";

    private final String apiKey;
    private final String buildSecret;
    private final String releaseNotesType;
    private final String notifyTestersType;
    private final String releaseNotesParameter;
    private final String apkPath;
    private final String testersEmails;
    private final String testersGroup;

    @DataBoundConstructor
    public FabricBetaPublisher(String apiKey, String buildSecret, String releaseNotesType, String notifyTestersType,
                               String releaseNotesParameter, String apkPath, String testersEmails, String testersGroup) {
        this.apiKey = apiKey;
        this.buildSecret = buildSecret;
        this.releaseNotesType = releaseNotesType == null ? RELEASE_NOTES_TYPE_NONE : releaseNotesType;
        this.notifyTestersType = notifyTestersType == null ? NOTIFY_TESTERS_TYPE_NONE : notifyTestersType;
        this.releaseNotesParameter = releaseNotesParameter;
        this.testersEmails = testersEmails;
        this.testersGroup = testersGroup;
        this.apkPath = apkPath;
    }

    @Override
    public boolean perform(@Nonnull AbstractBuild<?, ?> build, @Nonnull Launcher launcher, @Nonnull BuildListener listener)
            throws IOException, InterruptedException {
        FilePath workspace = build.getWorkspace();
        File manifestFile = getManifestFile(workspace);

        FilePath apkFilePath = new FilePath(workspace, apkPath);
        File apkFile = new File(apkFilePath.toURI());

        PrintStream logger = listener.getLogger();
        File toolsFile = FileUtils.downloadCrashlyticsTools(workspace, logger);

        String releaseNotes = getReleaseNotes(build, listener);

        List<String> command = buildCrashlyticsCommand(manifestFile, apkFile, toolsFile, releaseNotes);
        logger.println("Executing command: " + command);

        Process p = new ProcessBuilder(command).start();

        boolean failure = false;
        String s;
        BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream(), "UTF-8"));
        while ((s = stdError.readLine()) != null) {
            logger.println(s);
            failure = true;
        }
        stdError.close();
        return !failure;
    }

    private String getReleaseNotes(@Nonnull AbstractBuild<?, ?> build, @Nonnull TaskListener listener)
            throws IOException, InterruptedException {
        switch (releaseNotesType) {
            case RELEASE_NOTES_TYPE_NONE:
                return null;
            case RELEASE_NOTES_TYPE_PARAMETER:
                return build.getEnvironment(listener).get(releaseNotesParameter, "");
            case RELEASE_NOTES_TYPE_CHANGELOG:
                StringBuilder sb = new StringBuilder();
                if (!build.getChangeSet().isEmptySet()) {
                    boolean hasManyChangeSets = build.getChangeSet().getItems().length > 1;
                    for (ChangeLogSet.Entry entry : build.getChangeSet()) {
                        sb.append("\n");
                        if (hasManyChangeSets) {
                            sb.append("* ");
                        }
                        sb.append(entry.getMsg());
                    }
                }
                return sb.toString();
            default:
                return null;
        }
    }

    private List<String> buildCrashlyticsCommand(File manifestFile, File apkFile, File toolsFile, String releaseNotes) {
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(toolsFile.getPath());
        command.add("-androidRes");
        command.add(".");
        command.add("-apiKey");
        command.add(apiKey);
        command.add("-apiSecret");
        command.add(buildSecret);
        command.add("-androidManifest");
        command.add(manifestFile.getPath());
        command.add("-uploadDist");
        command.add(apkFile.getPath());
        command.add("-betaDistributionNotifications");
        command.add(String.valueOf(isSendNotifications()));
        if (testersEmails != null && !testersEmails.isEmpty()) {
            command.add("-betaDistributionEmails");
            command.add(testersEmails);
        }
        if (testersGroup != null && !testersGroup.isEmpty()) {
            command.add("-betaDistributionGroupAliases");
            command.add(testersGroup);
        }
        if (releaseNotes != null && !releaseNotes.isEmpty()) {
            command.add("-betaDistributionReleaseNotes");
            command.add(releaseNotes);
        }
        return command;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBuildSecret() {
        return buildSecret;
    }

    public String getApkPath() {
        return apkPath;
    }

    public boolean isSendNotifications() {
        return !notifyTestersType.equalsIgnoreCase(NOTIFY_TESTERS_TYPE_NONE);
    }

    public String getTestersGroup() {
        return testersGroup;
    }

    public String isReleaseNotesType(String releaseNotesType) {
        return this.releaseNotesType.equalsIgnoreCase(releaseNotesType) ? "true" : "";
    }

    public String isNotifyTestersType(String notifyTestersType) {
        return this.notifyTestersType.equalsIgnoreCase(notifyTestersType) ? "true" : "";
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
            load();
        }

        public FormValidation doCheckApiKey(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please input a Fabric API key");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckBuildSecret(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please input a Fabric build secret");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckApkPath(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please input an .apk file path");
            }
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Upload .apk to Fabric Beta";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }
    }
}

