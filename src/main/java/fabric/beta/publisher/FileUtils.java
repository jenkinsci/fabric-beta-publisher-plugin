package fabric.beta.publisher;

import okhttp3.ResponseBody;
import org.springframework.util.FileCopyUtils;
import retrofit2.Response;

import java.io.*;
import java.nio.file.Files;
import java.util.zip.ZipEntry;

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

    static File downloadCrashlyticsTools(PrintStream logger) throws IOException, InterruptedException {
        File crashlyticsZip = File.createTempFile("crashlytics", ".zip");
        Response<ResponseBody> response = FabricApi.service(logger).crashlyticsTools().execute();
        writeResponseBodyToDisk(crashlyticsZip, response.body());
        return crashlyticsZip;
    }

    static File extractCrashlyticsJar(File crashlyticsZip, PrintStream logger) throws IOException {
        File crashlyticsJar = createTempDirectory();
        unzip(crashlyticsZip.getAbsolutePath(), crashlyticsJar);
        File crashlyticsZipFile = new File(crashlyticsJar, "crashlytics-devtools.jar");

        delete(logger, crashlyticsZip);
        return crashlyticsZipFile;
    }

    static void delete(PrintStream logger, File... files) {
        for (File file : files) {
            logger.println("Temporary " + file.getName() + " got deleted = " + file.delete());
        }
    }

    static void unzip(String zipFilePath, File destDir) throws IOException {
        byte[] buffer = new byte[1024];
        FileInputStream fis = new FileInputStream(zipFilePath);
        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(fis);
        ZipEntry ze = zis.getNextEntry();
        while (ze != null) {
            String fileName = ze.getName();
            File newFile = new File(destDir, fileName);
            new File(newFile.getParent()).mkdirs();
            FileOutputStream fos = new FileOutputStream(newFile);
            int len;
            while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            zis.closeEntry();
            ze = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();
        fis.close();
    }

    static File createTempDirectory() throws IOException {
        File temp = File.createTempFile("temp", Long.toString(System.nanoTime()));

        if (!temp.delete()) {
            throw new IOException("Could not delete temp file: " + temp.getAbsolutePath());
        }

        if (!temp.mkdir()) {
            throw new IOException("Could not create temp directory: " + temp.getAbsolutePath());
        }

        return temp;
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