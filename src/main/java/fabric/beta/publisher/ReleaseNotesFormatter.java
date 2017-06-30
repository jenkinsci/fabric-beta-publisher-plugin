package fabric.beta.publisher;

import hudson.EnvVars;
import hudson.scm.ChangeLogSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static fabric.beta.publisher.FabricBetaPublisher.*;

class ReleaseNotesFormatter {
    static String getReleaseNotes(ChangeLogSet<? extends ChangeLogSet.Entry> changeLogSet, String releaseNotesType,
                                  String releaseNotesParameter, String releaseNotesFile, EnvVars environment)
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
                String releaseNotesFilePath = environment.expand(releaseNotesFile);
                byte[] fileContent = Files.readAllBytes(Paths.get(releaseNotesFilePath));
                return new String(fileContent, "UTF-8");
            default:
                return null;
        }
    }

}
