package codes.vps.maven.extension;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;


@Component(role = AbstractMavenLifecycleParticipant.class)
public class ProjectDefaults extends AbstractMavenLifecycleParticipant implements LogEnabled {

    private Logger logger;

    public static final String ALT_USER_SETTINGS_XML_LOCATION = "codes.vps.mvn-project-default";

    @Requirement(role = ArtifactRepositoryLayout.class)
    private ArtifactRepositoryLayout repoLayout;

    @Requirement
    private RepositorySystem repositorySystem;

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {

        super.afterProjectsRead(session);

        File useFile = getFile("${user.home}/.m2/default_project.xml", "user.home",
                ALT_USER_SETTINGS_XML_LOCATION);

        if (!useFile.exists()) {
            logger.debug("Default project file " + useFile.getAbsolutePath() + " does not exist, skipping setup");
            return;
        }

        try {

            logger.debug("Using default project file " + useFile.getAbsolutePath());

            Model defaultProject = new MavenXpp3Reader().read(new FileReader(useFile));

            processRepositories(session, defaultProject::getRepositories, defaultProject::getPluginRepositories);

            for (String profileId : session.getRequest().getActiveProfiles()) {

                Optional<Profile> defaultProfile =
                        defaultProject.getProfiles()
                                .stream().filter(mp->mp.getId().equals(profileId))
                                .findFirst();
                if (!defaultProfile.isPresent()) {
                    logger.debug("Profile "+profileId+" not found in default project definition");
                    continue;
                }

                processRepositories(session, ()->defaultProfile.get().getRepositories(),
                        ()->defaultProfile.get().getPluginRepositories());

            }

        } catch (Exception e) {
            throw new MavenExecutionException("Failed to process default project info from " + useFile, e);
        }
    }

    private void processRepositories(MavenSession session, Supplier<List<Repository>> repositoriesSrc,
                                     Supplier<List<Repository>> pluginRepositoriesSrc) throws Exception {

        RepositorySystemSession rss = session.getRepositorySession();

        convertRepositories(rss, "project", repositoriesSrc.get(),
                session.getTopLevelProject().getRemoteProjectRepositories());
        convertRepositories(rss, "plugin", pluginRepositoriesSrc.get(),
                session.getTopLevelProject().getRemotePluginRepositories());

        convertArtifactRepositories(rss, "project", repositoriesSrc.get(),
                session.getTopLevelProject().getRemoteArtifactRepositories());
        convertArtifactRepositories(rss, "plugin", pluginRepositoriesSrc.get(),
                session.getTopLevelProject().getPluginArtifactRepositories());

    }

    private void convertArtifactRepositories(RepositorySystemSession rss, String domain,
                                             List<Repository> modelRepositories,
                                             List<ArtifactRepository> registeredRepositories) throws Exception {

        for (Repository r : modelRepositories) {

            logger.info("Registering artifact " + domain + " repository " + r.getId());

            // from DefaultMavenProjectBuilder
            ArtifactRepository repo = repositorySystem.buildArtifactRepository(r);
            repositorySystem.injectMirror(rss, Collections.singletonList(repo));
            repositorySystem.injectProxy(rss, Collections.singletonList(repo));
            repositorySystem.injectAuthentication(rss, Collections.singletonList(repo));
            registeredRepositories.add(repo);

        }

    }

    private void convertRepositories(RepositorySystemSession rss, String domain,
                                     List<Repository> modelRepositories,
                                     List<RemoteRepository> registeredRepositories) {

        // Settings settings = settingsBuilder.buildSettings();

        for (Repository r : modelRepositories) {
            logger.info("Registering " + domain + " repository " + r.getId() + "->" + r.getUrl());

            String layout = r.getLayout();
            if (layout == null) {
                layout = repoLayout.getId();
            }
            RemoteRepository.Builder b = new RemoteRepository.Builder(r.getId(), layout, r.getUrl());

            // 🤦 Without this call the plugin repos are not authenticated.
            b.setAuthentication(rss.getAuthenticationSelector().getAuthentication(b.build()));

            RemoteRepository rr = b.build();
            registeredRepositories.add(rr);
        }
    }

    // copied from DefaultMavenSettingsBuilder
    @SuppressWarnings("SameParameterValue")
    private File getFile(String pathPattern, String basedirSysProp, String altLocationSysProp) {

        // -------------------------------------------------------------------------------------
        // Alright, here's the justification for all the regexp wizardry below...
        //
        // Continuum and other server-like apps may need to locate the user-level and
        // global-level settings somewhere other than ${user.home} and ${maven.home},
        // respectively. Using a simple replacement of these patterns will allow them
        // to specify the absolute path to these files in a customized components.xml
        // file. Ideally, we'd do full pattern-evaluation against the sysprops, but this
        // is a first step. There are several replacements below, in order to normalize
        // the path character before we operate on the string as a regex input, and
        // in order to avoid surprises with the File construction...
        // -------------------------------------------------------------------------------------

        String path = System.getProperty(altLocationSysProp);

        if (StringUtils.isEmpty(path)) {
            // TODO This replacing shouldn't be necessary as user.home should be in the
            // context of the container and thus the value would be interpolated by Plexus
            String basedir = System.getProperty(basedirSysProp);
            if (basedir == null) {
                basedir = System.getProperty("user.dir");
            }

            basedir = basedir.replaceAll("\\\\", "/");
            basedir = basedir.replaceAll("\\$", "\\\\\\$");

            path = pathPattern.replaceAll("\\$\\{" + basedirSysProp + "}", basedir);
            path = path.replaceAll("\\\\", "/");
            // ---------------------------------------------------------------------------------
            // I'm not sure if this last regexp was really intended to disallow the usage of
            // network paths as user.home directory. Unfortunately it did. I removed it and
            // have not detected any problems yet.
            // ---------------------------------------------------------------------------------
            // path = path.replaceAll( "//", "/" );

        }

        return new File(path).getAbsoluteFile();

    }

    @Override
    public void enableLogging(Logger logger) {
        this.logger = logger;
    }

}
