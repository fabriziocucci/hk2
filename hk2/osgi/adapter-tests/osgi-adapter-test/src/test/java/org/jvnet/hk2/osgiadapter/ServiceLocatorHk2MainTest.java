package org.jvnet.hk2.osgiadapter;


import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.CoreOptions.systemPackage;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.cleanCaches;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.logProfile;

import java.io.File;
import java.util.List;

import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.Descriptor;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

import com.sun.enterprise.module.ModulesRegistry;
import com.sun.enterprise.module.bootstrap.Main;
import com.sun.enterprise.module.bootstrap.ModuleStartup;
import com.sun.enterprise.module.bootstrap.StartupContext;

@RunWith(org.ops4j.pax.exam.junit.JUnit4TestRunner.class)
public class ServiceLocatorHk2MainTest {

	private static final String GROUP_ID = "org.glassfish.hk2";
	private static final String EXT_GROUP_ID = "org.glassfish.hk2.external";

	@org.ops4j.pax.exam.Inject
	BundleContext bundleContext;

	static File cacheDir;
	static File testFile;

	static final String text = "# generated on 2 Apr 2012 18:04:09 GMT\n"
			+ "class=com.sun.enterprise.admin.cli.optional.RestoreDomainCommand,index=com.sun.enterprise.admin.cli.CLICommand:restore-domain\n"
			+ "class=com.sun.enterprise.admin.cli.optional.ListBackupsCommand,index=com.sun.enterprise.admin.cli.CLICommand:list-backups\n";

	@Configuration
	public static Option[] configuration() {
		String projectVersion = System.getProperty("project.version");
		return options(
				felix(),
				systemPackage("sun.misc"),
				provision(mavenBundle().groupId(GROUP_ID).artifactId("hk2-api").version(projectVersion).startLevel(4)),
				provision(mavenBundle().groupId(GROUP_ID).artifactId("hk2-utils").version(projectVersion).startLevel(4)), 
				provision(mavenBundle().groupId(GROUP_ID).artifactId("hk2-deprecated").version(projectVersion).startLevel(4)),
				provision(mavenBundle().groupId(GROUP_ID).artifactId("hk2-runlevel").version(projectVersion).startLevel(4)),
				provision(mavenBundle().groupId(GROUP_ID).artifactId("core").version(projectVersion).startLevel(4)),
				provision(mavenBundle().groupId(GROUP_ID).artifactId("hk2-config").version(projectVersion).startLevel(4)),
				provision(mavenBundle().groupId(GROUP_ID).artifactId(
						"hk2-locator").version(projectVersion).startLevel(4)),
				provision(mavenBundle().groupId(EXT_GROUP_ID).artifactId(
						"javax.inject").version(projectVersion).startLevel(4)),
 				provision(mavenBundle().groupId(EXT_GROUP_ID).artifactId(
                                                "bean-validator").version(projectVersion).startLevel(4)),
				provision(mavenBundle().groupId(EXT_GROUP_ID).artifactId(
						"cglib").version(projectVersion).startLevel(4)),
				provision(mavenBundle().groupId(EXT_GROUP_ID).artifactId(
						"asm-all-repackaged").version(projectVersion).startLevel(4)),
				provision(mavenBundle().groupId(GROUP_ID)
						.artifactId("osgi-resource-locator").version("1.0.1").startLevel(4)),
				provision(mavenBundle().groupId(GROUP_ID).artifactId(
						"class-model").version(projectVersion).startLevel(4)),
				provision(mavenBundle().groupId(GROUP_ID).artifactId(
						"osgi-adapter").version(projectVersion).startLevel(1)),
				provision(mavenBundle().groupId(GROUP_ID).artifactId(
						"test-module-startup").version(projectVersion).startLevel(4)),

				systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level")
						.value("DEBUG"), logProfile(), cleanCaches()
		// systemProperty("com.sun.enterprise.hk2.repositories").value(cacheDir.toURI().toString()),
		// vmOption(
		// "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005" )
		);
	}

	@Test
	public <d> void testHK2Main() throws Throwable {

		try {
			Assert.assertNotNull("OSGi did not properly boot", this.bundleContext);

			final StartupContext startupContext = new StartupContext();
			final ServiceTracker hk2Tracker = new ServiceTracker(
					this.bundleContext, Main.class.getName(), null);
			hk2Tracker.open();
			final Main main = (Main) hk2Tracker.waitForService(0);

			// Expect correct subclass of Main to be registered as OSGi service
			Assert.assertEquals("org.jvnet.hk2.osgiadapter.HK2Main", main.getClass()
					.getCanonicalName());
			hk2Tracker.close();
			final ModulesRegistry mr = ModulesRegistry.class.cast(bundleContext
					.getService(bundleContext
							.getServiceReference(ModulesRegistry.class
									.getName())));

			Assert.assertEquals("org.jvnet.hk2.osgiadapter.OSGiModulesRegistryImpl",
					mr.getClass().getCanonicalName());

			final ServiceLocator serviceLocator = main.createServiceLocator(
					startupContext);

			ModulesRegistry mrFromServiceLocator = serviceLocator
					.getService(ModulesRegistry.class);
			Assert.assertEquals(mr, mrFromServiceLocator);

			// serviceLocator should have been registered as an OSGi service
			checkServiceLocatorOSGiRegistration(serviceLocator);

			// check osgi services got registered
			List<?> startLevelServices = serviceLocator
					.getAllServices(BuilderHelper
							.createContractFilter("org.osgi.service.startlevel.StartLevel"));

    		Assert.assertEquals(1, startLevelServices.size());
			
			List<?> startups = serviceLocator.getAllServices(BuilderHelper
					.createContractFilter(ModuleStartup.class
							.getCanonicalName()));
			Assert.assertEquals("Cannot find ModuleStartup", 1, startups.size());

			final ModuleStartup moduleStartup = main.findStartupService(null, startupContext);

			Assert.assertNotNull(
					"Expected a ModuleStartup that was provisioned as part of this test",
					moduleStartup);

			moduleStartup.start();
	
			List<?> configAdmin = serviceLocator.getAllServices(BuilderHelper.createContractFilter("org.osgi.service.cm.ConfigurationAdmin"));
	
			Assert.assertEquals(1, configAdmin.size());
					
		} catch (Exception ex) {
			if (ex.getCause() != null)
				throw ex.getCause();

			throw ex;
		}
	}

	private void checkServiceLocatorOSGiRegistration(
			final ServiceLocator serviceLocator) {
		ServiceReference serviceLocatorRef = bundleContext
				.getServiceReference(ServiceLocator.class.getName());

		ServiceLocator serviceLocatorFromOSGi = (ServiceLocator) bundleContext
				.getService(serviceLocatorRef);

		Assert.assertNotNull("Expected ServiceLocator to be registed in OSGi",
				serviceLocatorFromOSGi);
		Assert.assertEquals(
				"Expected same ServiceLocator in OSGi as the one passed in",
				serviceLocator, serviceLocatorFromOSGi);
	}

}
