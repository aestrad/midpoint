/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.schema.processor;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.internals.InternalCounters;
import com.evolveum.midpoint.schema.internals.InternalMonitor;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LayerType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.w3c.dom.Element;

import static com.evolveum.midpoint.util.MiscUtil.stateCheck;

public class ResourceSchemaFactory {

    private static final String USER_DATA_KEY_PARSED_RESOURCE_SCHEMA = ResourceSchema.class.getName()+".parsedResourceSchema";
    private static final String USER_DATA_KEY_REFINED_SCHEMA = ResourceSchema.class.getName()+".refinedSchema";

    public static ResourceSchema getCompleteSchema(@NotNull ResourceType resource)
            throws SchemaException, ConfigurationException {
        return getCompleteSchema(resource.asPrismObject());
    }

    /**
     * We assume that missing schema is a configuration (not schema) problem.
     */
    public static @NotNull ResourceSchema getCompleteSchemaRequired(@NotNull ResourceType resource)
            throws ConfigurationException, SchemaException {
        return requireSchemaPresent(
                getCompleteSchema(resource),
                resource);
    }

    private static @NotNull ResourceSchema requireSchemaPresent(ResourceSchema schema, @NotNull ResourceType resource)
            throws ConfigurationException {
        return MiscUtil.requireNonNull(
                schema,
                () -> new ConfigurationException("No schema in " + resource + ". A configuration problem?"));
    }

    public static ResourceSchema getCompleteSchema(ResourceType resourceType, LayerType layer)
            throws SchemaException, ConfigurationException {
        return getCompleteSchema(resourceType.asPrismObject(), layer);
    }

    public static @NotNull ResourceSchema getCompleteSchemaRequired(PrismObject<ResourceType> resource, LayerType layer)
            throws SchemaException, ConfigurationException {
        return getCompleteSchemaRequired(resource.asObjectable(), layer);
    }

    public static @NotNull ResourceSchema getCompleteSchemaRequired(ResourceType resource, LayerType layer)
            throws SchemaException, ConfigurationException {
        return requireSchemaPresent(
                getCompleteSchema(resource.asPrismObject(), layer),
                resource);
    }

    /**
     * Obtains refined schema for the resource.
     *
     * Returns null if the resource does not contain any (raw) schema.
     *
     * If the resource does NOT contain the schema, it must be mutable.
     */
    public static ResourceSchema getCompleteSchema(@NotNull PrismObject<ResourceType> resource)
            throws SchemaException, ConfigurationException {
        Preconditions.checkNotNull(resource, "Resource must not be null");

        ResourceSchema existingRefinedSchema = getExistingRefinedSchema(resource);
        if (existingRefinedSchema != null) {
            return existingRefinedSchema;
        } else {
            stateCheck(!resource.isImmutable(), "Trying to setup parsed schema on immutable resource: %s", resource);
            ResourceSchema completeSchema = parseCompleteSchema(resource.asObjectable());
            resource.setUserData(USER_DATA_KEY_REFINED_SCHEMA, completeSchema);
            if (completeSchema != null) {
                completeSchema.freeze();
            }
            return completeSchema;
        }
    }

    private static ResourceSchema getExistingRefinedSchema(PrismObject<ResourceType> resource) {
        Object userDataEntry = resource.getUserData(USER_DATA_KEY_REFINED_SCHEMA);
        if (userDataEntry != null) {
            if (userDataEntry instanceof ResourceSchema) {
                return (ResourceSchema) userDataEntry;
            } else {
                throw new IllegalStateException("Expected ResourceSchema under user data key " + USER_DATA_KEY_REFINED_SCHEMA +
                        "in " + resource + ", but got " + userDataEntry.getClass());
            }
        } else {
            return null;
        }
    }

    public static ResourceSchema getCompleteSchema(PrismObject<ResourceType> resource, LayerType layer)
            throws SchemaException, ConfigurationException {
        ResourceSchema schema = getCompleteSchema(resource);
        if (schema != null) {
            return schema.forLayer(layer);
        } else {
            return null;
        }
    }

    public static ResourceSchema getRawSchema(@NotNull ResourceType resource) throws SchemaException {
        return getRawSchema(
                resource.asPrismObject());
    }

    public static ResourceSchema getRawSchemaRequired(ResourceType resource) throws SchemaException, ConfigurationException {
        return requireSchemaPresent(
                getRawSchema(resource),
                resource);
    }

    /**
     * Obtains "raw" schema for the resource, i.e. the one without `schemaHandling` and similar configuration.
     *
     * If the resource does NOT contain the schema, it must be mutable.
     */
    public static ResourceSchema getRawSchema(@NotNull PrismObject<ResourceType> resource) throws SchemaException {
        Element resourceXsdSchema = ResourceTypeUtil.getResourceXsdSchema(resource);
        if (resourceXsdSchema == null) {
            return null;
        }

        // Synchronization here is a workaround for MID-5648. We need to synchronize parsing here because of DOM access even
        // before DomToSchemaProcessor (where global synchronization is done) comes into play.
        //
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (resourceXsdSchema) {
            Object userDataEntry = resource.getUserData(USER_DATA_KEY_PARSED_RESOURCE_SCHEMA);
            if (userDataEntry != null) {
                if (userDataEntry instanceof ResourceSchema) {
                    return (ResourceSchema) userDataEntry;
                } else {
                    throw new IllegalStateException("Expected ResourceSchema under user data key " +
                            USER_DATA_KEY_PARSED_RESOURCE_SCHEMA + "in " + resource + ", but got " + userDataEntry.getClass());
                }
            } else {
                stateCheck(!resource.isImmutable(), "Trying to set parsed schema on immutable resource: %s", resource);
                InternalMonitor.recordCount(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT);
                ResourceSchema parsedSchema =
                        ResourceSchemaParser.parse(resourceXsdSchema, "resource schema of " + resource);
                resource.setUserData(USER_DATA_KEY_PARSED_RESOURCE_SCHEMA, parsedSchema);
                return parsedSchema;
            }
        }
    }

    @VisibleForTesting
    public static boolean hasParsedSchema(ResourceType resourceType) {
        PrismObject<ResourceType> resource = resourceType.asPrismObject();
        return resource.getUserData(USER_DATA_KEY_PARSED_RESOURCE_SCHEMA) != null;
    }

    /**
     * Executes the real parsing. Returns complete schema (raw + refined).
     *
     * Normally internal to this class, but may be called externally from the test code.
     */
    @VisibleForTesting
    public static ResourceSchema parseCompleteSchema(ResourceType resource) throws SchemaException, ConfigurationException {
        var rawResourceSchema = ResourceSchemaFactory.getRawSchema(resource);
        if (rawResourceSchema != null) {
            return new RefinedResourceSchemaParser(resource, rawResourceSchema)
                    .parse();
        } else {
            return null;
        }
    }
}
