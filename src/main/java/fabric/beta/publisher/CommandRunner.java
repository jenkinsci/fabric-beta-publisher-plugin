package fabric.beta.publisher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.List;

class CommandRunner {
    static boolean runCommand(PrintStream logger, List<String> command) throws IOException {
        logger.println("Executing command: " + command);

        boolean success = true;
        Process p = new ProcessBuilder(command).start();
        String s;
        BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream(), "UTF-8"));
        while ((s = stdError.readLine()) != null) {
            logger.println(s);
            success = false;
        }
        stdError.close();
        return success;
    }

}
