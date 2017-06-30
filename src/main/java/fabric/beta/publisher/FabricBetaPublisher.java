package fabric.beta.publisher;

import com.google.common.base.Strings;
import hudson.model.*;
import net.lingala.zip4j.exception.ZipException;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;

import static fabric.beta.publisher.FileUtils.downloadCrashlyticsTools;
import static fabric.beta.publisher.FileUtils.getManifestFile;

public class FabricBetaPublisher extends Recorder implements SimpleBuildStep {
    private static final String RELEASE_NOTES_TYPE_FILE = "RELEASE_NOTES_FILE";
    private static final String RELEASE_NOTES_TYPE_PARAMETER = "RELEASE_NOTES_PARAMETER";
    private static final String RELEASE_NOTES_TYPE_CHANGELOG = "RELEASE_NOTES_FROM_CHANGELOG";
    private static final String RELEASE_NOTES_TYPE_NONE = "RELEASE_NOTES_NONE";

    private static final String NOTIFY_TESTERS_TYPE_NONE = "NOTIFY_TESTERS_NONE";
    private static final String NOTIFY_TESTERS_TYPE_EMAILS = "NOTIFY_TESTERS_EMAILS";
    private static final String NOTIFY_TESTERS_GROUP = "NOTIFY_TESTERS_GROUP";

    private final String apiKey;
    private final String buildSecret;
    private final String releaseNotesType;
    private final String notifyTestersType;
    private final String releaseNotesParameter;
    private final String releaseNotesFile;
    private final String apkPath;
    private final String testersEmails;
    private final String testersGroup;
    private final String organization;
    private final boolean useAntStyleInclude;

    @DataBoundConstructor
    public FabricBetaPublisher(String apiKey, String buildSecret, String releaseNotesType, String notifyTestersType,
                               String releaseNotesParameter, String releaseNotesFile, String apkPath,
                               String testersEmails, String testersGroup, String organization,
                               boolean useAntStyleInclude) {
        this.apiKey = apiKey;
        this.buildSecret = buildSecret;
        this.releaseNotesType = releaseNotesType == null ? RELEASE_NOTES_TYPE_NONE : releaseNotesType;
        this.notifyTestersType = notifyTestersType == null ? NOTIFY_TESTERS_TYPE_NONE : notifyTestersType;
        this.releaseNotesParameter = releaseNotesParameter;
        this.releaseNotesFile = releaseNotesFile;
        this.testersEmails = testersEmails;
        this.testersGroup = testersGroup;
        this.organization = organization;
        this.apkPath = apkPath;
        this.useAntStyleInclude = useAntStyleInclude;
    }

    @Override
    public boolean perform(@Nonnull AbstractBuild<?, ?> build, @Nonnull Launcher launcher,
                           @Nonnull BuildListener listener) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        ChangeLogSet<? extends ChangeLogSet.Entry> changeLogSet = build.getChangeSet();
        return publishFabric(build, build.getEnvironment(listener), build.getWorkspace(), logger, changeLogSet);
    }

    @Override
    public void perform(@Nonnull Run build, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws InterruptedException, IOException {
        PrintStream logger = listener.getLogger();
        ChangeLogSet<? extends ChangeLogSet.Entry> changeLogSet = getChangeLogSetFromRun(build);
        if (!publishFabric(build, build.getEnvironment(listener), workspace, logger, changeLogSet)) {
            build.setResult(Result.FAILURE);
        }
    }

    private boolean publishFabric(Run build, EnvVars environment, FilePath workspace, PrintStream logger,
                                  ChangeLogSet<? extends ChangeLogSet.Entry> changeLogSet)
            throws InterruptedException, IOException {
        logger.println("Fabric Beta Publisher Plugin:");

        File manifestFile = getManifestFile();

        File crashlyticsToolsFile = prepareCrashlytics(logger, manifestFile);
        if (crashlyticsToolsFile == null) {
            return false;
        }

        String releaseNotes = getReleaseNotes(environment, changeLogSet);

        final List<FilePath> apkFilePaths = getApkFilePaths(environment, workspace);
        boolean failure = apkFilePaths.isEmpty();
        for (int apkIndex = 0; apkIndex < apkFilePaths.size(); apkIndex++) {
            failure = uploadApkFile(build, apkIndex, environment, logger, manifestFile, crashlyticsToolsFile,
                    releaseNotes, failure, apkFilePaths.get(apkIndex));
        }
        FileUtils.delete(logger, manifestFile, crashlyticsToolsFile);
        return !failure;
    }

    private ChangeLogSet<? extends ChangeLogSet.Entry> getChangeLogSetFromRun(Run<?, ?> build) {
        ItemGroup<?> itemGroup = build.getParent().getParent();
        for (Item item : itemGroup.getItems()) {
            if (!item.getFullDisplayName().equals(build.getFullDisplayName())
                    && !item.getFullDisplayName().equals(build.getParent().getFullDisplayName())) {
                continue;
            }

            for (Job<?, ?> job : item.getAllJobs()) {
                if (job instanceof AbstractProject<?, ?>) {
                    AbstractProject<?, ?> project = (AbstractProject<?, ?>) job;
                    return project.getBuilds().getLastBuild().getChangeSet();
                }
            }
        }
        return ChangeLogSet.createEmpty(build);
    }

    private boolean uploadApkFile(Run build, int apkIndex, EnvVars environment, PrintStream logger,
                                  File manifestFile, File crashlyticsToolsFile, String releaseNotes,
                                  boolean failure, FilePath apkFilePath) throws IOException, InterruptedException {
        File apkFile;
        boolean shouldDeleteApk;
        if (apkFilePath.isRemote()) {
            apkFile = FileUtils.createTemporaryUploadFile(apkFilePath.read());
            shouldDeleteApk = true;
        } else {
            apkFile = new File(apkFilePath.toURI());
            shouldDeleteApk = false;
        }

        if (!Strings.isNullOrEmpty(organization)) {
            try {
                AppRelease appRelease = FileUtils.readBuildProperties(apkFile);
                if (appRelease == null) {
                    throw new InterruptedIOException("Could not read APK properties for apk " + apkFile);
                } else {
                    saveBuildLinks(logger, build, apkIndex, appRelease.buildLink(organization));
                }
            } catch (ZipException e) {
                e.printStackTrace();
                failure = true;
            }
        } else {
            logger.println("Skipped constructing Fabric Beta link because organization is not set.");
        }

        List<String> command = buildCrashlyticsCommand(environment, manifestFile, apkFile, crashlyticsToolsFile, releaseNotes);
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
        return failure;
    }

    private void saveBuildLinks(PrintStream logger, Run build, int apkIndex, String buildUrl)
            throws IOException, InterruptedException {
        EnvVarsAction envData = new EnvVarsAction();
        if (apkIndex == 0) {
            envData.add(logger, "FABRIC_BETA_INSTALL_URL", buildUrl);
        }
        envData.add(logger, "FABRIC_BETA_BUILD_URL_" + apkIndex, buildUrl);
        build.addAction(envData);
    }

    private File prepareCrashlytics(PrintStream logger, File manifestFile) throws IOException, InterruptedException {
        try {
            return downloadCrashlyticsTools(logger);
        } catch (ZipException e) {
            logger.println("Error downloading crashlytics-devtools.jar: " + e.getMessage());
            FileUtils.delete(logger, manifestFile);
        }
        return null;
    }

    private List<FilePath> getApkFilePaths(EnvVars environment, FilePath workspace) throws IOException, InterruptedException {
        if (useAntStyleInclude) {
            final FilePath[] filePaths = workspace.list(expand(environment, apkPath));
            return Arrays.asList(filePaths);
        } else {
            final List<FilePath> filePaths = new ArrayList<>();
            for (String oneApkPath : apkPath.split(",")) {
                filePaths.add(new FilePath(workspace, expand(environment, oneApkPath.trim())));
            }
            return filePaths;
        }
    }

    private String getReleaseNotes(EnvVars environment, ChangeLogSet<? extends ChangeLogSet.Entry> changeLogSet)
            throws IOException, InterruptedException {
        switch (releaseNotesType) {
            case RELEASE_NOTES_TYPE_NONE:
                return null;
            case RELEASE_NOTES_TYPE_PARAMETER:
                return environment.get(releaseNotesParameter, "");
            case RELEASE_NOTES_TYPE_CHANGELOG:
                StringBuilder sb = new StringBuilder();
                if (!changeLogSet.isEmptySet()) {
                    boolean hasManyChangeSets = changeLogSet.getItems().length > 1;
                    for (ChangeLogSet.Entry entry : changeLogSet) {
                        sb.append("\n");
                        if (hasManyChangeSets) {
                            sb.append("* ");
                        }
                        sb.append(entry.getMsg());
                    }
                }
                return sb.toString();
            case RELEASE_NOTES_TYPE_FILE:
                String releaseNotesFilePath = expand(environment, releaseNotesFile);
                byte[] fileContent = Files.readAllBytes(Paths.get(releaseNotesFilePath));
                return new String(fileContent, "UTF-8");
            default:
                return null;
        }
    }

    private List<String> buildCrashlyticsCommand(EnvVars environment,
                                                 File manifestFile, File apkFile, File toolsFile, String releaseNotes)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("java");

        if (System.getProperty("http.nonProxyHosts") != null) {
            command.add("-Dhttp.nonProxyHosts=\"" + System.getProperty("http.nonProxyHosts") + "\"");
        }

        if (System.getProperty("http.proxyHost") != null) {
            command.add("-Dhttp.proxyHost=" + System.getProperty("http.proxyHost"));
        }

        if (System.getProperty("http.proxyPort") != null) {
            command.add("-Dhttp.proxyPort=" + System.getProperty("http.proxyPort"));
        }

        if (System.getProperty("https.proxyHost") != null) {
            command.add("-Dhttps.proxyHost=" + System.getProperty("https.proxyHost"));
        }

        if (System.getProperty("https.proxyPort") != null) {
            command.add("-Dhttps.proxyPort=" + System.getProperty("https.proxyPort"));
        }

        command.add("-jar");
        command.add(toolsFile.getPath());
        command.add("-androidRes");
        command.add(".");
        command.add("-apiKey");
        command.add(expand(environment, apiKey));
        command.add("-apiSecret");
        command.add(expand(environment, buildSecret));
        command.add("-androidManifest");
        command.add(manifestFile.getPath());
        command.add("-uploadDist");
        command.add(apkFile.getPath());
        command.add("-betaDistributionNotifications");
        command.add(String.valueOf(shouldSendNotifications()));
        if (NOTIFY_TESTERS_TYPE_EMAILS.equals(notifyTestersType) && testersEmails != null && !testersEmails.isEmpty()) {
            command.add("-betaDistributionEmails");
            command.add(expand(environment, testersEmails));
        }
        if (NOTIFY_TESTERS_GROUP.equals(notifyTestersType) && testersGroup != null && !testersGroup.isEmpty()) {
            command.add("-betaDistributionGroupAliases");
            command.add(expand(environment, testersGroup));
        }
        if (releaseNotes != null && !releaseNotes.isEmpty()) {
            command.add("-betaDistributionReleaseNotes");
            command.add(releaseNotes);
        }
        return command;
    }

    private String expand(EnvVars environment, String s)
            throws IOException, InterruptedException {
        return environment.expand(s);
    }

    private boolean shouldSendNotifications() {
        return !notifyTestersType.equalsIgnoreCase(NOTIFY_TESTERS_TYPE_NONE);
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
    public String getTestersGroup() {
        return testersGroup;
    }

    @SuppressWarnings("unused")
    public String getOrganization() {
        return organization;
    }

    @SuppressWarnings("unused")
    public String getTestersEmails() {
        return testersEmails;
    }

    @SuppressWarnings("unused")
    public String isReleaseNotesType(String releaseNotesType) {
        return this.releaseNotesType.equalsIgnoreCase(releaseNotesType) ? "true" : "";
    }

    @SuppressWarnings("unused")
    public String isNotifyTestersType(String notifyTestersType) {
        return this.notifyTestersType.equalsIgnoreCase(notifyTestersType) ? "true" : "";
    }

    @SuppressWarnings("unused")
    public String getReleaseNotesType() {
        return releaseNotesType;
    }

    @SuppressWarnings("unused")
    public String getNotifyTestersType() {
        return notifyTestersType;
    }

    @SuppressWarnings("unused")
    public String getReleaseNotesParameter() {
        return releaseNotesParameter;
    }

    @SuppressWarnings("unused")
    public String getReleaseNotesFile() {
        return releaseNotesFile;
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

