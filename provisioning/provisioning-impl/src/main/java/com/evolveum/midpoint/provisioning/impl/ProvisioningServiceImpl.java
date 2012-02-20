/*
 * Copyright (c) 2011 Evolveum
 * 
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 * 
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.provisioning.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.evolveum.midpoint.common.QueryUtil;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PropertyPath;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.provisioning.api.ChangeNotificationDispatcher;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.provisioning.api.ResourceObjectShadowChangeDescription;
import com.evolveum.midpoint.provisioning.api.ResultHandler;
import com.evolveum.midpoint.provisioning.ucf.api.Change;
import com.evolveum.midpoint.provisioning.ucf.api.GenericFrameworkException;
import com.evolveum.midpoint.provisioning.util.ShadowCacheUtil;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.ResultArrayList;
import com.evolveum.midpoint.schema.ResultList;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.SchemaDebugUtil;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ConnectorHostType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ConnectorType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.FailedOperationTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PagingType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyAvailableValuesListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.QueryType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ScriptsType;

/**
 * Implementation of provisioning service.
 * 
 * It is just a "dispatcher" that routes interface calls to appropriate places.
 * E.g. the operations regarding resource definitions are routed directly to the
 * repository, operations of shadow objects are routed to the shadow cache and
 * so on.
 * 
 * WORK IN PROGRESS
 * 
 * There be dragons. Beware the dog. Do not trespass.
 * 
 * @author Radovan Semancik
 */
@Service(value = "provisioningService")
public class ProvisioningServiceImpl implements ProvisioningService {

	@Autowired(required=true)
	private ShadowCache shadowCache;
	@Autowired(required=true)
	private ResourceTypeManager resourceTypeManager;
	@Autowired(required=true)
	@Qualifier("cacheRepositoryService")
	private RepositoryService cacheRepositoryService;
	@Autowired(required=true)
	private ChangeNotificationDispatcher changeNotificationDispatcher;
	@Autowired(required=true)
	private ConnectorTypeManager connectorTypeManager;
	@Autowired(required=true)
	private PrismContext prismContext;

	private static final Trace LOGGER = TraceManager.getTrace(ProvisioningServiceImpl.class);

	// private static final QName TOKEN_ELEMENT_QNAME = new QName(
	// SchemaConstants.NS_PROVISIONING_LIVE_SYNC, "token");

	public ShadowCache getShadowCache() {
		return shadowCache;
	}

	public void setShadowCache(ShadowCache shadowCache) {
		this.shadowCache = shadowCache;
	}

	public ResourceTypeManager getResourceTypeManager() {
		return resourceTypeManager;
	}

	public void setResourceTypeManager(ResourceTypeManager resourceTypeManager) {
		this.resourceTypeManager = resourceTypeManager;
	}

	/**
	 * Get the value of repositoryService.
	 * 
	 * @return the value of repositoryService
	 */
	public RepositoryService getCacheRepositoryService() {
		return cacheRepositoryService;
	}

	/**
	 * Set the value of repositoryService
	 * 
	 * Expected to be injected.
	 * 
	 * @param repositoryService
	 *            new value of repositoryService
	 */
	public void setCacheRepositoryService(RepositoryService repositoryService) {
		this.cacheRepositoryService = repositoryService;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends ObjectType> PrismObject<T> getObject(Class<T> type, String oid, PropertyReferenceListType resolve,
			OperationResult parentResult) throws ObjectNotFoundException, CommunicationException,
			SchemaException {

		Validate.notNull(oid, "Oid of object to get must not be null.");
		Validate.notNull(parentResult, "Operation result must not be null.");

		// LOGGER.trace("**PROVISIONING: Getting object with oid {}", oid);

		// Result type for this operation
		OperationResult result = parentResult.createSubresult(ProvisioningService.class.getName()
				+ ".getObject");
		result.addParam("oid", oid);
		result.addParam("resolve", resolve);
		result.addContext(OperationResult.CONTEXT_IMPLEMENTATION_CLASS, ProvisioningServiceImpl.class);

		PrismObject<T> repositoryObject = null;

		try {
			repositoryObject = getCacheRepositoryService().getObject(type, oid, resolve, result);
			// if (LOGGER.isTraceEnabled()) {
			// LOGGER.trace("**PROVISIONING: Got repository object {}",
			// JAXBUtil.silentMarshalWrap(repositoryObject));
			// }
		} catch (ObjectNotFoundException e) {
			LOGGER.error("Can't get obejct with oid {}. Reason {}", oid, e);
			result.recordFatalError("Can't get object with oid " + oid + ". Reason: " + e.getMessage(), e);
			throw e;
		} catch (SchemaException ex) {
			LOGGER.error("Can't get obejct with oid {}. Reason {}", oid, ex);
			result.recordFatalError("Can't get object with oid " + oid + ". Reason: " + ex.getMessage(), ex);
			throw ex;
		}

		if (repositoryObject.canRepresent(ResourceObjectShadowType.class)) {
			// ResourceObjectShadowType shadow =
			// (ResourceObjectShadowType)object;
			// TODO: optimization needed: avoid multiple "gets" of the same
			// object

			ResourceObjectShadowType shadow = null;
			try {
				
				shadow = getShadowCache().getShadow((Class<ResourceObjectShadowType>)type, oid, 
						(ResourceObjectShadowType) (repositoryObject.asObjectable()), result);
				// if (LOGGER.isTraceEnabled()) {
				// LOGGER.trace("**PROVISIONING: Got shadow object {}",
				// JAXBUtil.silentMarshalWrap(shadow));
				// }
			} catch (ObjectNotFoundException e) {
				LOGGER.error("Can't get obejct with oid {}. Reason {}", oid, e);
				result.recordFatalError(e);
				throw e;
			} catch (CommunicationException e) {
				LOGGER.error("Can't get obejct with oid {}. Reason {}", oid, e);
				result.recordFatalError(e);
				throw e;
			} catch (SchemaException e) {
				LOGGER.error("Can't get obejct with oid {}. Reason {}", oid, e);
				result.recordFatalError(e);
				throw e;
			}

			// TODO: object resolving

			result.recordSuccess();
			// LOGGER.trace("Get object finished.");
			return shadow.asPrismObject();

		} else if (repositoryObject.canRepresent(ResourceType.class)) {
			// Make sure that the object is complete, e.g. there is a (fresh)
			// schema
			try {
				ResourceType completeResource = getResourceTypeManager().completeResource(
						(ResourceType) repositoryObject.asObjectable(), null, result);
				result.computeStatus("Resource retrieval failed");
				return completeResource.asPrismObject();
			} catch (ObjectNotFoundException ex) {
				result.recordFatalError("Resource object not found", ex);
				throw ex;
			} catch (SchemaException ex) {
				result.recordFatalError("Schema violation", ex);
				throw ex;
			} catch (CommunicationException ex) {
				result.recordFatalError("Error communicating with resource", ex);
				throw ex;
			}
		} else {
			result.recordSuccess();
			return repositoryObject;
		}

	}

	@Override
	public <T extends ObjectType> String addObject(PrismObject<T> object, ScriptsType scripts, OperationResult parentResult)
			throws ObjectAlreadyExistsException, SchemaException, CommunicationException,
			ObjectNotFoundException {
		// TODO

		Validate.notNull(object, "Object to add must not be null.");
		Validate.notNull(parentResult, "Operation result must not be null.");

		LOGGER.trace("**PROVISIONING: Start to add object {}", object);

		OperationResult result = parentResult.createSubresult(ProvisioningService.class.getName()
				+ ".addObject");
		result.addParam("object", object);
		result.addParam("scripts", scripts);
		result.addContext(OperationResult.CONTEXT_IMPLEMENTATION_CLASS, ProvisioningServiceImpl.class);

		String oid = null;
		if (object.canRepresent(ResourceObjectShadowType.class)) {
			try {
				// calling shadow cache to add object
				oid = getShadowCache().addShadow((ResourceObjectShadowType) object.asObjectable(), scripts, null, result);
				LOGGER.trace("**PROVISIONING: Added shadow object {}", oid);
				result.recordSuccess();
			} catch (GenericFrameworkException ex) {
				LOGGER.error("**PROVISIONING: Can't add object {}. Reason {}", object, ex);
				result.recordFatalError("Failed to add shadow object: " + ex.getMessage(), ex);
				throw new CommunicationException(ex.getMessage(), ex);
			} catch (SchemaException ex) {
				LOGGER.error("**PROVISIONING: Couldn't add object. Reason: {}", ex.getMessage(), ex);
				result.recordFatalError("Couldn't add object. Reason: " + ex.getMessage(), ex);
				throw new SchemaException("Couldn't add object. Reason: " + ex.getMessage(), ex);
			} catch (ObjectAlreadyExistsException ex) {
				result.recordFatalError("Could't add object. Object already exist, " + ex.getMessage(), ex);
				throw new ObjectAlreadyExistsException("Could't add object. Object already exist, "
						+ ex.getMessage(), ex);
			}
		} else {
			oid = cacheRepositoryService.addObject(object, result);
		}

		LOGGER.trace("**PROVISIONING: Adding object finished.");
		return oid;
	}

	@Override
	public int synchronize(String resourceOid, Task task, OperationResult parentResult)
			throws ObjectNotFoundException, CommunicationException, SchemaException {

		Validate.notNull(resourceOid, "Resource oid must not be null.");
		Validate.notNull(task, "Task must not be null.");
		Validate.notNull(parentResult, "Operation result must not be null.");

		OperationResult result = parentResult.createSubresult(ProvisioningService.class.getName()
				+ ".synchronize");
		result.addParam(OperationResult.PARAM_OID, resourceOid);
		result.addParam(OperationResult.PARAM_TASK, task);

		int processedChanges = 0;

		try {
			// Resolve resource
			PrismObject<ResourceType> resourceObject = getObject(ResourceType.class, resourceOid,
					new PropertyReferenceListType(), result);

			ResourceType resourceType = resourceObject.asObjectable();

			LOGGER.trace("**PROVISIONING: Start synchronization of resource {} ",
					SchemaDebugUtil.prettyPrint(resourceType));

			// getting token form task
			PrismProperty tokenProperty = null;

			if (task.getExtension() != null) {
				tokenProperty = task.getExtension(SchemaConstants.SYNC_TOKEN);
			}
			
			if (tokenProperty != null && (tokenProperty.getValue() == null || tokenProperty.getValue().getValue() == null)) {
				LOGGER.warn("Sync token exists, but it is empty (null value). Ignoring it.");
				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("Empty sync token property:\n{}",tokenProperty.dump());
				}
				tokenProperty = null;
			}

			// if the token is not specified in the task, get the latest token
			if (tokenProperty == null) {
				tokenProperty = getShadowCache().fetchCurrentToken(resourceType, parentResult);
				if (tokenProperty == null || tokenProperty.getValue() == null || tokenProperty.getValue().getValue() == null) {
					if (LOGGER.isTraceEnabled()) {
						LOGGER.trace("Empty current sync token property:\n{}",tokenProperty.dump());
					}
					throw new IllegalStateException("Current sync token null or empty: "+tokenProperty);
				}

			}

			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("**PROVISIONING: Got token property: {} from the task extension.",
						SchemaDebugUtil.prettyPrint(tokenProperty));
			}

			Collection<PropertyDelta> taskModifications = new ArrayList<PropertyDelta>();
			List<Change> changes = null;

			LOGGER.trace("Calling shadow cache to fetch changes.");
			changes = getShadowCache().fetchChanges(resourceType, tokenProperty, result);

			// for each change from the connector create change description
			for (Change change : changes) {

				// this is the case,when we want to skip processing of change,
				// because the shadow was not created or found to the resource
				// object
				// it may be caused with the fact, that the object which was
				// created in the resource was deleted before the sync run
				// such a change should be skipped to process consistent changes
				if (change.getOldShadow() == null) {
					PrismProperty newToken = change.getToken();
					PropertyDelta modificatedToken = getTokenModification(newToken);
					taskModifications.add(modificatedToken);
					processedChanges++;
					LOGGER.debug("Skipping processing change. Can't find appropriate shadow (e.g. the object was deleted on the resource meantime).");
					continue;
				}

				ResourceObjectShadowChangeDescription shadowChangeDescription = createResourceShadowChangeDescription(
						change, resourceType);
				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("**PROVISIONING: Created resource object shadow change description {}",
							SchemaDebugUtil.prettyPrint(shadowChangeDescription));
				}
				OperationResult notifyChangeResult = new OperationResult(ProvisioningService.class.getName()
						+ "notifyChange");
				notifyChangeResult.addParam("resourceObjectShadowChangeDescription", shadowChangeDescription);

				try {
					notifyResourceObjectChangeListeners(shadowChangeDescription, task, notifyChangeResult);
					notifyChangeResult.recordSuccess();
				} catch (RuntimeException ex) {
					notifyChangeResult.recordFatalError("Runtime exception occur: " + ex.getMessage(), ex);
					saveAccountResult(shadowChangeDescription, change, notifyChangeResult, parentResult);
					new RuntimeException(ex.getMessage(), ex);
				}

				notifyChangeResult.computeStatus("Error by notify change operation.");

				if (notifyChangeResult.isSuccess()) {
					deleteShadowFromRepo(change, parentResult);

					// get updated token from change,
					// create property modification from new token
					// and replace old token with the new one
					PrismProperty newToken = change.getToken();
					PropertyDelta modificatedToken = getTokenModification(newToken);
					taskModifications.add(modificatedToken);
					processedChanges++;

				} else {
					saveAccountResult(shadowChangeDescription, change, notifyChangeResult, parentResult);
				}

			}
			// also if no changes was detected, update token
			if (changes.isEmpty()) {
				LOGGER.trace("No changes to synchronize on " + ObjectTypeUtil.toShortString(resourceType));
				PropertyDelta modificatedToken = getTokenModification(tokenProperty);
				taskModifications.add(modificatedToken);
			}
			task.modify(taskModifications, result);

			// This happens in the (scheduled async) task. Recording of results
			// in the task is still not
			// ideal, therefore also log the errors with a full stack trace.
		} catch (ObjectNotFoundException e) {
			LOGGER.error("Synchronization error: object not found: {}", e.getMessage(), e);
			result.recordFatalError(e.getMessage(), e);
			throw new ObjectNotFoundException(e.getMessage(), e);
		} catch (CommunicationException e) {
			LOGGER.error("Synchronization error: communication problem: {}", e.getMessage(), e);
			result.recordFatalError("Error communicating with connector: " + e.getMessage(), e);
			throw new CommunicationException(e.getMessage(), e);
		} catch (GenericFrameworkException e) {
			LOGGER.error("Synchronization error: generic connector framework error: {}", e.getMessage(), e);
			result.recordFatalError(e.getMessage(), e);
			throw new CommunicationException(e.getMessage(), e);
		} catch (SchemaException e) {
			LOGGER.error("Synchronization error: schema problem: {}", e.getMessage(), e);
			result.recordFatalError(e.getMessage(), e);
			throw new SchemaException(e.getMessage(), e);
		}

		result.recordSuccess();
		return processedChanges;

	}

	@Override
	public <T extends ObjectType> ResultList<PrismObject<T>> listObjects(Class<T> objectType, PagingType paging,
			OperationResult parentResult) {

		Validate.notNull(objectType, "Object type to list must not be null.");
		Validate.notNull(parentResult, "Operation result must not be null.");

		LOGGER.trace("**PROVISIONING: Start listing objects of type {}", objectType);
		// Result type for this operation
		OperationResult result = parentResult.createSubresult(ProvisioningService.class.getName()
				+ ".listObjects");
		result.addParam("objectType", objectType);
		result.addParam("paging", paging);
		result.addContext(OperationResult.CONTEXT_IMPLEMENTATION_CLASS, ProvisioningServiceImpl.class);

		ResultList<PrismObject<T>> objListType = null;

		// TODO: should listing connectors trigger rediscovery?

		// if (ConnectorType.class.isAssignableFrom(objectType)) {
		// Set<ConnectorType> connectors = getShadowCache()
		// .getConnectorFactory().listConnectors();
		// if (connectors == null) {
		// result.recordFatalError("Can't list connectors.");
		// throw new IllegalStateException("Can't list connectors.");
		// }
		// if (connectors.isEmpty()) {
		// LOGGER.debug("There are no connectors known to the system.");
		// }
		// objListType = new ObjectListType();
		// for (ConnectorType connector : connectors) {
		// objListType.getObject().add(connector);
		// }
		// result.recordSuccess();
		// return objListType;
		// }

		if (ResourceObjectShadowType.class.isAssignableFrom(objectType)) {
			// Listing of shadows is not supported because this operation does
			// not specify resource
			// to search. Maybe we need another operation for this.

			result.recordFatalError("Listing of shadows is not supported");
			throw new NotImplementedException("Listing of shadows is not supported");

		} else {
			// TODO: delegate to repository
			objListType = getCacheRepositoryService().listObjects(objectType, paging, parentResult);

		}

		if (ResourceType.class.equals(objectType)) {
			ResultList<PrismObject<T>> newObjListType = new ResultArrayList<PrismObject<T>>();
			for (PrismObject<T> obj : objListType) {
				OperationResult objResult = new OperationResult(ProvisioningService.class.getName()
						+ ".listObjects.object");
				PrismObject<ResourceType> resource = (PrismObject<ResourceType>) obj;
				ResourceType completeResource;

				try {

					completeResource = getResourceTypeManager().completeResource(resource.asObjectable(), null, objResult);
					newObjListType.add(completeResource.asPrismObject());
					// TODO: what do to with objResult??

				} catch (ObjectNotFoundException e) {
					LOGGER.error("Error while completing {}: {}. Using non-complete resource.", new Object[] {
							resource, e.getMessage(), e });
					objResult.recordFatalError(e);
					obj.asObjectable().setFetchResult(objResult.createOperationResultType());
					newObjListType.add(obj);
					result.addSubresult(objResult);
					result.recordPartialError(e);

				} catch (SchemaException e) {
					LOGGER.error("Error while completing {}: {}. Using non-complete resource.", new Object[] {
							resource, e.getMessage(), e });
					objResult.recordFatalError(e);
					obj.asObjectable().setFetchResult(objResult.createOperationResultType());
					newObjListType.add(obj);
					result.addSubresult(objResult);
					result.recordPartialError(e);

				} catch (CommunicationException e) {
					LOGGER.error("Error while completing {}: {}. Using non-complete resource.", new Object[] {
							resource, e.getMessage(), e });
					objResult.recordFatalError(e);
					obj.asObjectable().setFetchResult(objResult.createOperationResultType());
					newObjListType.add(obj);
					result.addSubresult(objResult);
					result.recordPartialError(e);

				} catch (RuntimeException e) {
					// FIXME: Strictly speaking, the runtime exception should
					// not be handled here.
					// The runtime exceptions should be considered fatal anyway
					// ... but some of the
					// ICF exceptions are still translated to system exceptions.
					// So this provides
					// a better robustness now.
					LOGGER.error("System error while completing {}: {}. Using non-complete resource.",
							new Object[] { resource, e.getMessage(), e });
					objResult.recordFatalError(e);
					obj.asObjectable().setFetchResult(objResult.createOperationResultType());
					newObjListType.add(obj);
					result.addSubresult(objResult);
					result.recordPartialError(e);
				}
			}
			result.computeStatus();
			result.recordSuccessIfUnknown();
			return newObjListType;
		}

		result.recordSuccess();
		return objListType;

	}

	@Override
	public <T extends ObjectType> ResultList<PrismObject<T>> searchObjects(Class<T> type, QueryType query,
			PagingType paging, OperationResult parentResult) throws SchemaException, ObjectNotFoundException,
			CommunicationException {

		final ResultList<PrismObject<T>> objListType = new ResultArrayList<PrismObject<T>>();

		final ResultHandler<T> handler = new ResultHandler<T>() {

			@SuppressWarnings("unchecked")
			@Override
			public boolean handle(PrismObject<T> object, OperationResult parentResult) {
				return objListType.add(object);
			}
		};

		searchObjectsIterative(type, query, paging, handler, parentResult);
		return objListType;
	}

	@Override
	public <T extends ObjectType> void modifyObject(Class<T> type, ObjectDelta<T> objectChange,
			ScriptsType scripts, OperationResult parentResult) throws ObjectNotFoundException,
			SchemaException, CommunicationException {

		Validate.notNull(objectChange, "Object change must not be null.");
		Validate.notNull(parentResult, "Operation result must not be null.");

		OperationResult result = parentResult.createSubresult(ProvisioningService.class.getName()
				+ ".modifyObject");
		result.addParam("objectChange", objectChange);
		result.addParam(OperationResult.PARAM_OID, objectChange.getOid());
		result.addParam("scripts", scripts);
		result.addContext(OperationResult.CONTEXT_IMPLEMENTATION_CLASS, ProvisioningServiceImpl.class);

		LOGGER.trace("**PROVISIONING: Start to modify object.");
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("*PROVISIONING: Object change:\n{}", objectChange.dump());
		}

		if (objectChange == null || objectChange.getOid() == null) {
			result.recordFatalError("Object change or object change oid cannot be null");
			throw new IllegalArgumentException("Object change or object change oid cannot be null");
		}

		// getting object to modify
		PrismObject<T> object = getCacheRepositoryService().getObject(type, objectChange.getOid(),
				new PropertyReferenceListType(), parentResult);

		LOGGER.trace("**PROVISIONING: Modifying object with oid {}", objectChange.getOid());
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("**PROVISIONING: Object to modify:\n{}.", object.dump());
		}

		try {

			// calling shadow cache to modify object
			getShadowCache().modifyShadow(object.asObjectable(), null, objectChange, scripts, parentResult);
			result.recordSuccess();

		} catch (CommunicationException e) {
			result.recordFatalError(e);
			throw e;
		} catch (GenericFrameworkException e) {
			result.recordFatalError(e);
			throw new CommunicationException(e.getMessage(), e);
		} catch (SchemaException e) {
			result.recordFatalError(e);
			throw e;
		} catch (ObjectNotFoundException e) {
			result.recordFatalError(e);
			throw e;
		} catch (RuntimeException e) {
			result.recordFatalError(e);
			throw new SystemException("Internal error: " + e.getMessage(), e);
		}

		LOGGER.trace("Finished modifying of object with oid {}", objectChange.getOid());
	}

	@Override
	public <T extends ObjectType> void deleteObject(Class<T> type, String oid, ScriptsType scripts,
			OperationResult parentResult) throws ObjectNotFoundException, CommunicationException,
			SchemaException {
		// TODO Auto-generated method stub

		Validate.notNull(oid, "Oid of object to delete must not be null.");
		Validate.notNull(parentResult, "Operation result must not be null.");

		LOGGER.trace("**PROVISIONING: Start to delete object with oid {}", oid);

		OperationResult result = parentResult.createSubresult(ProvisioningService.class.getName()
				+ ".deleteObject");
		result.addParam("oid", oid);
		result.addParam("scripts", scripts);
		result.addContext(OperationResult.CONTEXT_IMPLEMENTATION_CLASS, ProvisioningServiceImpl.class);

		PrismObject<ObjectType> object = null;
		try {
			object = getCacheRepositoryService().getObject(ObjectType.class, oid,
					new PropertyReferenceListType(), parentResult);
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("**PROVISIONING: Object from repository to delete:\n{}", object.dump());
            }
		} catch (SchemaException e) {
			result.recordFatalError("Can't get object with oid " + oid + " from repository. Reason:  "
					+ e.getMessage() + " " + e);
			throw new ObjectNotFoundException(e.getMessage());
		}

		// TODO:check also others shadow objects
		if (object.canRepresent(ResourceObjectShadowType.class)) {

			try {
				getShadowCache().deleteShadow(object.asObjectable(), scripts, null, parentResult);
				result.recordSuccess();
			} catch (CommunicationException e) {
				result.recordFatalError(e.getMessage());
				throw new CommunicationException(e.getMessage(), e);
			} catch (GenericFrameworkException e) {
				result.recordFatalError(e.getMessage());
				throw new CommunicationException(e.getMessage(), e);
			} catch (SchemaException e) {
				result.recordFatalError(e.getMessage());
				throw new SchemaException(e.getMessage(), e);
			}

		} else {

			try {

				getCacheRepositoryService().deleteObject(type, oid, result);

			} catch (ObjectNotFoundException ex) {
				result.recordFatalError(ex);
				throw ex;
			}

		}
		LOGGER.trace("**PROVISIONING: Finished deleting object.");

		result.recordSuccess();
	}

	@Override
	public PropertyAvailableValuesListType getPropertyAvailableValues(String oid,
			PropertyReferenceListType properties, OperationResult parentResult)
			throws ObjectNotFoundException {
		// TODO Auto-generated method stub
		throw new NotImplementedException();
	}

	@Override
	public OperationResult testResource(String resourceOid) throws ObjectNotFoundException {
		// We are not going to create parent result here. We don't want to
		// pollute the result with
		// implementation details, as this will be usually displayed in the
		// table of "test resource" results.

		Validate.notNull(resourceOid, "Resource OID to test is null.");

		LOGGER.trace("Start testing resource with oid {} ", resourceOid);

		OperationResult parentResult = new OperationResult(TEST_CONNECTION_OPERATION);
		parentResult.addParam("resourceOid", resourceOid);
		parentResult.addContext(OperationResult.CONTEXT_IMPLEMENTATION_CLASS, ProvisioningServiceImpl.class);

		ResourceType resourceType = null;
		try {
			PrismObject<ResourceType> resource = getCacheRepositoryService().getObject(ResourceType.class, resourceOid,
					new PropertyReferenceListType(), parentResult);

			resourceType = resource.asObjectable();
			resourceTypeManager.testConnection(resourceType, parentResult);

		} catch (ObjectNotFoundException ex) {
			throw new ObjectNotFoundException("Object with OID " + resourceOid + " not found");
		} catch (SchemaException ex) {
			throw new IllegalArgumentException(ex.getMessage(), ex);
		}
		parentResult.computeStatus("Test resource has failed");

		LOGGER.trace("Finished testing {}, result: {} ", ObjectTypeUtil.toShortString(resourceType),
				parentResult.getStatus());
		return parentResult;
	}

	@Override
	public ResultList<PrismObject<? extends ResourceObjectShadowType>> listResourceObjects(String resourceOid,
			QName objectClass, PagingType paging, OperationResult parentResult) throws SchemaException,
			ObjectNotFoundException, CommunicationException {

		final OperationResult result = parentResult.createSubresult(ProvisioningService.class.getName()
				+ ".listResourceObjects");
		result.addParam("resourceOid", resourceOid);
		result.addParam("objectClass", objectClass);
		result.addParam("paging", paging);
		result.addContext(OperationResult.CONTEXT_IMPLEMENTATION_CLASS, ProvisioningServiceImpl.class);

		if (resourceOid == null) {
			throw new IllegalArgumentException("Resource not defined in a search query");
		}
		if (objectClass == null) {
			throw new IllegalArgumentException("Objectclass not defined in a search query");
		}

		PrismObject<ResourceType> resource = null;
		try {
			resource = getCacheRepositoryService().getObject(ResourceType.class, resourceOid,
					new PropertyReferenceListType(), result);

		} catch (ObjectNotFoundException e) {
			result.recordFatalError("Resource with oid " + resourceOid + "not found. Reason: " + e);
			throw new ObjectNotFoundException(e.getMessage(), e);
		}

		final ResultList<PrismObject<? extends ResourceObjectShadowType>> objectList = new ResultArrayList<PrismObject<? extends ResourceObjectShadowType>>();

		final ShadowHandler shadowHandler = new ShadowHandler() {
			@Override
			public boolean handle(ResourceObjectShadowType shadow) {
				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("listResourceObjects: processing shadow: {}", SchemaDebugUtil.prettyPrint(shadow));
				}

				objectList.add(shadow.asPrismObject());
				return true;
			}
		};

		resourceTypeManager.listShadows(resource.asObjectable(), objectClass, shadowHandler, false, result);

		return objectList;
	}

	@Override
	public <T extends ObjectType> void searchObjectsIterative(Class<T> type, QueryType query, PagingType paging, 
			final ResultHandler<T> handler, final OperationResult parentResult) throws SchemaException, ObjectNotFoundException,
			CommunicationException {

		Validate.notNull(query, "Search query must not be null.");
		Validate.notNull(parentResult, "Operation result must not be null.");

		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Start to search object. Query {}", QueryUtil.dump(query));
		}

		final OperationResult result = parentResult.createSubresult(ProvisioningService.class.getName()
				+ ".searchObjectsIterative");
		result.addParam("query", query);
		result.addParam("paging", paging);
		result.addContext(OperationResult.CONTEXT_IMPLEMENTATION_CLASS, ProvisioningServiceImpl.class);

		Element filter = query.getFilter();
		NodeList list = filter.getChildNodes();
		String resourceOid = null;
		QName objectClass = null;

		if (QNameUtil.compareQName(SchemaConstants.C_FILTER_AND, filter)) {
			for (int i = 0; i < list.getLength(); i++) {
				if (QNameUtil.compareQName(SchemaConstants.C_FILTER_TYPE, list.item(i))) {
					String filterType = list.item(i).getAttributes().getNamedItem("uri").getNodeValue();
					if (filterType == null || "".equals(filterType)) {
						result.recordFatalError("Object type is not defined.");
						throw new IllegalArgumentException("Object type is not defined.");
					}

				} else if (QNameUtil.compareQName(SchemaConstants.C_FILTER_EQUAL, list.item(i))) {
					NodeList equealList = list.item(i).getChildNodes();

					for (int j = 0; j < equealList.getLength(); j++) {
						if (QNameUtil.compareQName(SchemaConstants.C_FILTER_VALUE, equealList.item(j))) {
							Node value = equealList.item(j).getFirstChild();
							if (QNameUtil.compareQName(SchemaConstants.I_RESOURCE_REF, value)) {
								resourceOid = value.getAttributes().getNamedItem("oid").getNodeValue();
								LOGGER.trace("**PROVISIONING: Search objects on resource with oid {}",
										resourceOid);

							} else if (QNameUtil.compareQName(SchemaConstants.I_OBJECT_CLASS, value)) {
								objectClass = DOMUtil.getQNameValue((Element) value);
								LOGGER.trace("**PROVISIONING: Object class to search: {}", objectClass);
								if (objectClass == null) {
									result.recordFatalError("Object class was not defined.");
									throw new IllegalArgumentException("Object class was not defined.");
								}
							}
						}
					}
				}
			}
		}

		if (resourceOid == null) {
			throw new IllegalArgumentException("Resource not defined in a search query");
		}
		if (objectClass == null) {
			throw new IllegalArgumentException("Objectclass not defined in a search query");
		}

		PrismObject<ResourceType> resource = null;
		try {
			// Don't use repository. Repository resource will not have properly
			// set capabilities
			resource = getObject(ResourceType.class, resourceOid, null, result);

		} catch (ObjectNotFoundException e) {
			result.recordFatalError("Resource with oid " + resourceOid + "not found. Reason: " + e);
			throw new ObjectNotFoundException(e.getMessage(), e);
		}

		final ShadowHandler shadowHandler = new ShadowHandler() {

			@Override
			public boolean handle(ResourceObjectShadowType shadowType) {
				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("searchObjectsIterative: processing shadow: {}",
							SchemaDebugUtil.prettyPrint(shadowType));
				}

				OperationResult accountResult = result.createSubresult(ProvisioningService.class.getName()
						+ ".searchObjectsIterative.handle");
				boolean doContinue = handler.handle(shadowType.asPrismObject(), accountResult);
				accountResult.computeStatus();
				
				if (!accountResult.isSuccess()) {
					ObjectDelta shadowModificationType = ObjectDelta
							.createModificationReplaceProperty(shadowType.getOid(), SchemaConstants.C_RESULT,
									accountResult.createOperationResultType());
					try {
						cacheRepositoryService.modifyObject(AccountShadowType.class, shadowModificationType,
								result);
					} catch (ObjectNotFoundException ex) {
						result.recordFatalError(
								"Saving of result to " + ObjectTypeUtil.toShortString(shadowType)
										+ " shadow failed: Not found: "+ex.getMessage(), ex);
					} catch (SchemaException ex) {
						result.recordFatalError(
								"Saving of result to " + ObjectTypeUtil.toShortString(shadowType)
										+ " shadow failed: Schema error: "+ex.getMessage(), ex);
					}
				}

				return doContinue;
			}
		};

		getResourceTypeManager().searchObjectsIterative((Class<? extends ResourceObjectShadowType>)type,
				objectClass, resource.asObjectable(), shadowHandler, null, result);
		result.recordSuccess();
	}

	private synchronized void notifyResourceObjectChangeListeners(
			ResourceObjectShadowChangeDescription change, Task task, OperationResult parentResult) {
		changeNotificationDispatcher.notifyChange(change, task, parentResult);
	}

	private ResourceObjectShadowChangeDescription createResourceShadowChangeDescription(Change change,
			ResourceType resourceType) {
		ResourceObjectShadowChangeDescription shadowChangeDescription = new ResourceObjectShadowChangeDescription();
		shadowChangeDescription.setObjectDelta(change.getObjectDelta());
		shadowChangeDescription.setResource(resourceType.asPrismObject());
		shadowChangeDescription.setOldShadow(change.getOldShadow());
		ResourceObjectShadowType currentShadowType = change.getCurrentShadow().asObjectable();
		currentShadowType.setActivation(ShadowCacheUtil.completeActivation(currentShadowType, resourceType, null));

		shadowChangeDescription.setCurrentShadow(change.getCurrentShadow());
		shadowChangeDescription.setSourceChannel(QNameUtil.qNameToUri(SchemaConstants.CHANGE_CHANNEL_SYNC));
		return shadowChangeDescription;

	}

	private PropertyDelta getTokenModification(PrismProperty token) {
		PropertyDelta tokenDelta = new PropertyDelta(new PropertyPath(ResourceObjectShadowType.F_EXTENSION, token.getName()),
				token.getDefinition());
		tokenDelta.setValuesToReplace((Collection)token.getValues());
		return tokenDelta;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.evolveum.midpoint.provisioning.api.ProvisioningService#discoverConnectors
	 * (com.evolveum.midpoint.xml.ns._public.common.common_1.ConnectorHostType,
	 * com.evolveum.midpoint.common.result.OperationResult)
	 */
	@Override
	public Set<ConnectorType> discoverConnectors(ConnectorHostType hostType, OperationResult parentResult)
			throws CommunicationException {
		OperationResult result = parentResult.createSubresult(ProvisioningService.class.getName()
				+ ".discoverConnectors");
		result.addParam("host", hostType);
		result.addContext(OperationResult.CONTEXT_IMPLEMENTATION_CLASS, ProvisioningServiceImpl.class);

		Set<ConnectorType> discoverConnectors;
		try {
			discoverConnectors = connectorTypeManager.discoverConnectors(hostType, result);
		} catch (CommunicationException ex) {
			result.recordFatalError("Discovery failed", ex);
			throw ex;
		}

		result.computeStatus("Connector discovery failed");
		return discoverConnectors;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.evolveum.midpoint.provisioning.api.ProvisioningService#initialize()
	 */
	@Override
	public void postInit(OperationResult parentResult) {

		OperationResult result = parentResult.createSubresult(ProvisioningService.class.getName()
				+ ".initialize");
		result.addContext(OperationResult.CONTEXT_IMPLEMENTATION_CLASS, ProvisioningServiceImpl.class);

		// Discover local connectors
		Set<ConnectorType> discoverLocalConnectors = connectorTypeManager.discoverLocalConnectors(result);
		for (ConnectorType connector : discoverLocalConnectors) {
			LOGGER.info("Discovered local connector {}" + ObjectTypeUtil.toShortString(connector));
		}

		result.computeStatus("Provisioning post-initialization failed");
	}

	private ObjectDelta<? extends ResourceObjectShadowType> createShadowResultModification(
			ResourceObjectShadowChangeDescription shadowChangeDescription, Change change,
			OperationResult shadowResult) {
		

		String shadowOid = null;
		if (change.getObjectDelta() != null && change.getObjectDelta().getOid() != null){
			shadowOid = change.getObjectDelta().getOid();
		} else{
			if (change.getCurrentShadow().getOid() != null){
				shadowOid = change.getCurrentShadow().getOid();
			} else{
				if (change.getOldShadow().getOid() != null){
					shadowOid = change.getOldShadow().getOid();
				} else {
					throw new IllegalArgumentException("No uid value defined for the object to synchronize.");
				}
			}
		}

		PrismObjectDefinition<ResourceObjectShadowType> shadowDefinition = 
			ShadowCacheUtil.getResourceObjectShadowDefinition(prismContext);
		
		ObjectDelta<? extends ResourceObjectShadowType> shadowModification = ObjectDelta.createModificationReplaceProperty(
				shadowOid, SchemaConstants.C_RESULT, shadowResult.createOperationResultType());

		if (change.getObjectDelta() != null && change.getObjectDelta().getChangeType() == ChangeType.DELETE) {
			PrismPropertyDefinition failedOperationTypePropDef =
				shadowDefinition.findPropertyDefinition(ResourceObjectShadowType.F_FAILED_OPERATION_TYPE);
			PropertyDelta failedOperationTypeDelta = new PropertyDelta(ResourceObjectShadowType.F_FAILED_OPERATION_TYPE,
					failedOperationTypePropDef);
			failedOperationTypeDelta.setValueToReplace(new PrismPropertyValue(FailedOperationTypeType.DELETE));
			shadowModification.addModification(failedOperationTypeDelta);
		}
		return shadowModification;

	}

	private void saveAccountResult(ResourceObjectShadowChangeDescription shadowChangeDescription,
			Change change, OperationResult notifyChangeResult, OperationResult parentResult)
			throws ObjectNotFoundException, SchemaException {

		ObjectDelta<ResourceObjectShadowType> shadowModification = 
			(ObjectDelta<ResourceObjectShadowType>) createShadowResultModification(shadowChangeDescription, change, notifyChangeResult);
		// maybe better error handling is needed
		cacheRepositoryService.modifyObject(ResourceObjectShadowType.class, shadowModification, parentResult);

	}

	private void deleteShadowFromRepo(Change change, OperationResult parentResult)
			throws ObjectNotFoundException {
		if (change.getObjectDelta() != null && change.getObjectDelta().getChangeType() == ChangeType.DELETE
				&& change.getOldShadow() != null) {
			LOGGER.debug("Deleting detected shadow object form repository.");
			try {
				cacheRepositoryService.deleteObject(AccountShadowType.class, change.getOldShadow().getOid(),
						parentResult);
			} catch (ObjectNotFoundException ex) {
				parentResult.recordFatalError("Can't find object "
						+ change.getOldShadow() + " in repository.");
				throw new ObjectNotFoundException("Can't find object "
						+ change.getOldShadow() + " in repository.");
			}
			LOGGER.debug("Shadow object deleted successfully form repository.");
		}
	}

}
