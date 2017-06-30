package fabric.beta.publisher;

import hudson.model.Action;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

class EnvVarsAction implements Action {
    private transient Map<String, String> data = new HashMap<>();

    void add(PrintStream logger, String key, String value) {
        if (data == null) return;
        logger.println("Setting environment variable " + key + " = " + value);
        data.put(key, value);
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }

    public Map<String, String> getData() {
        return data;
    }
}