package fabric.beta.publisher;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import okhttp3.ResponseBody;
import org.springframework.util.FileCopyUtils;
import retrofit2.Response;

import java.io.*;
import java.nio.file.Files;

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