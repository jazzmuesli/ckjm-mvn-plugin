package com.github.jazzmuesli.ckjm.plugin;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.bcel.util.ClassPath;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;

import gr.spinellis.ckjm.MetricsFilter;

@Mojo(name = "metrics", defaultPhase = LifecyclePhase.NONE, requiresDependencyResolution = ResolutionScope.NONE)
public class CKJMMetricsMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	protected MavenProject project;

	@Component
	private PluginDescriptor pluginDescriptor;
	
	@Parameter(defaultValue = "${plugin.artifactMap}", required = true, readonly = true)
	private Map<String, Artifact> pluginArtifactMap;
	
	@Parameter(defaultValue = "${localRepository}", required = true, readonly = true)
	private ArtifactRepository localRepository;

	@Component
	private RepositorySystem repositorySystem;

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

		// this is a pretty ugly way to get classpath of dependencies.
    	LinkedHashSet<String> classpath = prepareClasspath(project, localRepository,
				repositorySystem, pluginArtifactMap, getLog());

		ClassPath cp = new ClassPath(classpath.stream().collect(Collectors.joining(File.pathSeparator)));
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

	private static final String CTESTER_PACKAGE = "com.github.jazzmuesli:ckjm-mvn-plugin";

	private static Set<Artifact> getDevArtifacts(ArtifactRepository localRepository, RepositorySystem repositorySystem,
			Map<String, Artifact> pluginArtifactMap) {
		ArtifactResolutionRequest request = new ArtifactResolutionRequest()
				.setArtifact(pluginArtifactMap.get(CTESTER_PACKAGE)).setResolveTransitively(true)
				.setLocalRepository(localRepository);
		ArtifactResolutionResult result = repositorySystem.resolve(request);
		return result.getArtifacts();
	}

	/**
	 * TODO: make it less hacky.
	 * 
	 * Here we need classpath of the target project + target/test-clases,
	 * target/classes + dependencies of this plugin. Note that this plugin won't be
	 * added to target project's pom.xml, but rather executed mvn
	 * org.pavelreich.saaremaa:plugin:analyse
	 * 
	 * @param log
	 * @param project
	 * @param pluginArtifactMap
	 * @param repositorySystem
	 * @param localRepository
	 * @return
	 * @throws MojoExecutionException
	 */
	public static LinkedHashSet<String> prepareClasspath(MavenProject project, ArtifactRepository localRepository,
			RepositorySystem repositorySystem, Map<String, Artifact> pluginArtifactMap, Log log)
			throws MojoExecutionException {
		LinkedHashSet<String> classpath = new LinkedHashSet<String>();

		try {
			log.info("project: "+project);
			project.getArtifacts().stream().filter(x -> x.getGroupId().equals(project.getGroupId())).forEach(x -> log.info("artifact: " + x));
			classpath.addAll(project.getTestClasspathElements());
			
			classpath.addAll(project.getCompileClasspathElements());
			// copied from
			// https://github.com/tbroyer/gwt-maven-plugin/blob/54fe4621d1ee5127b14030f6e1462de44bace901/src/main/java/net/ltgt/gwt/maven/CompileMojo.java#L295
			ClassWorld world = new ClassWorld();
			ClassRealm realm;
			log.info("pluginArtifactMap:" + pluginArtifactMap.keySet());
			try {
				realm = world.newRealm("gwt", null);
				for (String elt : project.getCompileSourceRoots()) {
					URL url = new File(elt).toURI().toURL();
					realm.addURL(url);
				}
				for (String elt : project.getCompileClasspathElements()) {
					URL url = new File(elt).toURI().toURL();
					realm.addURL(url);
				}
				for (Artifact elt : getDevArtifacts(localRepository, repositorySystem, pluginArtifactMap)) {
					URL url = elt.getFile().toURI().toURL();
					realm.addURL(url);
					log.info("transitive classpath: " + url);
				}
				URL pluginUrls = pluginArtifactMap.get(CTESTER_PACKAGE).getFile().toURI().toURL();
				realm.addURL(pluginUrls);
				List<String> urls = Arrays.asList(realm.getURLs()).stream().map(x -> {
					try {
						return new File(x.toURI()).getAbsolutePath();
					} catch (Exception e) {
						throw new IllegalArgumentException(e.getMessage(), e);
					}
				}).collect(Collectors.toList());
				urls.stream().filter(p->isAcceptedDependency(p)).forEach(x -> classpath.add(x));
			} catch (Exception e) {
				throw new MojoExecutionException(e.getMessage(), e);
			}

		} catch (DependencyResolutionRequiredException e1) {
			log.error(e1.getMessage(), e1);
		}
		return classpath;
	}

	private static boolean isAcceptedDependency(String p) {
		return true;
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
