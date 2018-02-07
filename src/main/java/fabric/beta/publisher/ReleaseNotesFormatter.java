package fabric.beta.publisher;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.scm.ChangeLogSet;

import java.io.IOException;

import static fabric.beta.publisher.FabricBetaPublisher.*;

class ReleaseNotesFormatter {
    static String getReleaseNotes(ChangeLogSet<? extends ChangeLogSet.Entry> changeLogSet, String releaseNotesType,
                                  String releaseNotesParameter, String releaseNotesFile, EnvVars environment,
                                  FilePath workspace)
            throws IOException, InterruptedException {
        switch (releaseNotesType) {
            case RELEASE_NOTES_TYPE_NONE:
                return null;
            case RELEASE_NOTES_TYPE_PARAMETER:
                return environment.get(releaseNotesParameter, "");
            case RELEASE_NOTES_TYPE_CHANGELOG:
                StringBuilder sb = new StringBuilder();
                if (!changeLogSet.isEmptySet()) {
                    boolean hasManyChangeSets = changeLogSet.getItems().length > 1;
                    for (ChangeLogSet.Entry entry : changeLogSet) {
                        sb.append("\n");
                        if (hasManyChangeSets) {
                            sb.append("* ");
                        }
                        sb.append(entry.getMsg());
                    }
                }
                return sb.toString();
            case RELEASE_NOTES_TYPE_FILE:
                FilePath releaseNotesFilePath = new FilePath(workspace, environment.expand(releaseNotesFile));
                return releaseNotesFilePath.readToString();
            default:
                return null;
        }
    }
}
