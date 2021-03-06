package com.hari.gradle.spark.plugin;

import static com.hari.gradle.spark.plugin.Constants.DOWNLOAD_DEPENDENCIES_TASK;
import static com.hari.gradle.spark.plugin.Constants.DOWNLOAD_DEPENDENCIES_TASK_DESC;
import static com.hari.gradle.spark.plugin.Constants.LAUNCH_SPARK_TASK;
import static com.hari.gradle.spark.plugin.Constants.LAUNCH_SPARK_TASK_DESC;
import static com.hari.gradle.spark.plugin.Constants.PREPARE_CLUSTER_SUBMIT_TASK;
import static com.hari.gradle.spark.plugin.Constants.PREPARE_CLUSTER_SUBMIT_TASK_DESC;
import static com.hari.gradle.spark.plugin.Settings.SETTINGS_EXTN;
import static com.hari.gradle.spark.plugin.SparkRunMode.getRunMode;
import static java.util.Arrays.asList;

import java.net.URI;
import java.net.URISyntaxException;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.specs.Spec;

import com.hari.gradle.spark.plugin.tasks.DownloadDependencies;
import com.hari.gradle.spark.plugin.tasks.LaunchSparkTask;
import com.hari.gradle.spark.plugin.tasks.PrepareForClusterSubmit;

/**
 * Gradle plugin to build and launch a spark application , this was created out
 * of the necessity of skipping so many mundane repetitive steps to deploy a
 * spark application especially in clusters. Intends to support three modes. 1)
 * local - Drivers and executors run on the same node. 2) client-mode - Driver
 * runs in the node launching the application and executors run in the cluster
 * nodes. 3) cluster-mode - Driver runs in one of the nodes in the cluster and
 * executors run in the cluster as well.
 * 
 * @author harim
 *
 */

public class SparkPluginProject implements Plugin<Project> {

	private static final String GROUP = "Spark Runner";

	@Override
	public void apply(Project p) {
		p.getPluginManager().apply(org.gradle.api.plugins.scala.ScalaPlugin.class);
		p.getRepositories().addAll(asList(p.getRepositories().mavenCentral(), p.getRepositories().mavenLocal(),
				p.getRepositories().maven(new Action<MavenArtifactRepository>() {
					@Override
					public void execute(MavenArtifactRepository customRepo) {
						try {
							customRepo.setUrl(new URI("http://repo.gradle.org/gradle/libs-releases-local"));
						} catch (URISyntaxException urise) {
							SPGLogger.logError.accept("Incorrect URL provided");
						}
					}
				})));
		final Settings settings = p.getExtensions().create(SETTINGS_EXTN, Settings.class);
		Task downloadDeps = p.getTasks().create(DOWNLOAD_DEPENDENCIES_TASK, DownloadDependencies.class);
		downloadDeps.setDescription(DOWNLOAD_DEPENDENCIES_TASK_DESC);
		downloadDeps.setGroup(GROUP);
		downloadDeps.dependsOn(p.getTasks().getByName("clean"), p.getTasks().getByName("jar"));

		Task prepClusterSubmit = p.getTasks().create(PREPARE_CLUSTER_SUBMIT_TASK, PrepareForClusterSubmit.class);
		prepClusterSubmit.setDescription(PREPARE_CLUSTER_SUBMIT_TASK_DESC);
		prepClusterSubmit.setGroup(GROUP);
		prepClusterSubmit.dependsOn(downloadDeps);
		prepClusterSubmit.onlyIf(new Spec<Task>() {
			@Override
			public boolean isSatisfiedBy(Task arg0) {
				String master = settings.getMaster();
				String mode = settings.getMode();
				SparkRunMode spkRunMode = getRunMode.apply(master).apply(mode);
				return spkRunMode == SparkRunMode.YARN_CLIENT || spkRunMode == SparkRunMode.YARN_CLUSTER;
			}
		});
		Task launchSpark = p.getTasks().create(LAUNCH_SPARK_TASK, LaunchSparkTask.class);
		launchSpark.setDescription(LAUNCH_SPARK_TASK_DESC);
		launchSpark.dependsOn(prepClusterSubmit);
		launchSpark.setGroup(GROUP);
	}
}
