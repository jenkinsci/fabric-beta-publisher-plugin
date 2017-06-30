package fabric.beta.publisher;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;
import okhttp3.ResponseBody;
import org.springframework.util.FileCopyUtils;
import retrofit2.Response;

import java.io.*;
import java.nio.file.Files;
import java.util.Properties;

class FileUtils {
    static File getManifestFile() throws IOException, InterruptedException {
        File manifestFile = File.createTempFile("xml", null);
        Files.write(manifestFile.toPath(),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><manifest></manifest>".getBytes("UTF-8"));
        return manifestFile;
    }

    static File createTemporaryUploadFile(InputStream inputStream) throws IOException {
        File file = File.createTempFile("app-build-tmp", "apk");
        FileOutputStream outputStream = new FileOutputStream(file);
        FileCopyUtils.copy(inputStream, outputStream);
        outputStream.close();
        return file;
    }

    static File downloadCrashlyticsTools(PrintStream logger)
            throws IOException, InterruptedException, ZipException {
        File crashlyticsZip = File.createTempFile("crashlytics", ".zip");
        Response<ResponseBody> response = FabricApi.service(logger).crashlyticsTools().execute();
        writeResponseBodyToDisk(crashlyticsZip, response.body());

        File crashlyticsJar = File.createTempFile("crashlytics-devtools", ".jar");
        ZipFile crashlyticsZipFile = new ZipFile(crashlyticsZip);
        crashlyticsZipFile.extractFile("crashlytics-devtools.jar", crashlyticsJar.getParent(), null, crashlyticsJar.getName());

        delete(logger, crashlyticsZip);
        return crashlyticsJar;
    }

    static void delete(PrintStream logger, File... files) {
        for (File file : files) {
            logger.println("Temporary " + file.getName() + " got deleted = " + file.delete());
        }
    }

    static AppRelease readBuildProperties(File apkFile) throws IOException, ZipException {
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

    private static void writeResponseBodyToDisk(File target, ResponseBody body) throws IOException, InterruptedException {
        byte[] fileReader = new byte[4096];

        InputStream inputStream = body.byteStream();
        try (OutputStream outputStream = new FileOutputStream(target)) {
            while (true) {
                int read = inputStream.read(fileReader);
                if (read == -1) {
                    break;
                }
                outputStream.write(fileReader, 0, read);
            }

            outputStream.flush();
        }
    }
}