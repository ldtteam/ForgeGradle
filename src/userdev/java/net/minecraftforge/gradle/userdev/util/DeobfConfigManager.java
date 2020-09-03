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

    public void addDeobfConfiguration(final Project project,
      final Configuration target,
      final DependencyRemapper remapper) {
        final String newDeobfName = target.getName() + StringUtils.capitalize(UserDevPlugin.DEOBF);
        addDeobfConfiguration(project, target, remapper, newDeobfName);
    }

    public void addDeobfConfiguration(final Project project, final Configuration target, final DependencyRemapper remapper, final String name)
    {
        final Configuration deobfConfig = project.getConfigurations().maybeCreate(name);
        addDeobfConfiguration(project, target, remapper, deobfConfig);
    }

    public void addDeobfConfiguration(final Project project, final Configuration target, final DependencyRemapper remapper, final Configuration deobfConfig)
    {
        final Configuration internalObfConfig = project.getConfigurations().maybeCreate(UserDevPlugin.OBF);
        deobfConfigurationsPerProject.computeIfAbsent(project, (p) -> Sets.newConcurrentHashSet()).add(new DeobfuscationConfigurationMarker(deobfConfig, target, remapper));
    }

    public void addPomArtifact(
      final Project project,
      final ExternalModuleDependency moduleDependency) {
        if (moduleDependency.getArtifacts().isEmpty()) {
            project.getLogger().error(String.format("Could not add POM artifact for dependency. The dependency: %s:%s:%s does not contain any artifacts.",
              moduleDependency.getGroup(),
              moduleDependency.getName(),
              moduleDependency.getVersion()));
        } else if (moduleDependency.getArtifacts().size() == 1) {
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
