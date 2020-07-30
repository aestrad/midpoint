/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.impl.lens.projector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ChangeTypeType;
import com.evolveum.prism.xml.ns._public.types_3.ObjectDeltaType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.api.context.SynchronizationPolicyDecision;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.model.impl.lens.LensProjectionContext;
import com.evolveum.midpoint.model.impl.lens.LensUtil;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.schema.ResourceShadowDiscriminator;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

/**
 * @author Radovan Semancik
 *
 */
@Component
public class DependencyProcessor {

    private static final Trace LOGGER = TraceManager.getTrace(DependencyProcessor.class);

    private static final String OP_SORT_PROJECTIONS_TO_WAVES = DependencyProcessor.class.getName() + ".sortProjectionsToWaves";

    @Autowired private ProvisioningService provisioningService;
    @Autowired private TaskManager taskManager;

    public <F extends ObjectType> void sortProjectionsToWaves(LensContext<F> context, OperationResult parentResult)
            throws PolicyViolationException {
        OperationResult result = parentResult.createMinorSubresult(OP_SORT_PROJECTIONS_TO_WAVES);
        try {

            // Create a snapshot of the projection collection at the beginning of computation.
            // The collection may be changed during computation (projections may be added). We do not want to process
            // these added projections. They are already processed inside the computation.
            // This also avoids ConcurrentModificationException
            LensProjectionContext[] projectionArray = context.getProjectionContexts().toArray(new LensProjectionContext[0]);

            // Reset incomplete flag for those contexts that are not yet computed
            for (LensProjectionContext projectionContext : context.getProjectionContexts()) {
                if (projectionContext.getWave() < 0) {
                    projectionContext.setWaveIncomplete(true);
                }
            }

            for (LensProjectionContext projectionContext : projectionArray) {
                determineProjectionWave(context, projectionContext, null, null);
                projectionContext.setWaveIncomplete(false);
            }

            if (LOGGER.isTraceEnabled()) {
                StringBuilder sb = new StringBuilder();
                for (LensProjectionContext projectionContext : context.getProjectionContexts()) {
                    sb.append("\n");
                    sb.append(projectionContext.getResourceShadowDiscriminator());
                    sb.append(": ");
                    sb.append(projectionContext.getWave());
                }
                LOGGER.trace("Projections sorted to waves (projection wave {}, execution wave {}):{}",
                        context.getProjectionWave(), context.getExecutionWave(), sb.toString());
            }
        } catch (Throwable t) {
            result.recordFatalError(t);
            throw t;
        } finally {
            result.computeStatusIfUnknown();
        }
    }

    private <F extends ObjectType> LensProjectionContext determineProjectionWave(LensContext<F> context,
            LensProjectionContext projectionContext, ResourceObjectTypeDependencyType inDependency, List<ResourceObjectTypeDependencyType> depPath) throws PolicyViolationException {
        if (!projectionContext.isWaveIncomplete()) {
            // This was already processed
            return projectionContext;
        }
        if (projectionContext.isDelete()) {
            // When deprovisioning (deleting) the dependencies needs to be processed in reverse
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Determining wave for (deprovision): {}", projectionContext.getHumanReadableName());
            }
            return determineProjectionWaveDeprovision(context, projectionContext, inDependency, depPath);
        } else {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Determining wave for (provision): {}", projectionContext.getHumanReadableName());
            }
            return determineProjectionWaveProvision(context, projectionContext, inDependency, depPath);
        }
    }

    private <F extends ObjectType> LensProjectionContext determineProjectionWaveProvision(LensContext<F> context,
            LensProjectionContext projectionContext, ResourceObjectTypeDependencyType inDependency, List<ResourceObjectTypeDependencyType> depPath) throws PolicyViolationException {
        if (depPath == null) {
            depPath = new ArrayList<>();
        }
        int determinedWave = 0;
        int determinedOrder = 0;
        for (ResourceObjectTypeDependencyType outDependency: projectionContext.getDependencies()) {
            if (inDependency != null && isHigerOrder(outDependency, inDependency)) {
                // There is incomming dependency. Deal only with dependencies of this order and lower
                // otherwise we can end up in endless loop even for legal dependencies.
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("  processing dependency: {}: ignore (higher order)", PrettyPrinter.prettyPrint(outDependency));
                }
                continue;
            }
            checkForCircular(depPath, outDependency, projectionContext);
            depPath.add(outDependency);
            ResourceShadowDiscriminator refDiscr = new ResourceShadowDiscriminator(outDependency,
                    projectionContext.getResource().getOid(), projectionContext.getKind());
            LensProjectionContext dependencyProjectionContext = findDependencyTargetContext(context, projectionContext, outDependency);
            if (dependencyProjectionContext == null || dependencyProjectionContext.isDelete()) {
                ResourceObjectTypeDependencyStrictnessType outDependencyStrictness = ResourceTypeUtil.getDependencyStrictness(outDependency);
                String nameRefResource = getResourceNameFromRef(refDiscr);
                String refResourceMessage = "";
                if (nameRefResource != null) {
                    refResourceMessage = " resource "+nameRefResource+"(oid:"+refDiscr.getResourceOid()+")";
                }
                String projectionResourceMessage = " resource "+projectionContext.getResourceName()+"(oid:"+projectionContext.getResourceOid()+")";
                if (outDependencyStrictness == ResourceObjectTypeDependencyStrictnessType.STRICT) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("  processing dependency: {}: unsatisfied strict dependency", PrettyPrinter.prettyPrint(outDependency));
                    }
                    throw new PolicyViolationException("Unsatisfied strict dependency of account ["+projectionContext.getResourceShadowDiscriminator().toHumanReadableDescription(false)+
                            projectionResourceMessage+"] dependent on ["+refDiscr.toHumanReadableDescription(nameRefResource == null)+refResourceMessage+"]: Account not provisioned");
                } else if (outDependencyStrictness == ResourceObjectTypeDependencyStrictnessType.LAX) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("  processing dependency: {}: unsatisfied lax dependency", PrettyPrinter.prettyPrint(outDependency));
                    }
                    // independent object not in the context, just ignore it
                    LOGGER.debug("Unsatisfied lax dependency of account ["+projectionContext.getResourceShadowDiscriminator().toHumanReadableDescription(false)
                            +projectionResourceMessage+"] dependent on ["+refDiscr.toHumanReadableDescription(nameRefResource == null)+refResourceMessage+"]: dependency skipped");
                } else if (outDependencyStrictness == ResourceObjectTypeDependencyStrictnessType.RELAXED) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("  processing dependency: {}: unsatisfied relaxed dependency", PrettyPrinter.prettyPrint(outDependency));
                    }
                    // independent object not in the context, just ignore it
                    LOGGER.debug("Unsatisfied relaxed dependency of account ["+projectionContext.getResourceShadowDiscriminator().toHumanReadableDescription(false)+
                            projectionResourceMessage+"] dependent on ["+refDiscr.toHumanReadableDescription(nameRefResource == null)+refResourceMessage+"]: dependency skipped");
                } else {
                    throw new IllegalArgumentException("Unknown dependency strictness "+outDependency.getStrictness()+" in "+refDiscr);
                }
            } else {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("  processing dependency: {}: satisfied dependency", PrettyPrinter.prettyPrint(outDependency));
                }
                dependencyProjectionContext = determineProjectionWave(context, dependencyProjectionContext, outDependency, depPath);
                LOGGER.trace("    dependency projection wave: {}", dependencyProjectionContext.getWave());
                if (dependencyProjectionContext.getWave() + 1 > determinedWave) {
                    determinedWave = dependencyProjectionContext.getWave() + 1;
                    if (outDependency.getOrder() == null) {
                        determinedOrder = 0;
                    } else {
                        determinedOrder = outDependency.getOrder();
                    }
                }
                LOGGER.trace("    determined dependency wave: {} (order={})", determinedWave, determinedOrder);
            }
            depPath.remove(outDependency);
        }
        LensProjectionContext resultAccountContext = projectionContext;
        if (projectionContext.getWave() >=0 && projectionContext.getWave() != determinedWave) {
            // Wave for this context was set during the run of this method (it was not set when we
            // started, we checked at the beginning). Therefore this context must have been visited again.
            // therefore there is a circular dependency. Therefore we need to create another context to split it.
            ResourceShadowDiscriminator origDiscr = projectionContext.getResourceShadowDiscriminator();
            ResourceShadowDiscriminator discr = new ResourceShadowDiscriminator(origDiscr.getResourceOid(), origDiscr.getKind(), origDiscr.getIntent(), origDiscr.getTag(), origDiscr.isTombstone());
            discr.setOrder(determinedOrder);
            if (!projectionContext.compareResourceShadowDiscriminator(discr, true)){
                resultAccountContext = createAnotherContext(context, projectionContext, discr);
            }
        }
//        LOGGER.trace("Wave for {}: {}", resultAccountContext.getResourceAccountType(), wave);
        resultAccountContext.setWave(determinedWave);
        return resultAccountContext;
    }

    private String getResourceNameFromRef(ResourceShadowDiscriminator refDiscr) {
        try {
            Task task = taskManager.createTaskInstance("Load resource");
            GetOperationOptions rootOpts = GetOperationOptions.createNoFetch();
            Collection<SelectorOptions<GetOperationOptions>> options = SelectorOptions.createCollection(rootOpts);
            PrismObject<ResourceType> resource = provisioningService.getObject(ResourceType.class, refDiscr.getResourceOid(), options, task, task.getResult());
            return resource.getName().getOrig();
        } catch (Exception e) {
            //ignoring exception and return null
            return null;
        }
    }

    private <F extends ObjectType> LensProjectionContext determineProjectionWaveDeprovision(LensContext<F> context,
                LensProjectionContext projectionContext, ResourceObjectTypeDependencyType inDependency, List<ResourceObjectTypeDependencyType> depPath) throws PolicyViolationException {
        if (depPath == null) {
            depPath = new ArrayList<>();
        }
        int determinedWave = 0;
        int determinedOrder = 0;

        // This needs to go in the reverse. We need to figure out who depends on us.
        for (DependencyAndSource ds: findReverseDependecies(context, projectionContext)) {
            LensProjectionContext dependencySourceContext = ds.sourceProjectionContext;
            ResourceObjectTypeDependencyType outDependency = ds.dependency;
            if (inDependency != null && isHigerOrder(outDependency, inDependency)) {
                // There is incomming dependency. Deal only with dependencies of this order and lower
                // otherwise we can end up in endless loop even for legal dependencies.
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("  processing (reversed) dependency: {}: ignore (higher order)", PrettyPrinter.prettyPrint(outDependency));
                }
                continue;
            }

            if (!dependencySourceContext.isDelete()) {
                ResourceObjectTypeDependencyStrictnessType outDependencyStrictness = ResourceTypeUtil.getDependencyStrictness(outDependency);
                if (outDependencyStrictness == ResourceObjectTypeDependencyStrictnessType.STRICT) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("  processing (reversed) dependency: {}: unsatisfied strict dependency", PrettyPrinter.prettyPrint(outDependency));
                    }
                    throw new PolicyViolationException("Unsatisfied strict reverse dependency of account " + dependencySourceContext.getResourceShadowDiscriminator()+
                        " dependent on " + projectionContext.getResourceShadowDiscriminator() + ": Account is provisioned, but the account that it depends on is going to be deprovisioned");
                } else if (outDependencyStrictness == ResourceObjectTypeDependencyStrictnessType.LAX) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("  processing (reversed) dependency: {}: unsatisfied lax dependency", PrettyPrinter.prettyPrint(outDependency));
                    }
                    // independent object not in the context, just ignore it
                    LOGGER.debug("Unsatisfied lax reversed dependency of account " + dependencySourceContext.getResourceShadowDiscriminator()+
                            " dependent on " + projectionContext.getResourceShadowDiscriminator() + "; dependency skipped");
                } else if (outDependencyStrictness == ResourceObjectTypeDependencyStrictnessType.RELAXED) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("  processing (reversed) dependency: {}: unsatisfied relaxed dependency", PrettyPrinter.prettyPrint(outDependency));
                    }
                    // independent object not in the context, just ignore it
                    LOGGER.debug("Unsatisfied relaxed dependency of account " + dependencySourceContext.getResourceShadowDiscriminator()+
                            " dependent on " + projectionContext.getResourceShadowDiscriminator() + "; dependency skipped");
                } else {
                    throw new IllegalArgumentException("Unknown dependency strictness "+outDependency.getStrictness()+" in "+dependencySourceContext.getResourceShadowDiscriminator());
                }
            } else {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("  processing (reversed) dependency: {}: satisfied", PrettyPrinter.prettyPrint(outDependency));
                }
                checkForCircular(depPath, outDependency, projectionContext);
                depPath.add(outDependency);
                ResourceShadowDiscriminator refDiscr = new ResourceShadowDiscriminator(outDependency,
                        projectionContext.getResource().getOid(), projectionContext.getKind());
                dependencySourceContext = determineProjectionWave(context, dependencySourceContext, outDependency, depPath);
                LOGGER.trace("    dependency projection wave: {}", dependencySourceContext.getWave());
                if (dependencySourceContext.getWave() + 1 > determinedWave) {
                    determinedWave = dependencySourceContext.getWave() + 1;
                    if (outDependency.getOrder() == null) {
                        determinedOrder = 0;
                    } else {
                        determinedOrder = outDependency.getOrder();
                    }
                }
                LOGGER.trace("    determined dependency wave: {} (order={})", determinedWave, determinedOrder);
                depPath.remove(outDependency);
            }
        }

        LensProjectionContext resultAccountContext = projectionContext;
        if (projectionContext.getWave() >=0 && projectionContext.getWave() != determinedWave) {
            // Wave for this context was set during the run of this method (it was not set when we
            // started, we checked at the beginning). Therefore this context must have been visited again.
            // therefore there is a circular dependency. Therefore we need to create another context to split it.
            if (!projectionContext.isDelete()){
                resultAccountContext = createAnotherContext(context, projectionContext, determinedOrder);
            }
        }
//            LOGGER.trace("Wave for {}: {}", resultAccountContext.getResourceAccountType(), wave);
        resultAccountContext.setWave(determinedWave);
        return resultAccountContext;
    }

    private <F extends ObjectType> Collection<DependencyAndSource> findReverseDependecies(LensContext<F> context,
            LensProjectionContext targetProjectionContext) {
        Collection<DependencyAndSource> deps = new ArrayList<>();
        for (LensProjectionContext projectionContext: context.getProjectionContexts()) {
            for (ResourceObjectTypeDependencyType dependency: projectionContext.getDependencies()) {
                if (LensUtil.isDependencyTargetContext(projectionContext, targetProjectionContext, dependency)) {
                    DependencyAndSource ds = new DependencyAndSource();
                    ds.dependency = dependency;
                    ds.sourceProjectionContext = projectionContext;
                    deps.add(ds);
                }
            }
        }
        return deps;
    }


    private void checkForCircular(List<ResourceObjectTypeDependencyType> depPath,
            ResourceObjectTypeDependencyType outDependency, LensProjectionContext projectionContext) throws PolicyViolationException {
        for (ResourceObjectTypeDependencyType pathElement: depPath) {
            if (pathElement.equals(outDependency)) {
                StringBuilder sb = new StringBuilder();
                Iterator<ResourceObjectTypeDependencyType> iterator = depPath.iterator();
                while (iterator.hasNext()) {
                    ResourceObjectTypeDependencyType el = iterator.next();
                    ObjectReferenceType resourceRef = el.getResourceRef();
                    if (resourceRef != null) {
                        sb.append(resourceRef.getOid());
                    }
                    sb.append("(").append(el.getKind()).append("/");
                    sb.append(el.getIntent()).append(")");
                    if (iterator.hasNext()) {
                        sb.append("->");
                    }
                }
                throw new PolicyViolationException("Circular dependency in "+projectionContext.getHumanReadableName()+", path: "+sb.toString());
            }
        }
    }

    private boolean isHigerOrder(ResourceObjectTypeDependencyType a,
            ResourceObjectTypeDependencyType b) {
        Integer ao = a.getOrder();
        Integer bo = b.getOrder();
        if (ao == null) {
            ao = 0;
        }
        if (bo == null) {
            bo = 0;
        }
        return ao > bo;
    }

    /**
     * Find context that has the closest order to the dependency.
     */
    private <F extends ObjectType> LensProjectionContext findDependencyTargetContext(
            LensContext<F> context, LensProjectionContext sourceProjContext, ResourceObjectTypeDependencyType dependency) {
        ResourceShadowDiscriminator refDiscr = new ResourceShadowDiscriminator(dependency,
                sourceProjContext.getResource().getOid(), sourceProjContext.getKind());
        LensProjectionContext selected = null;
        for (LensProjectionContext projectionContext: context.getProjectionContexts()) {
            if (!projectionContext.compareResourceShadowDiscriminator(refDiscr, false)) {
                continue;
            }
            int ctxOrder = projectionContext.getResourceShadowDiscriminator().getOrder();
            if (ctxOrder > refDiscr.getOrder()) {
                continue;
            }
            if (selected == null) {
                selected = projectionContext;
            } else {
                if (ctxOrder > selected.getResourceShadowDiscriminator().getOrder()) {
                    selected = projectionContext;
                }
            }
        }
        return selected;
    }

//    private <F extends ObjectType> boolean isDependencyTargetContext(LensProjectionContext sourceProjContext, LensProjectionContext targetProjectionContext, ResourceObjectTypeDependencyType dependency) {
//        ResourceShadowDiscriminator refDiscr = new ResourceShadowDiscriminator(dependency,
//                sourceProjContext.getResource().getOid(), sourceProjContext.getKind());
//        return targetProjectionContext.compareResourceShadowDiscriminator(refDiscr, false);
//    }

    private <F extends ObjectType> LensProjectionContext createAnotherContext(LensContext<F> context, LensProjectionContext origProjectionContext,
            ResourceShadowDiscriminator discr) {
        LensProjectionContext otherCtx = context.createProjectionContext(discr);
        otherCtx.setResource(origProjectionContext.getResource());
        // Force recon for the new context. This is a primitive way how to avoid phantom changes.
        otherCtx.setDoReconciliation(true);
        return otherCtx;
    }

    private <F extends ObjectType> LensProjectionContext createAnotherContext(LensContext<F> context, LensProjectionContext origProjectionContext,
            int determinedOrder) {
        ResourceShadowDiscriminator origDiscr = origProjectionContext.getResourceShadowDiscriminator();
        ResourceShadowDiscriminator discr = new ResourceShadowDiscriminator(origDiscr.getResourceOid(), origDiscr.getKind(), origDiscr.getIntent(), origDiscr.getTag(), origDiscr.isTombstone());
        discr.setOrder(determinedOrder);
        return createAnotherContext(context, origProjectionContext, discr);
    }

    /**
     * Check that the dependencies are still satisfied. Also check for high-orders vs low-order operation consistency
     * and stuff like that.
     */
    <F extends ObjectType> boolean checkDependencies(LensContext<F> context,
            LensProjectionContext projContext, OperationResult result) throws PolicyViolationException {
        if (projContext.isDelete()) {
            // It is OK if we depend on something that is not there if we are being removed ... for now
            return true;
        }

        if (projContext.getOid() == null || projContext.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.ADD) {
            // Check for lower-order contexts
            LensProjectionContext lowerOrderContext = null;
            for (LensProjectionContext projectionContext: context.getProjectionContexts()) {
                if (projContext == projectionContext) {
                    continue;
                }
                if (projectionContext.compareResourceShadowDiscriminator(projContext.getResourceShadowDiscriminator(), false) &&
                        projectionContext.getResourceShadowDiscriminator().getOrder() < projContext.getResourceShadowDiscriminator().getOrder()) {
                    if (projectionContext.getOid() != null) {
                        lowerOrderContext = projectionContext;
                        break;
                    }
                }
            }
            if (lowerOrderContext != null) {
                if (lowerOrderContext.getOid() != null) {
                    if (projContext.getOid() == null) {
                        projContext.setOid(lowerOrderContext.getOid());
                    }
                    if (projContext.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.ADD) {
                        // This context cannot be ADD. There is a lower-order context with an OID
                        // it means that the lower-order projection exists, we cannot add it twice
                        projContext.setSynchronizationPolicyDecision(SynchronizationPolicyDecision.KEEP);
                    }
                }
                if (lowerOrderContext.isDelete()) {
                    projContext.setSynchronizationPolicyDecision(SynchronizationPolicyDecision.DELETE);
                }
            }
        }

        for (ResourceObjectTypeDependencyType dependency: projContext.getDependencies()) {
            ResourceShadowDiscriminator refRat = new ResourceShadowDiscriminator(dependency,
                    projContext.getResource().getOid(), projContext.getKind());
            LOGGER.trace("LOOKING FOR {}", refRat);
            LensProjectionContext dependencyAccountContext = context.findProjectionContext(refRat);
            ResourceObjectTypeDependencyStrictnessType strictness = ResourceTypeUtil.getDependencyStrictness(dependency);
            if (dependencyAccountContext == null) {
                if (strictness == ResourceObjectTypeDependencyStrictnessType.STRICT) {
                    // This should not happen, it is checked before projection
                    throw new PolicyViolationException("Unsatisfied strict dependency of "
                            + projContext.getResourceShadowDiscriminator().toHumanReadableDescription() +
                            " dependent on " + refRat.toHumanReadableDescription() + ": No context in dependency check");
                } else if (strictness == ResourceObjectTypeDependencyStrictnessType.LAX) {
                    // independent object not in the context, just ignore it
                    LOGGER.trace("Unsatisfied lax dependency of account " +
                            projContext.getResourceShadowDiscriminator().toHumanReadableDescription() +
                            " dependent on " + refRat.toHumanReadableDescription() + "; dependency skipped");
                } else if (strictness == ResourceObjectTypeDependencyStrictnessType.RELAXED) {
                    // independent object not in the context, just ignore it
                    LOGGER.trace("Unsatisfied relaxed dependency of account "
                            + projContext.getResourceShadowDiscriminator().toHumanReadableDescription() +
                            " dependent on " + refRat.toHumanReadableDescription() + "; dependency skipped");
                } else {
                    throw new IllegalArgumentException("Unknown dependency strictness "+dependency.getStrictness()+" in "+refRat);
                }
            } else {
                // We have the context of the object that we depend on. We need to check if it was provisioned.
                if (strictness == ResourceObjectTypeDependencyStrictnessType.STRICT
                        || strictness == ResourceObjectTypeDependencyStrictnessType.RELAXED) {
                    if (wasProvisioned(dependencyAccountContext, context.getExecutionWave())) {
                        // everything OK
                    } else {
                        // We do not want to throw exception here. That will stop entire projection.
                        // Let's just mark the projection as broken and skip it.
                        LOGGER.warn("Unsatisfied dependency of account "+projContext.getResourceShadowDiscriminator()+
                                " dependent on "+refRat+": Account not provisioned in dependency check (execution wave "+context.getExecutionWave()+", account wave "+projContext.getWave() + ", dependency account wave "+dependencyAccountContext.getWave()+")");
                        projContext.setSynchronizationPolicyDecision(SynchronizationPolicyDecision.BROKEN);
                        return false;
                    }
                } else if (strictness == ResourceObjectTypeDependencyStrictnessType.LAX) {
                    // we don't care what happened, just go on
                    return true;        // TODO why return here? shouldn't we check other dependencies as well? [med]
                } else {
                    throw new IllegalArgumentException("Unknown dependency strictness "+dependency.getStrictness()+" in "+refRat);
                }
            }
        }
        return true;
    }

    <F extends ObjectType> void preprocessDependencies(LensContext<F> context){

        //in the first wave we do not have enough information to preprocess contexts
        if (context.getExecutionWave() == 0) {
            return;
        }

        for (LensProjectionContext projContext : context.getProjectionContexts()) {
            if (!projContext.isCanProject()) {
                continue;
            }

            for (ResourceObjectTypeDependencyType dependency: projContext.getDependencies()) {
                ResourceShadowDiscriminator refRat = new ResourceShadowDiscriminator(dependency,
                        projContext.getResource().getOid(), projContext.getKind());
                LOGGER.trace("LOOKING FOR {}", refRat);
                LensProjectionContext dependencyAccountContext = context.findProjectionContext(refRat);
                ResourceObjectTypeDependencyStrictnessType strictness = ResourceTypeUtil.getDependencyStrictness(dependency);
                if (dependencyAccountContext != null && dependencyAccountContext.isCanProject()) {
                    // We have the context of the object that we depend on. We need to check if it was provisioned.
                    if (strictness == ResourceObjectTypeDependencyStrictnessType.STRICT
                            || strictness == ResourceObjectTypeDependencyStrictnessType.RELAXED) {
                        if (wasExecuted(dependencyAccountContext)) {
                            // everything OK
                            if (ResourceTypeUtil.isForceLoadDependentShadow(dependency) && !dependencyAccountContext.isDelete()) {
                                dependencyAccountContext.setDoReconciliation(true);
                                projContext.setDoReconciliation(true);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Original comment (since 2014):
     *   Finally checks for all the dependencies. Some dependencies cannot be checked during wave computations as
     *   we might not have all activation decisions yet.
     *
     * However, for almost five years this method is called at end of each projection wave, i.e. not
     * only at the real end. (With the exception of previewChanges regime.) So let's keep executing
     * it in this way in both normal + preview modes.
     */
    <F extends ObjectType> void checkDependenciesFinal(LensContext<F> context, OperationResult result) throws PolicyViolationException {

        for (LensProjectionContext accountContext: context.getProjectionContexts()) {
            checkDependencies(context, accountContext, result);
        }

        for (LensProjectionContext accountContext: context.getProjectionContexts()) {
            if (accountContext.isDelete()
                    || accountContext.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.UNLINK) {
                // It is OK if we depend on something that is not there if we are being removed
                // but we still need to check if others depends on me
                for (LensProjectionContext projectionContext: context.getProjectionContexts()) {
                    if (projectionContext.isDelete()
                            || projectionContext.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.UNLINK
                            || projectionContext.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.BROKEN
                            || projectionContext.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.IGNORE) {
                        // If someone who is being deleted depends on us then it does not really matter
                        continue;
                    }
                    for (ResourceObjectTypeDependencyType dependency: projectionContext.getDependencies()) {
                        String dependencyResourceOid = dependency.getResourceRef() != null ?
                                dependency.getResourceRef().getOid() : projectionContext.getResource().getOid();
                        // TODO what to do if dependencyResourceOid or accountContext.getResource() is null?
                        if (dependencyResourceOid != null && accountContext.getResource() != null &&
                                 dependencyResourceOid.equals(accountContext.getResource().getOid()) &&
                                MiscSchemaUtil.equalsIntent(dependency.getIntent(), projectionContext.getResourceShadowDiscriminator().getIntent())) {
                            // Someone depends on us
                            if (ResourceTypeUtil.getDependencyStrictness(dependency) == ResourceObjectTypeDependencyStrictnessType.STRICT) {
                                throw new PolicyViolationException("Cannot remove "+accountContext.getHumanReadableName()
                                        +" because "+projectionContext.getHumanReadableName()+" depends on it");
                            }
                        }
                    }
                }

            }
        }
    }

    private boolean wasProvisioned(LensProjectionContext projectionContext, int executionWave) {
        int accountWave = projectionContext.getWave();
        if (accountWave >= executionWave) {
            // This had no chance to be provisioned yet, so we assume it will be provisioned
            return true;
        }
        if (projectionContext.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.BROKEN
                || projectionContext.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.IGNORE) {
            return false;
        }

        PrismObject<ShadowType> objectCurrent = projectionContext.getObjectCurrent();
        if (objectCurrent == null) {
            return false;
        }


        if (!projectionContext.isExists()) {
            return false;
        }

        // This is getting tricky now. We cannot simply check for projectionContext.isExists() here.
        // entire projection is loaded with pointInTime=future. Therefore this does NOT
        // reflect actual situation. If there is a pending operation to create the object then
        // isExists will in fact be true even if the projection was not provisioned yet.
        // We need to check pending operations to see if there is pending add delta.
        if (hasPendingAddOperation(objectCurrent)) {
            return false;
        }

        return true;
    }

    private boolean hasPendingAddOperation(PrismObject<ShadowType> objectCurrent) {
        List<PendingOperationType> pendingOperations = objectCurrent.asObjectable().getPendingOperation();
        for (PendingOperationType pendingOperation: pendingOperations) {
            if (pendingOperation.getExecutionStatus() != PendingOperationExecutionStatusType.EXECUTING) {
                continue;
            }
            ObjectDeltaType delta = pendingOperation.getDelta();
            if (delta == null) {
                continue;
            }
            if (delta.getChangeType() != ChangeTypeType.ADD) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean wasExecuted(LensProjectionContext accountContext) {
        if (accountContext.isAdd()) {
            return accountContext.getOid() != null &&
                    !accountContext.getExecutedDeltas().isEmpty();
        } else {
            return true;
        }
    }

    static class DependencyAndSource {
        ResourceObjectTypeDependencyType dependency;
        LensProjectionContext sourceProjectionContext;
    }


}
