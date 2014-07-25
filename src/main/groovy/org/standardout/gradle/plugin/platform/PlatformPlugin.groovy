/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.standardout.gradle.plugin.platform

import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.jar.*

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedArtifact
import org.eclipse.core.runtime.internal.adaptor.EclipseEnvironmentInfo
import org.osgi.framework.Version
import org.osgi.framework.Constants
import org.standardout.gradle.plugin.platform.internal.BundleArtifact;
import org.standardout.gradle.plugin.platform.internal.BundlesAction;
import org.standardout.gradle.plugin.platform.internal.DefaultFeature
import org.standardout.gradle.plugin.platform.internal.Feature
import org.standardout.gradle.plugin.platform.internal.FileBundleArtifact;
import org.standardout.gradle.plugin.platform.internal.ResolvedBundleArtifact;
import org.standardout.gradle.plugin.platform.internal.SourceBundleArtifact
import org.standardout.gradle.plugin.platform.internal.config.BndConfig;
import org.standardout.gradle.plugin.platform.internal.util.FeatureUtil;
import org.standardout.gradle.plugin.platform.internal.util.gradle.DependencyHelper;

/**
 * OSGi platform plugin for Gradle.
 * 
 * @author Robert Gregor
 * @author Simon Templer
 */
public class PlatformPlugin implements Plugin<Project> {

	public static final String TASK_BUNDLES = 'bundles'
	public static final String CONF_PLATFORM = 'platform'
	public static final String CONF_AUX = 'platformaux'
	
	private Project project
	
	private File bundlesDir
	private File featureFile
	private File categoryFile
	private File featuresDir
	private File downloadsDir
	
	@Override
	public void apply(Project project) {
		this.project = project
		
		configureEnvironment(project)
		
		// ensure download-task plugin is applied
		project.apply(plugin: 'download-task')

		// register extension
		project.extensions.create('platform', PlatformPluginExtension, project)
		
		// initialize file/directory members
		// names are fixed because of update site conventions
		bundlesDir = new File(project.buildDir, 'plugins')
		featureFile = new File(project.buildDir, 'feature.xml')
		categoryFile = new File(project.buildDir, 'category.xml')
		featuresDir = new File(project.buildDir, 'features')
		downloadsDir = new File(project.buildDir, 'eclipse-downloads')
		
		// create configuration
		project.configurations.create CONF_PLATFORM
		project.configurations.create CONF_AUX
		
		project.afterEvaluate {
			// feature version default
			if (project.platform.featureVersion == null) {
				if (project.version) {
					try {
						project.platform.featureVersion = Version.parseVersion(project.version).toString()
					} catch (e) {
						// ignore
					}
				}
			}
			if (project.platform.featureVersion == null) {
				project.platform.featureVersion = '1.0.0'
			}
		}

		// create bundles task
		Task bundlesTask = project.task(TASK_BUNDLES)
		
		// depend on the artifacts (rather than a task)
		//XXX not sure if this really has any effect
		bundlesTask.dependsOn(project.configurations.getByName(CONF_PLATFORM).allArtifacts.buildDependencies)
		
		// define bundles task
		bundlesTask.doFirst(new BundlesAction(project, bundlesDir))
		
		/*
		 * Clean task.
		 */
		project.task('clean').doLast {
			featureFile.delete()
			categoryFile.delete()
			featuresDir.deleteDir()
			bundlesDir.deleteDir()
			// don't delete download in default clean
//			downloadsDir.deleteDir()
			project.platform.updateSiteDir.deleteDir()
			project.platform.updateSiteZipFile.delete()
		}
		
		/*
		 * Generate a feature.xml from the target file.
		 */
		Task generateFeatureTask = project.task('generateFeature', dependsOn: bundlesTask).doFirst {
			featureFile.parentFile.mkdirs()
			
			// create platform feature.xml
			Feature feature = new DefaultFeature(
				id: project.platform.featureId,
				label: project.platform.featureName,
				version: project.platform.featureVersion,
				providerName: project.platform.featureProvider,
				bundles: project.platform.artifacts.values().toList(),
				includedFeatures: project.platform.features.values()
			)
			
			use(FeatureUtil) {
				feature.createFeatureXml(featureFile)
			}
			
			project.logger.info 'Generated feature.xml.'
		}
		
		/*
		 * Create Feature JAR.
		 */
		Task bundleFeatureTask = project.task('bundleFeature', dependsOn: generateFeatureTask).doFirst {
			featuresDir.mkdirs()
			// create feature jar
			def target = new File(featuresDir,
				"${project.platform.featureId}_${project.platform.featureVersion}.jar")
			project.ant.zip(destfile: target) {
				fileset(dir: project.buildDir) {
					include(name: 'feature.xml')
				}
			}
			
			project.logger.info 'Packaged feature.'
		}
		
		/*
		 * Create JARs for additional defined features. 
		 */
		Task additionalFeaturesTask = project.task('additionalFeatures', dependsOn: bundlesTask).doFirst {
			project.platform.features.values().each { Feature feature ->
				File featureJar = new File(featuresDir, "${feature.id}_${feature.version}.jar")
				
				use(FeatureUtil) {
					feature.createJar(featureJar)
				}
			}
		}
		
		/*
		 * Generate category.xml.
		 */
		Task generateCategoryTask = project.task('generateCategory', dependsOn: additionalFeaturesTask).doFirst {
			categoryFile.parentFile.mkdirs()
			
			categoryFile.withWriter('UTF-8'){
				w ->
				def xml = new groovy.xml.MarkupBuilder(w)
				xml.setDoubleQuotes(true)
				xml.mkp.xmlDeclaration(version:'1.0', encoding: 'UTF-8')
				
				xml.site{
					// the main platform feature
					feature(url: "features/${project.platform.featureId}_${project.platform.featureVersion}.jar",
							id: project.platform.featureId,
							version: project.platform.featureVersion) {
						// associate the feature to the category
						category(name: project.platform.categoryId)
					}
							
					// additional features
					project.platform.features.values().each { Feature f ->
						feature(url: "features/${f.id}_${f.version}.jar",
								id: f.id,
								version: f.version) {
							// associate the feature to the category
							category(name: project.platform.categoryId)
						}
					}
							
					// define the category
					'category-def'(name: project.platform.categoryId, label: project.platform.categoryName)
				}
			}
			
			project.logger.info 'Generated category.xml.'
		}
		
		/*
		 * Task that checks if Eclipse is there / Eclipse home is specified.
		 */
		Task checkEclipseTask = project.task('checkEclipse').doFirst {
			// path to Eclipse provided in extension
			if (project.platform.eclipseHome != null) {
				return
			}
			
			// from system property
			def eclipseHome = System.properties['ECLIPSE_HOME']
			
			if (!eclipseHome) {
				File downloadedEclipse = new File(downloadsDir, 'eclipse')
				if (downloadedEclipse.exists()) {
					// downloaded Eclipse already exists
					eclipseHome = downloadedEclipse
				}
				else {
					// download and extract Eclipse
					def artifacts = project.platform.eclipseMirror
					if (artifacts.containsKey(project.ext.osgiOS) && artifacts[project.ext.osgiOS].containsKey(project.ext.osgiWS) &&
						artifacts[project.ext.osgiOS][project.ext.osgiWS].containsKey(project.ext.osgiArch)) {
						
						// Download artifact
						String artifactDownloadUrl = artifacts[project.ext.osgiOS][project.ext.osgiWS][project.ext.osgiArch]
						def filename = artifactDownloadUrl.substring(artifactDownloadUrl.lastIndexOf('/') + 1)
						def artifactZipPath = new File(downloadsDir, filename)
						def artifactZipPathPart = new File(downloadsDir, filename + '.part')
						if (!artifactZipPath.exists()) {
							project.download {
								src artifactDownloadUrl
								dest artifactZipPathPart
								overwrite true
							}
							artifactZipPathPart.renameTo(artifactZipPath)
						}
				
						// Unzip artifact
						println('Copying ' + name + ' ...')
						def artifactInstallPath = downloadsDir
						if (artifactZipPath.name.endsWith('.zip')) {
							project.ant.unzip(src: artifactZipPath, dest: artifactInstallPath)
						} else {
							project.ant.untar(src: artifactZipPath, dest: artifactInstallPath, compression: 'gzip')
						}
						if (downloadedEclipse.exists()) {
							eclipseHome = downloadedEclipse
						}
						else {
							project.logger.error 'Could not find "eclipse" directory in extracted artifact'
						}
					}
					else {
						project.logger.error 'Unable to download eclipse artifact'
					}
					
				}
			}
			
			if (eclipseHome) {
				project.platform.eclipseHome = eclipseHome as File
			}
		}
		
		/*
		 * Build a p2 repository with all the bundles
		 */
		Task updateSiteTask = project.task('updateSite', dependsOn: [bundleFeatureTask, generateCategoryTask, checkEclipseTask]).doFirst {
			project.platform.updateSiteDir.mkdirs()
			
			assert project.platform.eclipseHome
			def eclipseHome = project.platform.eclipseHome.absolutePath
			
			// find launcher jar
			def launcherFiles = project.ant.fileScanner {
				fileset(dir: eclipseHome) {
					include(name: 'plugins/org.eclipse.equinox.launcher_*.jar')
				}
			}
			def launcherJar = launcherFiles.iterator().next()
			assert launcherJar
			
			project.logger.info "Using Eclipse at $eclipseHome for p2 repository generation."
			
			/*
			 * Documentation on Publisher:
			 * http://help.eclipse.org/juno/index.jsp?topic=/org.eclipse.platform.doc.isv/guide/p2_publisher.html
			 * http://wiki.eclipse.org/Equinox/p2/Publisher
			 */
			
			// launch Publisher for Features and Bundles
			def repoDirUri = URLDecoder.decode(project.platform.updateSiteDir.toURI().toString(), 'UTF-8')
			def categoryFileUri = URLDecoder.decode(categoryFile.toURI().toString(), 'UTF-8')
			project.exec {
				commandLine 'java', '-jar', launcherJar,
					'-application', 'org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher',
					'-metadataRepository', repoDirUri,
					'-artifactRepository', repoDirUri,
					'-source', project.buildDir,
					'-configs', 'ANY', '-publishArtifacts', '-compress'
			}
			
			// launch Publisher for category / site.xml
			project.exec {
				commandLine 'java', '-jar', launcherJar,
					'-application', 'org.eclipse.equinox.p2.publisher.CategoryPublisher',
					'-metadataRepository', repoDirUri,
					'-categoryDefinition', categoryFileUri,
					'-compress'
			}
			
			project.logger.info 'Built p2 repository.'
		}
		
		/*
		 * Archive update site.
		 */
		Task siteArchiveTask = project.task('updateSiteZip', dependsOn: [updateSiteTask]).doFirst {
			project.ant.zip(destfile: project.platform.updateSiteZipFile) {
				fileset(dir: project.platform.updateSiteDir) {
					include(name: '**')
				}
			}
		}
	}
	
	
	
	/**
	 * Guess current environment and store information in project.ext.
	 */
	def configureEnvironment(Project project) {
		project.with {
			def eei = EclipseEnvironmentInfo.getDefault()
			if (!ext.properties.containsKey('osgiOS')) {
				ext.osgiOS = eei.getOS()
			}
			if (!ext.properties.containsKey('osgiWS')) {
				ext.osgiWS = eei.getWS()
			}
			if (!ext.properties.containsKey('osgiArch')) {
				ext.osgiArch = eei.getOSArch()
			}
		}
	}

}
