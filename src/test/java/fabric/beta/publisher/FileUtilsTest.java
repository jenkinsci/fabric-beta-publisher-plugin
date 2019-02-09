package fabric.beta.publisher;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertNotNull;

public class FileUtilsTest {

    private PrintStream logger = new PrintStream(new OutputStream() {
        @Override
        public void write(int b) throws IOException {

        }
    });

    @Test
    public void testExtractCrashlyticsJar() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        File crashlyticsZip = new File(classLoader.getResource("crashlytics.zip").getFile());
        File jar = FileUtils.extractCrashlyticsJar(crashlyticsZip, logger);

        assertNotNull(jar);
    }
}
