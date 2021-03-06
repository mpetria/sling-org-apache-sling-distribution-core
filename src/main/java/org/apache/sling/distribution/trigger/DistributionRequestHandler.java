/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.distribution.trigger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import aQute.bnd.annotation.ConsumerType;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.distribution.DistributionRequest;
import org.apache.sling.distribution.component.impl.DistributionComponentKind;

/**
 * An handler for {@link org.apache.sling.distribution.DistributionRequest}s passed to a
 * {@link DistributionTrigger}
 */
@ConsumerType
public interface DistributionRequestHandler {

    /**
     * returns the name of the owner of this request handler.
     * @return
     */
    @Nonnull
    String getName();

    /**
     * returns the kind of component that owns this request handler. Might be null, for unknown kinds of components.
     * @return
     */
    @CheckForNull
    DistributionComponentKind getComponentKind();

    /**
     * handle the request according to the trigger implementation.
     *
     * @param request a distribution request
     */
    void handle(@Nullable ResourceResolver resourceResolver, @Nonnull DistributionRequest request);

}
