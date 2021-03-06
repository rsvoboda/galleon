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
package org.jboss.galleon.pkg.optional.test;

import org.jboss.galleon.Constants;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningOption;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeatureConfig;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.creator.FeaturePackCreator;
import org.jboss.galleon.runtime.ResolvedFeatureId;
import org.jboss.galleon.spec.FeatureParameterSpec;
import org.jboss.galleon.spec.FeatureSpec;
import org.jboss.galleon.spec.PackageDependencySpec;
import org.jboss.galleon.state.ProvisionedFeaturePack;
import org.jboss.galleon.state.ProvisionedState;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.MvnUniverse;
import org.jboss.galleon.universe.ProvisionFromUniverseTestBase;
import org.jboss.galleon.xml.ProvisionedConfigBuilder;
import org.jboss.galleon.xml.ProvisionedFeatureBuilder;

/**
 *
 * @author Alexey Loubyansky
 */
public class OptionalPackagesNoneBasicTestCase extends ProvisionFromUniverseTestBase {

    private FeaturePackLocation prod1;

    @Override
    protected void createProducers(MvnUniverse universe) throws ProvisioningException {
        universe.createProducer("prod1");
    }

    @Override
    protected void createFeaturePacks(FeaturePackCreator creator) throws ProvisioningException {

        prod1 = newFpl("prod1", "1", "1.0.0.Final");

        creator.newFeaturePack()
            .setFPID(prod1.getFPID())
            .addFeatureSpec(FeatureSpec.builder("specA")
                    .addParam(FeatureParameterSpec.createId("id"))
                    .addPackageDep(PackageDependencySpec.required("p9"))
                    .addPackageDep(PackageDependencySpec.optional("p11"))
                    .build())
            .newPackage("p1", true)
                .addDependency(PackageDependencySpec.optional("p2"))
                .addDependency(PackageDependencySpec.passive("p5"))
                .addDependency(PackageDependencySpec.optional("p8"))
                .getFeaturePack()
            .newPackage("p2")
                .addDependency("p3")
                .addDependency("p4")
                .getFeaturePack()
            .newPackage("p3")
                .getFeaturePack()
            .newPackage("p4")
                .getFeaturePack()
            .newPackage("p5")
                .addDependency("p6")
                .addDependency("p7")
                .getFeaturePack()
            .newPackage("p6")
                .getFeaturePack()
            .newPackage("p7")
                .getFeaturePack()
            .newPackage("p8")
                .getFeaturePack()
            .newPackage("p9")
                .addDependency("p10", true)
                .getFeaturePack()
            .newPackage("p10")
                .getFeaturePack()
            .newPackage("p11");

    }

    @Override
    protected ProvisioningConfig provisioningConfig() throws ProvisioningException {
        return ProvisioningConfig.builder()
                .addOption(ProvisioningOption.OPTIONAL_PACKAGES.getName(), Constants.NONE)
                .addFeaturePackDep(FeaturePackConfig.builder(prod1)
                        .includePackage("p3")
                        .includePackage("p4")
                        .includePackage("p6")
                        .includePackage("p7")
                        //.excludePackage("p9")
                        .build())
                .addConfig(ConfigModel.builder("model1", "name1")
                        .addFeature(new FeatureConfig("specA").setParam("id", "1"))
                        .build())
                .build();
    }

    @Override
    protected ProvisionedState provisionedState() throws ProvisioningException {
        return ProvisionedState.builder()
                .addFeaturePack(ProvisionedFeaturePack.builder(prod1.getFPID())
                        .addPackage("p1")
                        .addPackage("p3")
                        .addPackage("p4")
                        .addPackage("p6")
                        .addPackage("p7")
                        .addPackage("p9")
                        .build())
                .addConfig(ProvisionedConfigBuilder.builder()
                        .setModel("model1")
                        .setName("name1")
                        .addFeature(ProvisionedFeatureBuilder.builder(ResolvedFeatureId.create(prod1.getProducer(), "specA", "id", "1")))
                        .build())
                .build();
    }
}