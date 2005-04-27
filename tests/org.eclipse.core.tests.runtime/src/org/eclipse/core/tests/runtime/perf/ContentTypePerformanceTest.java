/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tests.runtime.perf;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.internal.content.ContentType;
import org.eclipse.core.internal.content.ContentTypeBuilder;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.core.tests.harness.BundleTestingHelper;
import org.eclipse.core.tests.harness.PerformanceTestRunner;
import org.eclipse.core.tests.runtime.*;
import org.eclipse.core.tests.runtime.content.NaySayerContentDescriber;
import org.eclipse.core.tests.session.PerformanceSessionTestSuite;
import org.eclipse.core.tests.session.SessionTestSuite;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

public class ContentTypePerformanceTest extends RuntimeTest {

	private final static String CONTENT_TYPE_PREF_NODE = Platform.PI_RUNTIME + IPath.SEPARATOR + "content-types"; //$NON-NLS-1$	
	private static final String DEFAULT_NAME = "file_" + ContentTypePerformanceTest.class.getName();
	private static final int ELEMENTS_PER_LEVEL = 2;
	private static final int NUMBER_OF_LEVELS = 10;
	private static final int NUMBER_OF_ELEMENTS = computeTotalTypes(NUMBER_OF_LEVELS, ELEMENTS_PER_LEVEL);

	private static final String TEST_DATA_ID = "org.eclipse.core.tests.runtime.contenttype.perf.testdata";

	private static int computeTotalTypes(int levels, int elementsPerLevel) {
		double sum = 0;
		for (int i = 0; i <= levels; i++)
			sum += Math.pow(elementsPerLevel, i);
		return (int) sum;
	}

	private static String createContentType(String id, String baseTypeId, String[] fileNames, String[] fileExtensions, String describer) {
		StringBuffer result = new StringBuffer();
		result.append("<content-type id=\"");
		result.append(id);
		result.append("\" name=\"");
		result.append(id);
		result.append("\" ");
		if (baseTypeId != null) {
			result.append("base-type=\"");
			result.append(baseTypeId);
			result.append("\" ");
		}
		if (fileNames != null && fileNames.length > 0) {
			result.append("file-names=\"");
			result.append(toListString(fileNames));
			result.append("\" ");
		}
		if (fileExtensions != null && fileExtensions.length > 0) {
			result.append("file-extensions=\"");
			result.append(toListString(fileExtensions));
			result.append("\" ");
		}
		result.append("describer=\"");
		result.append(describer);
		result.append("\"/>");
		return result.toString();
	}

	public static int createContentTypes(Writer writer, String baseTypeId, int created, int numberOfLevels, int nodesPerLevel) throws IOException {
		if (numberOfLevels == 0)
			return 0;
		int local = nodesPerLevel;
		for (int i = 0; i < nodesPerLevel; i++) {
			String id = generateContentType(writer, created + i, baseTypeId);
			local += createContentTypes(writer, id, created + local, numberOfLevels - 1, nodesPerLevel);
		}
		return local;
	}

	private static String generateContentType(Writer writer, int number, String baseTypeId) throws IOException {
		String id = "performance" + number;
		String definition = createContentType(id, baseTypeId, number > 0 ? null : new String[] {DEFAULT_NAME}, null, NaySayerContentDescriber.class.getName());
		writer.write(definition);
		writer.write(System.getProperty("line.separator"));
		return id;
	}

	private static String getContentTypeId(int i) {
		return TEST_DATA_ID + ".performance" + i;
	}

	public static Test suite() {
		TestSuite suite = new TestSuite(ContentTypePerformanceTest.class.getName());

//		suite.addTest(new ContentTypePerformanceTest("testDoSetUp"));
//		suite.addTest(new ContentTypePerformanceTest("testIsKindOf"));
//		suite.addTest(new ContentTypePerformanceTest("testDoTearDown"));

				SessionTestSuite setUp = new SessionTestSuite(PI_RUNTIME_TESTS, "testDoSetUp");
		setUp.addTest(new ContentTypePerformanceTest("testDoSetUp"));
		suite.addTest(setUp);

		TestSuite singleRun = new PerformanceSessionTestSuite(PI_RUNTIME_TESTS, 1, "singleSessionTests");
		singleRun.addTest(new ContentTypePerformanceTest("testContentMatching"));
		singleRun.addTest(new ContentTypePerformanceTest("testContentTXTMatching"));
		singleRun.addTest(new ContentTypePerformanceTest("testContentXMLMatching"));
		singleRun.addTest(new ContentTypePerformanceTest("testNameMatching"));
		singleRun.addTest(new ContentTypePerformanceTest("testIsKindOf"));
		suite.addTest(singleRun);

		TestSuite loadCatalog = new PerformanceSessionTestSuite(PI_RUNTIME_TESTS, 5, "multipleSessionTests");
		loadCatalog.addTest(new ContentTypePerformanceTest("testLoadCatalog"));
		suite.addTest(loadCatalog);

		TestSuite tearDown = new SessionTestSuite(PI_RUNTIME_TESTS, "testDoTearDown");
		tearDown.addTest(new ContentTypePerformanceTest("testDoTearDown"));
		suite.addTest(tearDown);
		return suite;
	}

	static String toListString(Object[] list) {
		if (list.length == 0)
			return ""; //$NON-NLS-1$
		StringBuffer result = new StringBuffer();
		for (int i = 0; i < list.length; i++) {
			result.append(list[i]);
			result.append(',');
		}
		// ignore last comma
		return result.substring(0, result.length() - 1);
	}

	public ContentTypePerformanceTest(String name) {
		super(name);
	}

	private int countTestContentTypes(IContentType[] all) {
		String namespace = TEST_DATA_ID + '.';
		int count = 0;
		for (int i = 0; i < all.length; i++)
			if (all[i].getId().startsWith(namespace))
				count++;
		return count;
	}

	/** Tests how much the size of the catalog affects the performance of content type matching by content analysis and name*/
	public void doTestContentMatching(final String name, final String contents, int outer, int inner) {
		// warm up preference service		
		loadPreferences();
		// warm up content type registry
		final IContentTypeManager manager = loadContentTypeManager();
		loadDescribers();
		new PerformanceTestRunner() {
			protected void test() {
				try {
					manager.findContentTypesFor(getContents(contents), name);
				} catch (IOException e) {
					fail("2.0", e);
				}
			}
		}.run(this, outer, inner);
	}

private Bundle installContentTypes(String tag, int numberOfLevels, int nodesPerLevel) {
		TestRegistryChangeListener listener = new TestRegistryChangeListener(Platform.PI_RUNTIME, ContentTypeBuilder.PT_CONTENTTYPES, null, null);
		listener.register();
		IPath pluginLocation = getRandomLocation();
		pluginLocation.toFile().mkdirs();
		URL installURL = null;
		try {
			installURL = pluginLocation.toFile().toURL();
		} catch (MalformedURLException e) {
			fail(tag + ".0.5", e);
		}
		Writer writer = null;
		Bundle installed = null;
		try {
			try {
				writer = new BufferedWriter(new FileWriter(pluginLocation.append("plugin.xml").toFile()), 0x10000);
				writer.write("<plugin id=\"" + TEST_DATA_ID + "\" name=\"Content Type Performance Test Data\" version=\"1\">");
				writer.write(System.getProperty("line.separator"));
				writer.write("<requires><import plugin=\"" + PI_RUNTIME_TESTS + "\"/></requires>");
				writer.write(System.getProperty("line.separator"));
				writer.write("<extension point=\"org.eclipse.core.runtime.contentTypes\">");
				writer.write(System.getProperty("line.separator"));
				String root = generateContentType(writer, 0, null);
				createContentTypes(writer, root, 1, numberOfLevels, nodesPerLevel);
				writer.write("</extension></plugin>");
			} catch (IOException e) {
				fail(tag + ".1.0", e);
			} finally {
				if (writer != null)
					try {
						writer.close();
					} catch (IOException e) {
						fail("1.1", e);
					}
			}
			try {
				installed = RuntimeTestsPlugin.getContext().installBundle(installURL.toExternalForm());
			} catch (BundleException e) {
				fail(tag + ".3.0", e);
			}
			BundleTestingHelper.refreshPackages(RuntimeTestsPlugin.getContext(), new Bundle[] {installed});
			assertNotNull(tag + ".4.0", listener.getEvent(10000));
		} finally {
			listener.unregister();
		}
		return installed;
	}	/**
	 * Returns a loaded content type manager. Except for load time tests, this method should
	 * be called outside the scope of performance monitoring.
	 */
	private IContentTypeManager loadContentTypeManager() {
		// any cheap interaction that causes the catalog to be built				
		Platform.getContentTypeManager().getContentType(IContentTypeManager.CT_TEXT);
		return Platform.getContentTypeManager();
	}

	/** Forces all describers to be loaded.*/
	private void loadDescribers() {
		final IContentTypeManager manager = Platform.getContentTypeManager();
		IContentType[] allTypes = manager.getAllContentTypes();
		for (int i = 0; i < allTypes.length; i++)
			((ContentType) allTypes[i]).getDescriber();
	}

	private void loadPreferences() {
		new InstanceScope().getNode(CONTENT_TYPE_PREF_NODE);
	}

	/** Tests how much the size of the catalog affects the performance of content type matching by content analysis */
	public void testContentMatching() {
		doTestContentMatching(DEFAULT_NAME, getRandomString(), 10, 1);
	}

	public void testContentTXTMatching() {
		doTestContentMatching(getRandomString(), "foo.txt", 1, 200000);
	}

	public void testContentXMLMatching() {
		doTestContentMatching(getRandomString(), "foo.xml", 1, 100000);
	}

	public void testDoSetUp() {
		installContentTypes("1.0", NUMBER_OF_LEVELS, ELEMENTS_PER_LEVEL);
	}

	public void testDoTearDown() {
		Bundle bundle = Platform.getBundle(TEST_DATA_ID);
		if (bundle == null)
			// there is nothing to clean up (install failed?) 
			fail("1.0 nothing to clean-up");
		try {
			bundle.uninstall();
			ensureDoesNotExistInFileSystem(new File(new URL(bundle.getLocation()).getFile()));
		} catch (MalformedURLException e) {
			fail("2.0", e);
		} catch (BundleException e) {
			fail("3.0", e);
		}
	}

	public void testIsKindOf() {
		// warm up preference service		
		loadPreferences();
		// warm up content type registry
		final IContentTypeManager manager = loadContentTypeManager();
		final IContentType root = manager.getContentType(getContentTypeId(0));
		assertNotNull("2.0", root);
		new PerformanceTestRunner() {
			protected void test() {
				for (int i = 0; i < NUMBER_OF_ELEMENTS; i++) {
					IContentType type = manager.getContentType(getContentTypeId(i));
					assertNotNull("3.0." + i, type);
					assertTrue("3.1." + i, type.isKindOf(root));
				}
			}
		}.run(this, 10, 15);
	}

	/**
	 * This test is intended for running as a session test.
	 */
	public void testLoadCatalog() {
		// warm up preference service		
		loadPreferences();
		new PerformanceTestRunner() {
			protected void test() {
				// any interation that will cause the registry to be loaded
				Platform.getContentTypeManager().getContentType(IContentTypeManager.CT_TEXT);
			}
		}.run(this, 1, /* must run only once - the suite controls how many sessions are run */1);
		// sanity check to make sure we are running with good data		
		assertEquals("missing content types", NUMBER_OF_ELEMENTS, countTestContentTypes(Platform.getContentTypeManager().getAllContentTypes()));
	}

	/** Tests how much the size of the catalog affects the performance of content type matching by name */
	public void testNameMatching() {
		// warm up preference service		
		loadPreferences();
		// warm up content type registry
		final IContentTypeManager manager = loadContentTypeManager();
		loadDescribers();
		new PerformanceTestRunner() {
			protected void test() {
				IContentType[] associated = manager.findContentTypesFor("foo.txt");
				// we know at least the etxt content type should be here
				assertTrue("2.0", associated.length >= 1);
				// and it is supposed to be the first one (since it is at the root)
				assertEquals("2.1", IContentTypeManager.CT_TEXT, associated[0].getId());
			}
		}.run(this, 10, 200000);
	}
}