package com.github.jazzmuesli.ckjm.plugin;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;

import gr.spinellis.ckjm.MetricsFilter;

@Mojo(name = "metrics", defaultPhase = LifecyclePhase.NONE, requiresDependencyResolution = ResolutionScope.NONE)
public class CKJMMetricsMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	protected MavenProject project;

	@Component
	private PluginDescriptor pluginDescriptor;

	public void execute() throws MojoExecutionException {
		File buildDirectory = new File(project.getBuild().getOutputDirectory());

		getLog().info("buildDirectory: " + buildDirectory);
		// from
		// https://stackoverflow.com/questions/37265797/how-to-add-maven-build-output-to-plugin-classloader
		// add the build directory to the classpath for the classloader
		try {
			ClassRealm realm = pluginDescriptor.getClassRealm();
			realm.addURL(buildDirectory.toURI().toURL());
		} catch (MalformedURLException e1) {
			e1.printStackTrace();
			getLog().error(e1);
		}

		processSourceDirectory(project.getBuild().getOutputDirectory());
		processSourceDirectory(project.getBuild().getTestOutputDirectory());
	}

	protected void processSourceDirectory(String dirName) {
		try {
			getLog().info("Processing " + dirName);
			if (new File(dirName).exists()) {
				List<String> files = Files.walk(Paths.get(dirName)).filter(p -> p.toFile().getName().endsWith(".class"))
						.map(s -> s.toFile().getAbsolutePath()).collect(Collectors.toList());

				getLog().info("Found " + files.size() + " files in " + dirName);
				CSVCkjmOutputHandler outputPlain = new CSVCkjmOutputHandler(dirName + "/ckjm.csv");
				MetricsFilter.runMetrics(files.toArray(new String[0]), outputPlain, false);
			}
		} catch (Exception e) {
			getLog().error(e.getMessage(), e);
		}
	}

}
