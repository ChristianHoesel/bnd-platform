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

package org.standardout.gradle.plugin.platform.internal.config

import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import aQute.bnd.osgi.Constants

/**
 * Represents the configuration of a bundle concerning bnd.
 */
class BndConfig {
	
	/**
	 * Constructor.
	 * 
	 * @param project the gradle project
	 */
	BndConfig(Project project, String group, String name, String version, File file) {
		this.project = project
		
		this.group = group
		this.name = name
		this.version = version
		this.file = file
	}
	
	final Project project
	
	final String group
	
	final String name
	
	final File file
	
	/**
	 * Version that is either provided or can be set for file dependencies.
	 */
	void setVersion(String version) {
		properties[Constants.BUNDLE_VERSION] = version
	}
	def getVersion() {
		properties[Constants.BUNDLE_VERSION]
	}
	
	/**
	 * Custom symbolic name.
	 */
	void setSymbolicName(String symbolicName) {
		properties[Constants.BUNDLE_SYMBOLICNAME] = symbolicName
	}
	def getSymbolicName() {
		properties[Constants.BUNDLE_SYMBOLICNAME]
	}
	
	/**
	 * Custom bundle name.
	 */
	void setBundleName(String bundleName) {
		properties[Constants.BUNDLE_NAME] = bundleName
	}
	def getBundleName() {
		properties[Constants.BUNDLE_NAME]
	}
	
	/**
	 * Map of bnd instruction names to instructions.
	 */
	final Map<String, String> properties = [:]
	
	/**
	 * Create a bnd instruction.
	 */
	def instruction(String name, def value) {
		properties[name] = (value as String).trim()
		this
	}
	
	/**
	 * Add packages for optional import.
	 */
	def optionalImport(String... packages) {
		def list = packages as List
		list = list.collect { it + ';resolution:=optional' }
		instruction 'Import-Package', list.join(',') + ',' + (properties['Import-Package']?:'*')
	}
	
}
