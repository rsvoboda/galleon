/*
 * Copyright 2016-2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.galleon.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.SingleUniverseTestBase;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.util.StringUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class LayoutTestBase extends SingleUniverseTestBase {

    @Override
    protected void testPm(ProvisioningManager pm) throws ProvisioningException {
        throw new UnsupportedOperationException();
    }

    protected ProvisioningConfig provisioningConfig() throws ProvisioningException {
        return null;
    }

    protected Path featurePackZip() throws ProvisioningException {
        return null;
    }

    protected ProvisioningLayoutFactory getLayoutFactory() throws ProvisioningException {
        return ProvisioningLayoutFactory.getInstance();
    }

    protected ProvisioningLayout<FeaturePackLayout> buildLayout() throws Exception {
        final ProvisioningConfig config = provisioningConfig();
        if(config == null) {
            final Path p = featurePackZip();
            if(p == null) {
                throw new IllegalStateException("Either provisioningConfig() or featurePackZip() have to return a non-null value");
            }
            return getLayoutFactory().newConfigLayout(p, false);
        }
        return getLayoutFactory().newConfigLayout(config);
    }

    protected ProvisioningConfig expectedLayoutConfig() throws ProvisioningDescriptionException {
        return null;
    }

    protected void assertLayoutConfig(ProvisioningConfig layoutConfig) throws Exception {
    }

    protected abstract void assertLayout(ProvisioningLayout<FeaturePackLayout> layout) throws Exception;

    @Test
    public void main() throws Exception {

        try(ProvisioningLayout<FeaturePackLayout> layout = buildLayout()) {
            if(pmErrors() != null) {
                Assert.fail("Errors expected");
            }
            assertLayout(layout);
            final ProvisioningConfig expectedConfig = expectedLayoutConfig();
            if(expectedConfig == null) {
                assertLayoutConfig(layout.getConfig());
            } else {
                assertEquals(expectedConfig, layout.getConfig());
            }
        } catch(ProvisioningException e) {
            final String[] errors = pmErrors();
            if(errors == null) {
                throw e;
            }
            Throwable t = e;
            int i = 0;
            while(t != null) {
                if(i == errors.length) {
                    Assert.fail("There are more error messages than expected");
                }
                assertEquals(errors[i++], t.getMessage());
                t = e.getCause();
            }
        }
    }

    protected void assertOrdering(final FPID[] expected, ProvisioningLayout<FeaturePackLayout> layout) {
        if (expected.length == 0) {
            assertFalse("Layout not empty", layout.hasFeaturePacks());
            return;
        }
        final List<FeaturePackLayout> featurePacks = layout.getOrderedFeaturePacks();
        if (expected.length != featurePacks.size()) {
            fail(expected, featurePacks);
        }
        for (int i = 0; i < expected.length; ++i) {
            if (expected[i].equals(featurePacks.get(i).getFPID())) {
                continue;
            }
            fail(expected, featurePacks);
        }
    }

    private void fail(final FPID[] expected, final List<FeaturePackLayout> featurePacks) {
        final StringBuilder buf = new StringBuilder();
        buf.append("Expected ");
        StringUtils.append(buf, Arrays.asList(expected));
        buf.append(" but was ");
        if(featurePacks.isEmpty()) {
            buf.append("empty");
        } else {
            buf.append(featurePacks.get(0).getFPID());
            for (int j = 1; j < featurePacks.size(); ++j) {
                buf.append(',').append(featurePacks.get(j).getFPID());
            }
        }
        Assert.fail(buf.toString());
    }
}
