package fabric.beta.publisher;

import hudson.model.*;
import hudson.scm.ChangeLogSet;

class ChangelogReader {
    static ChangeLogSet<? extends ChangeLogSet.Entry> getChangeLogSetFromRun(Run<?, ?> build) {
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
}
