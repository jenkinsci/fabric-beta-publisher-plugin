package fabric.beta.publisher;

import okhttp3.ResponseBody;
import org.springframework.util.FileCopyUtils;
import retrofit2.Response;

import java.io.*;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        FileInputStream fis = null;
        ZipInputStream zis = null;
        try {
            byte[] buffer = new byte[1024];
            fis = new FileInputStream(zipFilePath);
            zis = new ZipInputStream(fis);
            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(destDir, fileName);
                File parentFile = new File(newFile.getParent());
                boolean folderCreated = parentFile.mkdirs();
                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
                ze = zis.getNextEntry();
            }

        } finally {
            if (zis != null) {
                zis.closeEntry();
                zis.close();
            }
            if (fis != null) {
                fis.close();
            }
        }
    }

    static File createTempDirectory() throws IOException {
        File temp = Files.createTempDirectory("temp" + Long.toString(System.nanoTime())).toFile();

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
