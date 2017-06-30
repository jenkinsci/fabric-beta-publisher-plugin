package fabric.beta.publisher;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;

import javax.annotation.Nonnull;
import java.io.IOException;

@Extension
public class EnvVarsContributor extends EnvironmentContributor {
    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener)
            throws IOException, InterruptedException {
        EnvVarsAction action = run.getAction(EnvVarsAction.class);
        if (action != null) {
            envs.putAll(action.getData());
        }
    }
}
