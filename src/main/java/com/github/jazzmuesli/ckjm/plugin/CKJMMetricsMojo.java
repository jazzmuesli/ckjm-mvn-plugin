package com.github.jazzmuesli.ckjm.plugin;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.bcel.util.ClassPath;
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

		ClassPath cp = ClassPath.SYSTEM_CLASS_PATH;
        if (new File(project.getBuild().getOutputDirectory()).exists()) {
        	// use output dir or delegate to previous cp
        	cp = new ClassPath(cp, project.getBuild().getOutputDirectory());
        }
        if (new File(project.getBuild().getTestOutputDirectory()).exists()) {
        	// use test output dir or delegate to previous cp
        	cp = new ClassPath(cp, project.getBuild().getTestOutputDirectory());
        }
        getLog().info("classpath: " + cp);
        
		processSourceDirectory(cp, project.getBuild().getOutputDirectory());
		processSourceDirectory(cp, project.getBuild().getTestOutputDirectory());
	}

	protected void processSourceDirectory(ClassPath cp, String dirName) {
		try {
			getLog().info("Processing " + dirName);
			if (new File(dirName).exists()) {
				List<String> files = Files.walk(Paths.get(dirName)).filter(p -> p.toFile().getName().endsWith(".class"))
						.map(s -> s.toFile().getAbsolutePath()).collect(Collectors.toList());
				List<String> classNames = files.stream().map(f-> getClassNameFromFile(dirName, f)).collect(Collectors.toList());
				getLog().info("Found " + files.size() + " files in " + dirName);
				File ckjmFilesFile = new File(dirName).toPath().resolve("ckjm-files.txt").toFile();
				getLog().info("Writing " + files.size() + " files to " + ckjmFilesFile);
				Files.write(ckjmFilesFile.toPath(), files, StandardOpenOption.CREATE);
				
				File ckjmClassesFile = new File(dirName).toPath().resolve("ckjm-classes.txt").toFile();
				getLog().info("Writing " + classNames.size() + " classes to " + ckjmClassesFile);
				Files.write(ckjmClassesFile.toPath(), classNames, StandardOpenOption.CREATE);
				
				CSVCkjmOutputHandler outputPlain = new CSVCkjmOutputHandler(dirName + "/ckjm.csv");
				MetricsFilter.runMetrics(cp, classNames.toArray(new String[0]), outputPlain, true);
			}
		} catch (Exception e) {
			getLog().error(e.getMessage(), e);
		}
	}

	/**
	 * convert
	 * /private/tmp/jfreechart/target/test-classes/org/jfree/chart/renderer/category/LevelRendererTest.class
	 * to
	 * org.jfree.chart.renderer.category.LevelRendererTest
	 * @param dirName /private/tmp/jfreechart/target/test-classes
	 * @param f /private/tmp/jfreechart/target/test-classes/org/jfree/chart/renderer/category/LevelRendererTest.class
	 * @return org.jfree.chart.renderer.category.LevelRendererTest
	 */
	protected String getClassNameFromFile(String dirName, String f) {
		return new File(f).getAbsolutePath().replaceAll(dirName, "").replace("/", ".").replaceAll(".class$", "").replaceAll("^\\.", "");
	}

}
