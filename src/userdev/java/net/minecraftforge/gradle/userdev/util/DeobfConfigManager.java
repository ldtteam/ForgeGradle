package net.minecraftforge.gradle.userdev.util;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.minecraftforge.gradle.userdev.UserDevPlugin;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import java.util.Map;
import java.util.Set;

/**
 * This class functions a central manager for handling deobfuscation configurations.
 *
 * In particular this manages the internal obfuscated dependency as well as adding
 * new deobfuscation source configurations to a project.
 */
public class DeobfConfigManager
{

    private static final DeobfConfigManager INSTANCE = new DeobfConfigManager();

    public static DeobfConfigManager getInstance()
    {
        return INSTANCE;
    }

    private final Map<Project, Set<DeobfuscationConfigurationMarker>> deobfConfigurationsPerProject = Maps.newConcurrentMap();
    
    private DeobfConfigManager()
    {
    }

    /**
     * This method should be invoked by ForgeGradle to make this manager handle a given project for a given remapper.
     * It should only be invoked once.
     *
     * This method registers several different additional configurations, for every sourceset it register a deobf source variant for the following configurations:
     *   - compile
     *   - runtime
     *   - api
     *   - implementation
     *   - compileOnly
     *   - runtimeOnly
     *
     * The actual name of the configuration is dependent on the actual source set, and might vary, but for all configurations the following schema is followed:
     * When the configuration that is supposed to be used as target is named 'X' then the deobfuscation source config is named 'XDeobf'.
     *
     * @param project The project to apply the manager to.
     * @param remapper The remapper used in the project.
     */
    public void onAppliedToProject(final Project project, final DependencyRemapper remapper) {
        addDeobfConfiguration(project, remapper);

        project.afterEvaluate(p -> {
            final Configuration internalObfConfig = project.getConfigurations().maybeCreate(UserDevPlugin.OBF);

            for (final DeobfuscationConfigurationMarker marker : deobfConfigurationsPerProject.getOrDefault(project, Sets.newHashSet()))
            {
                DependencySet deobfDeps = marker.getDeobfConfiguration().getDependencies();
                deobfDeps.stream()
                  .filter(ExternalModuleDependency.class::isInstance)
                  .forEach(dep -> {
                      p.getDependencies().add(marker.getTargetConfiguration().getName(), marker.getRemapper().remap(dep));
                      internalObfConfig.getDependencies().add(dep);
                  });
            }
        });
    }

    /**
     * This method registers several different additional configurations, for every sourceset it register a deobf source variant for the following configurations:
     *   - compile
     *   - runtime
     *   - api
     *   - implementation
     *   - compileOnly
     *   - runtimeOnly
     *
     * The actual name of the configuration is dependent on the actual source set, and might vary, but for all configurations the following schema is followed:
     * When the configuration that is supposed to be used as target is named 'X' then the deobfuscation source config is named 'XDeobf'.
     *
     * @param project The project to add the configuraitons to.
     * @param remapper The remapper used in the project.
     */
    public void addDeobfConfiguration(final Project project,
      final DependencyRemapper remapper) {
        Convention convention = project.getConvention();
        JavaPluginConvention javaPluginConvention = convention.getPlugin(JavaPluginConvention.class);
        SourceSetContainer sourceSets = javaPluginConvention.getSourceSets();

        for (final SourceSet sourceSet : sourceSets)
        {
            addDeobfConfiguration(project, sourceSet, remapper);
        }
    }

    /**
     * This method registers several different additional configurations, for a given sourceset it register a deobf source variant for the following configurations:
     *   - compile
     *   - runtime
     *   - api
     *   - implementation
     *   - compileOnly
     *   - runtimeOnly
     *
     * The actual name of the configuration is dependent on the actual source set, and might vary, but for all configurations the following schema is followed:
     * When the configuration that is supposed to be used as target is named 'X' then the deobfuscation source config is named 'XDeobf'.
     *
     * @param project The project to add the configurations to.
     * @param sourceSet The sourceSet to add the configurations for.
     * @param remapper The remapper used in the project.
     */
    public void addDeobfConfiguration(final Project project,
      final SourceSet sourceSet,
      final DependencyRemapper remapper) {
        final Configuration sourceSetCompileConfiguration = project.getConfigurations().maybeCreate(sourceSet.getCompileConfigurationName());
        final Configuration sourceSetRuntimeConfiguration = project.getConfigurations().maybeCreate(sourceSet.getRuntimeConfigurationName());
        final Configuration sourceSetCompileOnlyConfiguration = project.getConfigurations().maybeCreate(sourceSet.getCompileOnlyConfigurationName());
        final Configuration sourceSetRuntimeOnlyConfiguration = project.getConfigurations().maybeCreate(sourceSet.getRuntimeOnlyConfigurationName());
        final Configuration sourceSetImplementationConfiguration = project.getConfigurations().maybeCreate(sourceSet.getImplementationConfigurationName());
        final Configuration sourceSetApiConfiguration = project.getConfigurations().maybeCreate(sourceSet.getApiConfigurationName());

        addDeobfConfiguration(
          project,
          sourceSetCompileConfiguration,
          remapper
        );
        addDeobfConfiguration(
          project,
          sourceSetRuntimeConfiguration,
          remapper
        );
        addDeobfConfiguration(
          project,
          sourceSetCompileOnlyConfiguration,
          remapper
        );
        addDeobfConfiguration(
          project,
          sourceSetRuntimeOnlyConfiguration,
          remapper
        );
        addDeobfConfiguration(
          project,
          sourceSetImplementationConfiguration,
          remapper
        );
        addDeobfConfiguration(
          project,
          sourceSetApiConfiguration,
          remapper
        );
    }

    /**
     * This method registers a single deobfuscation source configuration, given a target configuration.
     *
     * The actual name of the configuration is dependent on the actual source set, and might vary, but for all configurations the following schema is followed:
     * When the configuration that is supposed to be used as target is named 'X' then the deobfuscation source config is named 'XDeobf'.
     *
     * @param project The project to add the configuration to.
     * @param target The target configuration to add the deobfed dependencies to.
     * @param remapper The remapper used in the project.
     */
    public void addDeobfConfiguration(final Project project,
      final Configuration target,
      final DependencyRemapper remapper) {
        final String newDeobfName = target.getName() + StringUtils.capitalize(UserDevPlugin.DEOBF);
        addDeobfConfiguration(project, target, remapper, newDeobfName);
    }

    /**
     * This method registers a single deobfuscation source configuration, given a target configuration.
     * The name of the deobfuscation source configuration is passed as a parameter.
     *
     * @param project The project to add the configuration to.
     * @param target The target configuration to add the deobfed dependencies to.
     * @param remapper The remapper used in the project.
     * @param name The name of the source deobfuscation configuration.
     */
    public void addDeobfConfiguration(final Project project, final Configuration target, final DependencyRemapper remapper, final String name)
    {
        final Configuration deobfConfig = project.getConfigurations().maybeCreate(name);
        startTrackingDeobfuscationConfiguration(project, target, remapper, deobfConfig);
    }

    /**
     * This method starts tracking a given configuration as a deobfuscation source.
     *
     * @param project The project to add the configuration to.
     * @param target The target configuration to add the deobfed dependencies to.
     * @param remapper The remapper used in the project.
     * @param deobfConfig The source deobfuscation configuration.
     */
    public void startTrackingDeobfuscationConfiguration(final Project project, final Configuration target, final DependencyRemapper remapper, final Configuration deobfConfig)
    {
        deobfConfigurationsPerProject.computeIfAbsent(project, (p) -> Sets.newConcurrentHashSet()).add(new DeobfuscationConfigurationMarker(deobfConfig, target, remapper));
    }

    /**
     * This
     * @param project
     * @param moduleDependency
     */
    public void addPomArtifact(
      final Project project,
      final ExternalModuleDependency moduleDependency) {
        if (moduleDependency.getArtifacts().size() == 1) {
            addPomArtifactFrom(
              moduleDependency,
              moduleDependency.getArtifacts().iterator().next()
            );
        } else {
            if (moduleDependency.getArtifacts().stream().map(DependencyArtifact::getName).distinct().count() > 1) {
                project.getLogger().error(String.format("Multiple different artifact names found. The dependency: %s:%s:%s contains multiple artifacts with different names. POM resolution not possible.",
                  moduleDependency.getGroup(),
                  moduleDependency.getName(),
                  moduleDependency.getVersion()));
            } else {
                addPomArtifactFrom(
                  moduleDependency,
                  moduleDependency.getArtifacts().iterator().next()
                );
            }
        }
    }

    private void addPomArtifactFrom(final ExternalModuleDependency moduleDependency, final DependencyArtifact artifact) {
        final DefaultDependencyArtifact pomArtifact = new DefaultDependencyArtifact(
          artifact.getName(),
          "pom",
          "pom",
          "",
          null
        );

        moduleDependency.addArtifact(pomArtifact);
    }

    private final class DeobfuscationConfigurationMarker {
        private final Configuration deobfConfiguration;
        private final Configuration targetConfiguration;
        private final DependencyRemapper remapper;

        private DeobfuscationConfigurationMarker(
          final Configuration deobfConfiguration,
          final Configuration targetConfiguration,
          final DependencyRemapper remapper) {
            this.deobfConfiguration = deobfConfiguration;
            this.targetConfiguration = targetConfiguration;
            this.remapper = remapper;
        }

        public Configuration getDeobfConfiguration()
        {
            return deobfConfiguration;
        }

        public Configuration getTargetConfiguration()
        {
            return targetConfiguration;
        }

        public DependencyRemapper getRemapper()
        {
            return remapper;
        }
    }
}
