bnd-platform
============

Using OSGi and having trouble to get all the dependencies as proper OSGi bundles?
Even worse, sometimes you need to adapt bundles due to class loading issues or make an annoying dependency optional?

*bnd-platform* can help you solve that problem - it builds OSGi bundles and even Eclipse Update Sites from existing JARs, for instance retrieved from Maven repositories together with transitive dependencies. If needed you can adapt the creation of bundles via general or individual configuration options.
*bnd-platform* is a [Gradle](http://www.gradle.org/) plugin and uses [bnd](http://www.aqute.biz/Bnd/Bnd) to create bundles and [Eclipse](http://www.eclipse.org/) for the creation of p2 repositories.

For a quick start, check out the [sample project on GitHub](https://github.com/stempler/bnd-platform-sample).

**What *bnd-platform* can do:**
* Create bundles for any JARs that can be defined as dependencies using Gradle (e.g. local JARs, JARs from Maven repositories) and their transitive dependencies
* Download dependency sources and create source bundles (with *Eclipse-SourceBundle* manifest header)
* Add *Bundle-License* and *Bundle-Vendor* headers based on information from associated POM files
* Merge multiple JARs/dependencies into one bundle, e.g. where needed due to duplicate packages or classloader issues
* Adapt the configuration for wrapping JARs or adapting existing bundles, e.g. to influence the imported packages
* Create an Eclipse Update Site / p2 repository from the created bundles

**What *bnd-platform* does not (or at least not yet):**
* Automatically associate version numbers to imported packages
* Create bundles or update sites from source projects (though that would probably not be too complicated - contributions welcome!)

Usage
-----

The simplest way to apply the plugin to your Gradle build is using the latest release hosted on Maven Central:

```groovy
buildscript {
	repositories {
		mavenCentral()
	}
	dependencies {
		classpath 'org.standardout:bnd-platform:0.2'
	}
}

apply plugin: 'platform'
```

Alternatives are including the repository content in the **buildSrc** folder as done in the [sample project](https://github.com/stempler/bnd-platform-sample) or by installing the plugin to your local Maven repository using `gradlew install` and adding it as dependency to your build script via `mavenLocal()` repository.

*bnd-platform* has been tested with Gradle 1.11.

### Tasks

The **platform** plugin comes with several Gradle tasks - the following are the main tasks and build upon each other:

* ***bundles*** - create bundles and write them to **build/plugins**
* ***updateSite*** - create a p2 repository from the bundles and write it to **build/updatesite** (default)
* ***updateSiteZip*** - create a ZIP archive from the p2 repository and write it to **build/updatesite.zip** (default) 

In addition, the ***clean*** task deletes all previously created bundles or update site artifacts. Usually you will want to clean the created bundles when building an update site, e.g. `gradle clean updateSite`.

Be aware that for building the p2 repository Eclipse is used. If no path to a local Eclipse installation is configured (see the settings section later on) the plugin will by default download Eclipse Indigo and use it for that purpose.

### Adding dependencies

*bnd-platform* adds a configuration named **platform** to a Gradle build. You can add dependencies to the **platform** configuration and configure them like you would with any other Gradle build - for example:

```groovy
// add Maven Central so the dependency can be resolved
repositories {
	mavenCentral()
}

dependencies {
    // add pegdown as dependency to the platform configuration
    platform 'org.pegdown:pegdown:1.4.2'
}
```

That's it - if you combine the previous code snippet with this one you have your first *bnd-platform* build script. A call `gradle updateSite` would create a p2 repository containing bundles for the [pegdown](https://github.com/sirthias/pegdown) library and its dependencies.

Please see the Gradle documentation on [basic](http://www.gradle.org/docs/current/userguide/artifact_dependencies_tutorial.html) and [advanced](http://www.gradle.org/docs/current/userguide/dependency_management.html) dependency management for more details on the dependency configration and advanced issues like resolving version conflicts or dealing with transitive dependencies.

As an alternative to the conventional dependency declaration you can use the *platform* extension to add a dependency using the **bundle** keyword:

```groovy
platform {
    // add pegdown as dependency to the platform configuration
    bundle 'org.pegdown:pegdown:1.4.2'
}
```

Both notations support adapting the dependency configuration as supported by Gradle, e.g. excluding specific transitive dependencies. However, adapting the OSGi bundle creation is only possible with the second notation, as it supports an additional **bnd** configuration.

### Bundle configuration

The *bnd* library is used as a tool to create OSGi bundles from JARs. *bnd* analyzes the class files in a JAR to determine packages to import and export. *bnd* also allows to configure its behavior through a set of header instructions that resemble the OSGi manifest headers (see the [bnd website](http://www.aqute.biz/Bnd/Format) for a detailed description).

The bundle configuration can be applied when adding a dependency:

```groovy
platform {
    bundle(group: 'net.sf.ehcache', name: 'ehcache-core', version:'2.6.6') {
    	bnd {
			// make hibernate packages optional
			optionalImport 'org.hibernate', 'org.hibernate.*'
		}
	}
}
```

Or independently - just registering the configuration without adding a dependency. The configuration is only applied if the dependency is either added directly or as a transitive dependency at another point in the script.

```groovy
platform {
    bnd(group: 'net.sf.ehcache', name: 'ehcache-core') {
		// make hibernate packages optional
		optionalImport 'org.hibernate', 'org.hibernate.*'
	}
}
```

Note that in the example above the version was omitted - the configuration applies to any dependency matching the group and name, regardless of its version.

The configuration options inside the **bnd** call are sketched below:

```groovy
platform {
    bnd(<DependencyNotation>) {
        // override/set the symbolic name
        symbolicName = <SymbolicNameString>
        // override/set the bundle name
        bundleName = <BundleNameString> 
        // override/set bundle version
        version = <VersionString>
        // generic bnd header instruction
        instruction <Header>, <Instruction>
        // adapt the Import-Package instruction to import the given packages optionally
        optionalImport <Package1>, <Package2>, ...
    }
}
```

### Default configuration

*bnd-platform* will by default leave JARs that are recognized as bundles (meaning they have a *Bundle-SymbolicName* header already defined) as they are, except to eventually added *Bundle-License* and *Bundle-Vendor* headers if not yet present. If an existing bundle is wrapped because a bundle configuration applies to it, the configration from the bundle manifest applies as default configuration.

To other JARs the global default configuration applies, which exports packages with the dependency's version number and imports any identified packages as mandatory import. You can override or extend the global default configuration by adding a **bnd** configuration without giving a dependency notation, for example:

```groovy
platform {
    bnd {
        // make the package junit.framework an optional import
        // for all JARs that were not bundles already
        optionalImport 'junit.framework'
    }
}
```

But be careful what you put into the default configuration - setting the *symbolicName* or *version* here will not be seen as error, but does not make any sense and may lead to unpredicatable behavior (as there can't be two bundles with the same symbolic name and version).

###  Configuration priority

A bundle configuration that is more concrete will always override/extend a more general configuration. A configuration applied to a dependency group takes precedence over the default configuration, while a configuration specified for a combination of group and name in turn takes precedence over a group configuration.
If configurations are defined on the same level, the configuration that is defined later in the script will override/extend a previous one.

Please note that in addition, in the combined configurations for a bundle, all assignments (e.g. `version = '1.0.0'`) take precedence over the method calls like `instruction` and `optionalImport`. This allows for instance to both override the version of a bundle in a concrete configuration and making use of it in a more general configuration:

```groovy
platform {
    bnd(group: 'org.standardout') {
        // packages with version number (uses the version provided further below)
        instruction 'Export-Package', "org.*;version=$version"
    }
    bnd(group: 'org.standardout', name: 'bnd-platform', version: '0.1') {
        // override the version
        version = '0.1.0.RELEASE'
    }
}
```

### Sharing configurations

Even though setting up an extensive platform of OSGi bundles can be done quite fast using *bnd-platform*, in many cases additional configuration is necessary. It comes naturally that it should be possible to reuse and share those configurations.

With Gradle you can use `apply from: 'someScript.gradle'` to include other build scripts. In those you can define dependencies, bnd configuration or remote repositories like you would do in the main build script.

An alternative is using the [gradle-include-plugin](https://github.com/stempler/gradle-include-plugin) which allows you to include specific methods from an external script and thus provide parameters to the include. In context of *bnd-platform* it often makes sense to provide a version number as parameter. See the [sample project](https://github.com/stempler/bnd-platform-sample) for some nice examples, e.g. the [logging](https://github.com/stempler/bnd-platform-sample/blob/master/modules/logging.groovy) or [geotools](https://github.com/stempler/bnd-platform-sample/blob/master/modules/geotools.groovy) platform modules defined there. Using the *include* plugin these modules are applied to the sample build like this:

```groovy
include {
	from('modules/logging.groovy') {
		slf4jAndLogback '1.7.2', '1.0.10' // slf4j and logback with given versions
	}

	from('modules/geotools.groovy') {
		geotools() // include geotools with default modules and version
	}
}
```

We have created a repository on GitHub to collect the platform modules and configurations we use for our projects and you are welcome to fork and contribute: [shared-platform](https://github.com/igd-geo/shared-platform). The repository is designed to be used with the [gradle-include-plugin](https://github.com/stempler/gradle-include-plugin) and to enable the sharing of configurations without imposing them on you - just include the configuration that makes sense for you and augment it with your own.

### Local dependencies

You can easily add local JARs to the platform. **If the JAR is not an OSGi bundle yet**, you have add it on its own and at least provide **symbolicName** and **version**:

```groovy
platform {
	bundle file('someLibrary.jar'), {
		bnd {
			version = '1.0.0' // mandatory
			symbolicName = 'com.example.library.some' // mandatory
			bundleName = 'Some Library'
			instruction 'Export-Package', "com.example.library.some.*;version=$version"
		}
	}

	// depends on groovy
	bundle 'org.codehaus.groovy:groovy:1.8.5'
} 
```

As in the example above, you should make sure to add additional dependencies that might be needed by the JAR. Please note that for a JAR `filename.jar` sources provided in a `filename-sources.jar` will be wrapped automatically in a corresponding source bundle.

**JARs that are already OSGi bundles** you can include en masse, and without the need for additional configuration, for example:

```groovy
platform {
	// all bundles in a directory
	bundle fileTree(dir: 'lib') {
		include '*.jar'
	}
	
	// specific bundles
	bundle files('someBundle.jar', 'someOtherBundle.jar')
}
```


### Merged bundles

Sometimes it is necessary to create a bundle out of multiple JARs, most often due to class loading issues. The [Geotools](http://www.geotools.org/) library is a famous example of that. It uses [Java SPI](http://docs.oracle.com/javase/tutorial/sound/SPI-intro.html) as extension mechanism, which does only recognize extensions from the same class loader. One way [suggested to cope with this](http://docs.geotools.org/stable/userguide/welcome/integration.html#osgi) is creating a monolithic bundle that includes all the needed geotools modules (which I prefer because with Geotools otherwise you have different bundles partly exporting the same packages). Doing this with *bnd-platform* is quite easy:

```groovy
platform {
	def geotoolsVersion = '10.4'

	// define the merged bundle
	merge {
		// the match closure is applied to all dependencies/artifacts encountered
		// if true, an artifact is included in the bundle
		match {
			// merge all artifacts in org.geotools group, but not gt-opengis
			it.group == 'org.geotools' && it.name != 'gt-opengis'
		}

		bnd {
			symbolicName = 'org.geotools'
			bundleName = 'Geotools'
			version = geotoolsVersion
			instruction 'Export-Package', "org.geotools.*;version=$version"
			instruction 'Private-Package', '*'
		}
	}
	
	// add geotools modules as dependencies
	bundle "org.geotools:gt-shapefile:$geotoolsVersion"
	// etc.
}
```

Providing the **symbolicName** and **version** as part of the *bnd* configuration is mandatory for merged bundles, as the information from the original JARs' manifests is discarded.
Please note that the example above misses the Maven repositories needed to actually retrieve those artifacts, see the sample project for a [more complete example](https://github.com/stempler/bnd-platform-sample/blob/master/modules/geotools.groovy).

If you use `match { ... }` to merge bundles, it is called for each artifact. The artifact can be accessed via the variable **it**. An artifact has the following properties that can be useful to check against in a *match*:

* **group** - the group name of the artifact, e.g. *'org.geotools'*
* **name** - the name (artifact ID) of the artifact, e.g. *'gt-shapefile'*
* **version** - the version of the artifact
* **file** - the local or downloaded file of the artifact, as File object
 
As alternative to **match** or in combination with it you can add bundles to merge via **bundle** or **include**. The syntax is the same as when adding dependencies. However, using **include** you just specify an artifact to be included if it is a dependency defined somewhere else, it does not add it as dependency.

```groovy
platform {
	merge {
		bundle 'someGroup:someArtifact:1.0.0' // also added as dependency
		include group: 'someGroup', name: 'someOtherArtifact' // not added as dependency
		
		bnd {
			...
		}
	}
}

```

#### Merge settings

You can supply parameters to **merge**, currently those are:

* **failOnDuplicate** - fail if the same file occurs in more than one JAR (not taking into account the manifest)  (default: **true**)
* **collectServices** - combines files in `META-INF/services` defining extensions via SPI (default: **true**)

You can specify them as named parameters, e.g.:

```groovy
platform {
	merge(failOnDuplicate: false, collectServices: true) {
		...
	}
}
```

Plugin settings
---------------

Via the platform extension there are several settings you can provide:

* **fetchSources** - if sources for external dependencies should be fetched and source bundles created (default: **true**)
* **updateSiteDir** - the directory the generated p2 repository is written to (default: `new File(buildDir, 'updatesite')`)
* **updateSiteZipFile** - the target file for the zipped p2 repository (default: `new File(buildDir, 'updatesite.zip')`)
* **eclipseHome** - File object pointing to the directory of a local Eclipse installation to be used for generating the p2 repository (default: `null`)
* **eclipseMirror** - Eclipse download URLs to be used when no local installation is provided via *eclipseHome*, the URLs are identified per osgiOS (win32, linux, macosx), osgiWS (win32, gtk, cocoa) and arch (x86, x86_64), e.g. `eclipseMirror.win32.win32.x86_64 = ...` (defaults to official Eclipse mirrors with Eclipse Indigo)
* **featureId** - the identifier of the feature including the platform bundles that will be available in the created update site (default: **'platform.feature'**)
* **featureName** - the name of the feature including the platform bundles that will be available in the created update site (default: **'Generated platform feature'**)
* **featureVersion** - the version number for the feature including the platform bundles that will be available in the created update site (defaults to the project version)
* **categoryId** - the identifier of the feature's category (default: **'platform'**)
* **categoryName** - the name of the feature's category (default: **'Target platform'**)

For example:

```groovy
platform {
	fetchSources = false
	featureVersion = '3.1.0'
	eclipseHome = new File('/opt/eclipse')
	eclipseMirror.linux.gtk.x86_64 = 'http://myeclipsedownload.com/eclipse.tar.gz'
}
```

License
-------

This software is licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
