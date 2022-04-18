/*******************************************************************************
 * Copyright (c) 2022 Christoph Läubrich and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tests.runtime;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Optional;
import java.util.concurrent.*;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.junit.Test;
import org.osgi.framework.ServiceRegistration;

/**
 *
 */
public class ServiceLookupTest {

	@Test
	public void testLookup() throws InterruptedException, ExecutionException, TimeoutException {
		Optional<IPreferencesService> lookup = RuntimeTestsPlugin.getPlugin().lookup(IPreferencesService.class);
		assertNotNull(lookup);
		assertTrue("no Preferences Service?", lookup.isPresent());
		CompletableFuture<IPreferencesService> future = RuntimeTestsPlugin.getPlugin().track(IPreferencesService.class);
		assertNotNull("Service must be there...", future.get(10, TimeUnit.SECONDS));
		Optional<Not_A_Servcie> lookup2 = RuntimeTestsPlugin.getPlugin().lookup(Not_A_Servcie.class);
		assertNotNull(lookup2);
		assertTrue("Should not be found", lookup2.isEmpty());
	}

	@Test
	public void testTrack() throws InterruptedException, ExecutionException, TimeoutException {
		CompletableFuture<TrackedService> future = RuntimeTestsPlugin.getPlugin().track(TrackedService.class);
		try {
			future.get(1, TimeUnit.SECONDS);
			fail("Should not be completed!");
		} catch (TimeoutException e) {
			// We must timeout here as there is no such service....
		}
		ServiceRegistration<TrackedService> registration = RuntimeTestsPlugin.getContext()
				.registerService(TrackedService.class, new TrackedService() {
				}, null);
		try {
			assertNotNull("Service must be there...", future.get(10, TimeUnit.SECONDS));
		} finally {
			registration.unregister();
		}
	}


	public static interface Not_A_Servcie {

	}

	public static interface TrackedService {

	}
}
