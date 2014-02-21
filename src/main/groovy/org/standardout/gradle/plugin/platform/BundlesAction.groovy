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

import org.gradle.api.Action
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.osgi.framework.Version;
import org.standardout.gradle.plugin.platform.internal.BndConfig;
import org.standardout.gradle.plugin.platform.internal.BndHelper;
import org.standardout.gradle.plugin.platform.internal.BundleArtifact;
import org.standardout.gradle.plugin.platform.internal.DependencyHelper;
import org.standardout.gradle.plugin.platform.internal.FileBundleArtifact;
import org.standardout.gradle.plugin.platform.internal.ResolvedBundleArtifact;
import org.standardout.gradle.plugin.platform.internal.SourceBundleArtifact;

import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Jar;


/**
 * Action that creates bundles.
 * 
 * @author Robert Gregor
 * @author Simon Templer
 */
class BundlesAction implements Action<Task> {

	private final Project project
	
	private final File targetDir
	
	BundlesAction(Project project, File targetDir) {
		this.project = project
		this.targetDir = targetDir
	}
	
	@Override
	public void execute(Task task) {
		Configuration config = project.getConfigurations().getByName(PlatformPlugin.CONF_PLATFORM)
		ResolvedConfiguration resolved = config.resolvedConfiguration
		
		if (project.logger.infoEnabled) {
			// output some debug information on the configuration
			configInfo(config, project.logger.&info)
			resolvedConfigInfo(resolved.resolvedArtifacts, project.logger.&info)
		}
		
		// collect dependency files (to later be able to determine pure file dependencies)
		def dependencyFiles = config.collect()

		// create artifact representations
		// id is mapped to artifacts
		def artifacts = project.platform.artifacts 
		resolved.resolvedArtifacts.each {
			BundleArtifact artifact = new ResolvedBundleArtifact(it, project)
			artifacts[artifact.id] = artifact
			
			dependencyFiles.remove(artifact.file)
		}
		
		// dependency source artifacts
		if (project.platform.fetchSources) {
			def sourceArtifacts = DependencyHelper.resolveSourceArtifacts(config, project.configurations)
			sourceArtifacts.each {
				SourceBundleArtifact artifact = new SourceBundleArtifact(it, project)
				artifacts[artifact.id] = artifact
				
				// check if associated bundle is found, associated source to 
				if (artifacts[artifact.unifiedName]) {
					BundleArtifact bundle = artifacts[artifact.unifiedName]
					if (bundle) {
						artifact.parentBundle = bundle
						bundle.sourceBundle = artifact
					}
					else {
						project.logger.warn "No parent bundle for source ${artifact.id} found"
					}
				}
			}
			
			// output info
			if (project.logger.infoEnabled) {
				resolvedConfigInfo('Source artifacts', sourceArtifacts, project.logger.&info)
			}
		}
		
		// file artifacts
		dependencyFiles.each {
			// for all remaining dependencies assume they are local files
			FileBundleArtifact artifact = new FileBundleArtifact(it, project)
			artifacts[artifact.id] = artifact
			
			if (project.platform.fetchSources) {
				//TODO look for and associate source bundle
			}
		}
		
		targetDir.mkdirs()

		if(!artifacts) {
			project.logger.warn 'No platform artifacts could be found, no bundles created'
			return
		} else {
			project.logger.info "Processing ${artifacts.size()} dependency artifacts:"
		}
		
		project.platform.configurations.createBundles(artifacts.values(), targetDir)
	}
	
	// methods logging information for easier debugging
	
	protected void configInfo(Configuration config, def log) {
		log("Configuration: $config.name")
		
		log('  Dependencies:')
		config.allDependencies.each {
			log("    - $it.group $it.name $it.version")
//			it.properties.each {
//				k, v ->
//				log("    $k: $v")
//			}
		}
		
		log('  Files:')
		config.collect().each {
			log("    - ${it}")
		}
	}
	
	protected void resolvedConfigInfo(String title = 'Resolved configuration', Iterable<ResolvedArtifact> resolvedArtifacts, def log) {
		log(title)
		
		log('  Artifacts:')
		resolvedArtifacts.each {
			log("    ${it.name}:")
			log("      File: $it.file")
			log("      Classifier: $it.classifier")
			log("      Extension: $it.extension")
			log("      Group: $it.moduleVersion.id.group")
			log("      Name: $it.moduleVersion.id.name")
			log("      Version: $it.moduleVersion.id.version")
//			it.properties.each {
//				k, v ->
//				log("      $k: $v")
//			}
		}
	}

}
