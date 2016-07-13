package fabric.beta.publisher;

import hudson.FilePath;
import okhttp3.ResponseBody;
import retrofit2.Response;

import java.io.*;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class FileUtils {
    private static final String CRASHLYTICS_PATH = "crashlytics/crashlytics-devtools.jar";
    private static final String CRASHLYTICS_ZIP = "crashlytics.zip";

    static File getManifestFile(FilePath workspace) throws IOException, InterruptedException {
        FilePath manifestFilePath = new FilePath(workspace, "xml");
        File manifestFile = new File(manifestFilePath.toURI());
        Files.write(manifestFile.toPath(), "<?xml version=\"1.0\" encoding=\"utf-8\"?><manifest></manifest>".getBytes("UTF-8"));
        return manifestFile;
    }

    static File downloadCrashlyticsTools(FilePath workspace, PrintStream logger) throws IOException, InterruptedException {
        FilePath jarFp = new FilePath(workspace, CRASHLYTICS_PATH);
        if (jarFp.exists()) {
            return new File(jarFp.toURI());
        }

        Response<ResponseBody> response = FabricApi.service(logger).crashlyticsTools().execute();
        File zip = writeResponseBodyToDisk(workspace, response.body());
        extractFolder(zip.getPath(), logger);
        return new File(jarFp.toURI());
    }

    private static File writeResponseBodyToDisk(FilePath workspace, ResponseBody body) throws IOException, InterruptedException {
        FilePath filePath = new FilePath(workspace, CRASHLYTICS_ZIP);
        File file = new File(filePath.toURI());

        byte[] fileReader = new byte[4096];

        InputStream inputStream = body.byteStream();
        try (OutputStream outputStream = new FileOutputStream(file)) {
            while (true) {
                int read = inputStream.read(fileReader);
                if (read == -1) {
                    break;
                }
                outputStream.write(fileReader, 0, read);
            }

            outputStream.flush();
        }
        return file;
    }

    private static void extractFolder(String zipFile, PrintStream logger) throws IOException {
        System.out.println(zipFile);
        int BUFFER = 2048;
        File file = new File(zipFile);

        try (ZipFile zip = new ZipFile(file)) {
            String newPath = zipFile.substring(0, zipFile.length() - ".zip".length());

            if (!new File(newPath).mkdir()) return;
            Enumeration zipFileEntries = zip.entries();

            // Process each entry
            while (zipFileEntries.hasMoreElements()) {
                // grab a zip file entry
                ZipEntry entry = (ZipEntry) zipFileEntries.nextElement();
                String currentEntry = entry.getName();
                File destFile = new File(newPath, currentEntry);
                //destFile = new File(newPath, destFile.getName());
                File destinationParent = destFile.getParentFile();

                // create the parent directory structure if needed
                if (!destinationParent.mkdirs()) return;

                if (!entry.isDirectory()) {
                    BufferedInputStream is = new BufferedInputStream(zip.getInputStream(entry));
                    int currentByte;
                    // establish buffer for writing file
                    byte data[] = new byte[BUFFER];

                    // write the current file to disk
                    FileOutputStream fos = new FileOutputStream(destFile);
                    BufferedOutputStream dest = new BufferedOutputStream(fos,
                            BUFFER);

                    // read and write until last byte is encountered
                    while ((currentByte = is.read(data, 0, BUFFER)) != -1) {
                        dest.write(data, 0, currentByte);
                    }
                    dest.flush();
                    dest.close();
                    is.close();
                }

                if (currentEntry.endsWith(".zip")) {
                    // found a zip file, try to open
                    extractFolder(destFile.getAbsolutePath(), logger);
                }
            }
        } catch (IOException e) {
            e.printStackTrace(logger);
        }
    }
}