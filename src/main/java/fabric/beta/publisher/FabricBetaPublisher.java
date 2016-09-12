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
import net.lingala.zip4j.exception.ZipException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static fabric.beta.publisher.FileUtils.downloadCrashlyticsTools;
import static fabric.beta.publisher.FileUtils.getManifestFile;

public class FabricBetaPublisher extends Recorder {
    private static final String RELEASE_NOTES_TYPE_PARAMETER = "RELEASE_NOTES_PARAMETER";
    private static final String RELEASE_NOTES_TYPE_CHANGELOG = "RELEASE_NOTES_FROM_CHANGELOG";
    private static final String RELEASE_NOTES_TYPE_NONE = "RELEASE_NOTES_NONE";

    private static final String NOTIFY_TESTERS_TYPE_NONE = "NOTIFY_TESTERS_NONE";

    private final String apiKey;
    private final String buildSecret;
    private final String releaseNotesType;
    private final String notifyTestersType;
    private final String releaseNotesParameter;
    private final String apkPath;
    private final String testersEmails;
    private final String testersGroup;
    private final boolean useAntStyleInclude;

    @DataBoundConstructor
    public FabricBetaPublisher(String apiKey, String buildSecret, String releaseNotesType, String notifyTestersType,
                               String releaseNotesParameter, String apkPath, String testersEmails, String testersGroup,
                               boolean useAntStyleInclude) {
        this.apiKey = apiKey;
        this.buildSecret = buildSecret;
        this.releaseNotesType = releaseNotesType == null ? RELEASE_NOTES_TYPE_NONE : releaseNotesType;
        this.notifyTestersType = notifyTestersType == null ? NOTIFY_TESTERS_TYPE_NONE : notifyTestersType;
        this.releaseNotesParameter = releaseNotesParameter;
        this.testersEmails = testersEmails;
        this.testersGroup = testersGroup;
        this.apkPath = apkPath;
        this.useAntStyleInclude = useAntStyleInclude;
    }

    @Override
    public boolean perform(@Nonnull AbstractBuild<?, ?> build, @Nonnull Launcher launcher, @Nonnull BuildListener listener)
            throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        logger.println("Fabric Beta Publisher Plugin:");

        File manifestFile = getManifestFile();

        File crashlyticsToolsFile;
        try {
            crashlyticsToolsFile = downloadCrashlyticsTools(logger);
        } catch (ZipException e) {
            logger.println("Error downloading crashlytics-devtools.jar: " + e.getMessage());
            FileUtils.delete(logger, manifestFile);
            return false;
        }

        String releaseNotes = getReleaseNotes(build, listener);

        final List<FilePath> apkFilePaths = getApkFilePaths(build.getWorkspace());
        boolean failure = apkFilePaths.isEmpty();
        for (final FilePath apkFilePath : apkFilePaths) {
            File apkFile;
            boolean shouldDeleteApk;
            if (apkFilePath.isRemote()) {
                apkFile = FileUtils.createTemporaryUploadFile(apkFilePath.read());
                shouldDeleteApk = true;
            } else {
                apkFile = new File(apkFilePath.toURI());
                shouldDeleteApk = false;
            }

            List<String> command = buildCrashlyticsCommand(manifestFile, apkFile, crashlyticsToolsFile, releaseNotes);
            logger.println("Executing command: " + command);

            Process p = new ProcessBuilder(command).start();
            String s;
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream(), "UTF-8"));
            while ((s = stdError.readLine()) != null) {
                logger.println(s);
                failure = true;
            }
            stdError.close();
            if (shouldDeleteApk) {
                FileUtils.delete(logger, apkFile);
            }
        }
        FileUtils.delete(logger, manifestFile, crashlyticsToolsFile);
        return !failure;
    }

    private List<FilePath> getApkFilePaths(FilePath workspace)
            throws IOException, InterruptedException {
        if (useAntStyleInclude) {
            final FilePath[] filePaths = workspace.list(apkPath);
            return Arrays.asList(filePaths);
        } else {
            final List<FilePath> filePaths = new ArrayList<>();
            for (String oneApkPath : apkPath.split(",")) {
                filePaths.add(new FilePath(workspace, oneApkPath.trim()));
            }
            return filePaths;
        }
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

    @SuppressWarnings("unused")
    public String getApiKey() {
        return apiKey;
    }

    @SuppressWarnings("unused")
    public String getBuildSecret() {
        return buildSecret;
    }

    @SuppressWarnings("unused")
    public String getApkPath() {
        return apkPath;
    }

    @SuppressWarnings("unused")
    public boolean isUseAntStyleInclude() {
        return useAntStyleInclude;
    }

    @SuppressWarnings("unused")
    public boolean isSendNotifications() {
        return !notifyTestersType.equalsIgnoreCase(NOTIFY_TESTERS_TYPE_NONE);
    }

    @SuppressWarnings("unused")
    public String getTestersGroup() {
        return testersGroup;
    }

    @SuppressWarnings("unused")
    public String isReleaseNotesType(String releaseNotesType) {
        return this.releaseNotesType.equalsIgnoreCase(releaseNotesType) ? "true" : "";
    }

    @SuppressWarnings("unused")
    public String isNotifyTestersType(String notifyTestersType) {
        return this.notifyTestersType.equalsIgnoreCase(notifyTestersType) ? "true" : "";
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
            load();
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckApiKey(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please input a Fabric API key");
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckBuildSecret(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please input a Fabric build secret");
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("unused")
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

