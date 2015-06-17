/*
 * Copyright (c) 2010-2013 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.provisioning.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.common.refinery.RefinedAssociationDefinition;
import com.evolveum.midpoint.common.refinery.RefinedAttributeDefinition;
import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.prism.ModificationType;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.delta.ContainerDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.match.MatchingRule;
import com.evolveum.midpoint.prism.match.MatchingRuleRegistry;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.EqualFilter;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.util.PrismUtil;
import com.evolveum.midpoint.provisioning.api.GenericConnectorException;
import com.evolveum.midpoint.provisioning.ucf.api.AttributesToReturn;
import com.evolveum.midpoint.provisioning.ucf.api.ConnectorInstance;
import com.evolveum.midpoint.provisioning.ucf.api.GenericFrameworkException;
import com.evolveum.midpoint.provisioning.ucf.api.Operation;
import com.evolveum.midpoint.provisioning.ucf.api.PropertyModificationOperation;
import com.evolveum.midpoint.provisioning.ucf.api.ResultHandler;
import com.evolveum.midpoint.provisioning.util.ProvisioningUtil;
import com.evolveum.midpoint.schema.processor.ResourceAttribute;
import com.evolveum.midpoint.schema.processor.ResourceAttributeContainer;
import com.evolveum.midpoint.schema.processor.ResourceObjectIdentification;
import com.evolveum.midpoint.schema.processor.SearchHierarchyConstraints;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.exception.TunnelException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceObjectAssociationDirectionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowAssociationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

/**
 * Class that collects the entitlement-related methods used by ResourceObjectConverter
 * 
 * Should NOT be used by any class other than ResourceObjectConverter.
 * 
 * @author Radovan Semancik
 *
 */
@Component
class EntitlementConverter {
	
	private static final Trace LOGGER = TraceManager.getTrace(EntitlementConverter.class);
	
	@Autowired(required=true)
	private ResourceObjectReferenceResolver resourceObjectReferenceResolver;
	
	@Autowired(required=true)
	private PrismContext prismContext;
	
	@Autowired(required = true)
	private MatchingRuleRegistry matchingRuleRegistry;

	//////////
	// GET
	/////////
	
	public void postProcessEntitlementsRead(ProvisioningContext subjectCtx,
			PrismObject<ShadowType> resourceObject, OperationResult parentResult) throws SchemaException, CommunicationException, ObjectNotFoundException, ConfigurationException, SecurityViolationException {
		ResourceType resourceType = subjectCtx.getResource();
		RefinedObjectClassDefinition objectClassDefinition = subjectCtx.getObjectClassDefinition();
		Collection<RefinedAssociationDefinition> entitlementAssociationDefs = objectClassDefinition.getEntitlementAssociations();
		if (entitlementAssociationDefs != null) {
			ResourceAttributeContainer attributesContainer = ShadowUtil.getAttributesContainer(resourceObject);
			RefinedResourceSchema refinedSchema = RefinedResourceSchema.getRefinedSchema(resourceType);
			
			PrismContainerDefinition<ShadowAssociationType> associationDef = resourceObject.getDefinition().findContainerDefinition(ShadowType.F_ASSOCIATION);
			PrismContainer<ShadowAssociationType> associationContainer = associationDef.instantiate();
			
			for (RefinedAssociationDefinition assocDefType: entitlementAssociationDefs) {
				ProvisioningContext entitlementCtx = subjectCtx.spawn(ShadowKindType.ENTITLEMENT, assocDefType.getIntents());
				RefinedObjectClassDefinition entitlementDef = entitlementCtx.getObjectClassDefinition();
				if (entitlementDef == null) {
					throw new SchemaException("No definition for entitlement intent(s) '"+assocDefType.getIntents()+"' in "+resourceType);
				}
				ResourceObjectAssociationDirectionType direction = assocDefType.getResourceObjectAssociationType().getDirection();
				if (direction == ResourceObjectAssociationDirectionType.SUBJECT_TO_OBJECT) {
					postProcessEntitlementSubjectToEntitlement(resourceType, resourceObject, objectClassDefinition, assocDefType, entitlementDef, attributesContainer, associationContainer, parentResult);					
				} else if (direction == ResourceObjectAssociationDirectionType.OBJECT_TO_SUBJECT) {
					if (assocDefType.getResourceObjectAssociationType().getShortcutAssociationAttribute() != null) {
						postProcessEntitlementSubjectToEntitlement(resourceType, resourceObject, objectClassDefinition, 
								assocDefType, entitlementDef, attributesContainer, associationContainer, 
								assocDefType.getResourceObjectAssociationType().getShortcutAssociationAttribute(),
								assocDefType.getResourceObjectAssociationType().getShortcutValueAttribute(), parentResult);
					} else {
						postProcessEntitlementEntitlementToSubject(subjectCtx, resourceObject, assocDefType, entitlementCtx, attributesContainer, associationContainer, parentResult);
					}
				} else {
					throw new IllegalArgumentException("Unknown entitlement direction "+direction+" in association "+assocDefType+" in "+resourceType);
				}
			}
			
			if (!associationContainer.isEmpty()) {
				resourceObject.add(associationContainer);
			}
		}
	}
	
	private <S extends ShadowType,T> void postProcessEntitlementSubjectToEntitlement(ResourceType resourceType, PrismObject<S> resourceObject, 
			RefinedObjectClassDefinition objectClassDefinition, RefinedAssociationDefinition assocDefType,
			RefinedObjectClassDefinition entitlementDef,
			ResourceAttributeContainer attributesContainer, PrismContainer<ShadowAssociationType> associationContainer,
			OperationResult parentResult) throws SchemaException {
		QName assocAttrName = assocDefType.getResourceObjectAssociationType().getAssociationAttribute();
		QName valueAttrName = assocDefType.getResourceObjectAssociationType().getValueAttribute();
		postProcessEntitlementSubjectToEntitlement(resourceType, resourceObject, objectClassDefinition, 
				assocDefType, entitlementDef, attributesContainer, associationContainer, assocAttrName, 
				valueAttrName, parentResult);
    }
	
	private <S extends ShadowType,T> void postProcessEntitlementSubjectToEntitlement(ResourceType resourceType, 
			PrismObject<S> resourceObject, 
			RefinedObjectClassDefinition objectClassDefinition, RefinedAssociationDefinition assocDefType,
			RefinedObjectClassDefinition entitlementDef,
			ResourceAttributeContainer attributesContainer, PrismContainer<ShadowAssociationType> associationContainer,
			QName assocAttrName, QName valueAttrName,
			OperationResult parentResult) throws SchemaException {
		QName associationName = assocDefType.getName();
		if (associationName == null) {
			throw new SchemaException("No name in entitlement association "+assocDefType+" in "+resourceType);
		}

		if (assocAttrName == null) {
			throw new SchemaException("No association attribute defined in entitlement association '"+associationName+"' in "+resourceType);
		}
		RefinedAttributeDefinition assocAttrDef = objectClassDefinition.findAttributeDefinition(assocAttrName);
		if (assocAttrDef == null) {
			throw new SchemaException("Association attribute '"+assocAttrName+"'defined in entitlement association '"+associationName+"' was not found in schema for "+resourceType);
		}
		ResourceAttribute<T> assocAttr = attributesContainer.findAttribute(assocAttrName);
		if (assocAttr == null || assocAttr.isEmpty()) {
			// Nothing to do. No attribute to base the association on.
			return;
		}

		if (valueAttrName == null) {
			throw new SchemaException("No value attribute defined in entitlement association '"+associationName+"' in "+resourceType);
		}
		RefinedAttributeDefinition valueAttrDef = entitlementDef.findAttributeDefinition(valueAttrName);

        for (PrismPropertyValue<T> assocAttrPVal : assocAttr.getValues()) {

            ResourceAttribute<T> valueAttribute = valueAttrDef.instantiate();
            valueAttribute.add(assocAttrPVal.clone());

            PrismContainerValue<ShadowAssociationType> associationCVal = associationContainer.createNewValue();
            associationCVal.asContainerable().setName(associationName);
            ResourceAttributeContainer identifiersContainer = new ResourceAttributeContainer(
                    ShadowAssociationType.F_IDENTIFIERS, entitlementDef.toResourceAttributeContainerDefinition(), prismContext);
            associationCVal.add(identifiersContainer);
            identifiersContainer.add(valueAttribute);
        }
    }
	
	private <S extends ShadowType,T> void postProcessEntitlementEntitlementToSubject(ProvisioningContext subjectCtx, final PrismObject<S> resourceObject, 
			RefinedAssociationDefinition assocDefType, final ProvisioningContext entitlementCtx,
			ResourceAttributeContainer attributesContainer, final PrismContainer<ShadowAssociationType> associationContainer,
			OperationResult parentResult) throws SchemaException, CommunicationException, ObjectNotFoundException, ConfigurationException, SecurityViolationException {
		ResourceType resourceType = subjectCtx.getResource();
		final QName associationName = assocDefType.getName();
		final RefinedObjectClassDefinition entitlementDef = entitlementCtx.getObjectClassDefinition();
		if (associationName == null) {
			throw new SchemaException("No name in entitlement association "+assocDefType+" in "+resourceType);
		}
		
		QName assocAttrName = assocDefType.getResourceObjectAssociationType().getAssociationAttribute();
		if (assocAttrName == null) {
			throw new SchemaException("No association attribute defined in entitlement association '"+associationName+"' in "+resourceType);
		}
		RefinedAttributeDefinition assocAttrDef = entitlementDef.findAttributeDefinition(assocAttrName);
		if (assocAttrDef == null) {
			throw new SchemaException("Association attribute '"+assocAttrName+"'defined in entitlement association '"+associationName+"' was not found in schema for "+resourceType);
		}
		
		QName valueAttrName = assocDefType.getResourceObjectAssociationType().getValueAttribute();
		if (valueAttrName == null) {
			throw new SchemaException("No value attribute defined in entitlement association '"+associationName+"' in "+resourceType);
		}
		ResourceAttribute<T> valueAttr = attributesContainer.findAttribute(valueAttrName);
		if (valueAttr == null || valueAttr.isEmpty()) {
			throw new SchemaException("Value attribute "+valueAttrName+" has no value; attribute defined in entitlement association '"+associationName+"' in "+resourceType);
		}
		if (valueAttr.size() > 1) {
			throw new SchemaException("Value attribute "+valueAttrName+" has no more than one value; attribute defined in entitlement association '"+associationName+"' in "+resourceType);
		}
		
		ObjectQuery query = createQuery(assocDefType, assocAttrDef, valueAttr);
		
		AttributesToReturn attributesToReturn = ProvisioningUtil.createAttributesToReturn(entitlementCtx);
		
		SearchHierarchyConstraints searchHierarchyConstraints = null;
		ResourceObjectReferenceType baseContextRef = entitlementDef.getBaseContext();
		if (baseContextRef != null) {
			// TODO: this should be done once per search. Not in every run of postProcessEntitlementEntitlementToSubject
			// this has to go outside of this method
			PrismObject<ShadowType> baseContextShadow = resourceObjectReferenceResolver.resolve(baseContextRef, "base context specification in "+entitlementDef, parentResult);
			RefinedObjectClassDefinition baseContextObjectClassDefinition = subjectCtx.getRefinedSchema().determineCompositeObjectClassDefinition(baseContextShadow);
			ResourceObjectIdentification baseContextIdentification = new ResourceObjectIdentification(baseContextObjectClassDefinition, ShadowUtil.getIdentifiers(baseContextShadow));
			searchHierarchyConstraints = new SearchHierarchyConstraints(baseContextIdentification, null);
		}
		
		ResultHandler<ShadowType> handler = new ResultHandler<ShadowType>() {
			@Override
			public boolean handle(PrismObject<ShadowType> entitlementShadow) {
				PrismContainerValue<ShadowAssociationType> associationCVal = associationContainer.createNewValue();
				associationCVal.asContainerable().setName(associationName);
				Collection<ResourceAttribute<?>> entitlementIdentifiers = ShadowUtil.getIdentifiers(entitlementShadow);
				try {
					ResourceAttributeContainer identifiersContainer = new ResourceAttributeContainer(
							ShadowAssociationType.F_IDENTIFIERS, entitlementDef.toResourceAttributeContainerDefinition(), prismContext);
					associationCVal.add(identifiersContainer);
					identifiersContainer.getValue().addAll(ResourceAttribute.cloneCollection(entitlementIdentifiers));
					
					// Remember the full shadow in user data. This is used later as an optimization to create the shadow in repo 
					identifiersContainer.setUserData(ResourceObjectConverter.FULL_SHADOW_KEY, entitlementShadow);
					if (LOGGER.isTraceEnabled()) {
						LOGGER.trace("Processed entitlement-to-subject association for account {} and entitlement {}",
								ShadowUtil.getHumanReadableName(resourceObject), ShadowUtil.getHumanReadableName(entitlementShadow));
					}
				} catch (SchemaException e) {
					throw new TunnelException(e);
				}
				return true;
			}
		};
		
		ConnectorInstance connector = subjectCtx.getConnector();
		try {
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("Processed entitlement-to-subject association for account {}: query {}",
						ShadowUtil.getHumanReadableName(resourceObject), query);
			}
			try {
				connector.search(entitlementDef, query, handler, attributesToReturn, null, searchHierarchyConstraints, parentResult);
			} catch (GenericFrameworkException e) {
				throw new GenericConnectorException("Generic error in the connector " + connector + ". Reason: "
						+ e.getMessage(), e);
			}
		} catch (TunnelException e) {
			throw (SchemaException)e.getCause();
		}
		
	}

    // precondition: valueAttr has exactly one value
	private <TV,TA> ObjectQuery createQuery(RefinedAssociationDefinition assocDefType, RefinedAttributeDefinition assocAttrDef, ResourceAttribute<TV> valueAttr) throws SchemaException{
		MatchingRule<TA> matchingRule = matchingRuleRegistry.getMatchingRule(assocDefType.getResourceObjectAssociationType().getMatchingRule(),
				assocAttrDef.getTypeName());
		PrismPropertyValue<TA> converted = PrismUtil.convertPropertyValue(valueAttr.getValue(0), valueAttr.getDefinition(), assocAttrDef);
		PrismPropertyValue<TA> normalized = converted;
		if (matchingRule != null) {
			TA normalizedRealValue = matchingRule.normalize(converted.getValue());
			normalized = new PrismPropertyValue<TA>(normalizedRealValue);
		}
		LOGGER.trace("Converted entitlement filter: {} ({}) def={}", 
				new Object[]{normalized, normalized.getValue().getClass(), assocAttrDef});
		ObjectFilter filter = EqualFilter.createEqual(new ItemPath(ShadowType.F_ATTRIBUTES, assocAttrDef.getName()), assocAttrDef, normalized);
		ObjectQuery query = ObjectQuery.createObjectQuery(filter);
		query.setAllowPartialResults(true);
		return query;
	}
	
	//////////
	// ADD
	/////////
	
	public void processEntitlementsAdd(ProvisioningContext ctx, PrismObject<ShadowType> shadow) throws SchemaException {
		PrismContainer<ShadowAssociationType> associationContainer = shadow.findContainer(ShadowType.F_ASSOCIATION);
		if (associationContainer == null || associationContainer.isEmpty()) {
			return;
		}
		Map<QName, PropertyDelta<?>> deltaMap = new HashMap<QName, PropertyDelta<?>>();
		collectEntitlementToAttrsDelta(ctx, deltaMap, associationContainer.getValues(), ModificationType.ADD);
		for (PropertyDelta<?> delta: deltaMap.values()) {
			delta.applyTo(shadow);
		}
	}
	
	public <T> void collectEntitlementsAsObjectOperationInShadowAdd(ProvisioningContext ctx, Map<ResourceObjectDiscriminator, ResourceObjectOperations> roMap,
			PrismObject<ShadowType> shadow, OperationResult result) 
					throws SchemaException, ObjectNotFoundException, CommunicationException, SecurityViolationException, ConfigurationException {
		PrismContainer<ShadowAssociationType> associationContainer = shadow.findContainer(ShadowType.F_ASSOCIATION);
		if (associationContainer == null || associationContainer.isEmpty()) {
			return;
		}
		collectEntitlementsAsObjectOperation(ctx, roMap, associationContainer.getValues(), null, shadow, 
				ModificationType.ADD, result);
	}

	
	//////////
	// MODIFY
	/////////
	
	/**
	 * Collects entitlement changes from the shadow to entitlement section into attribute operations.
	 * NOTE: only collects  SUBJECT_TO_ENTITLEMENT entitlement direction.
	 */
	public void collectEntitlementChange(ProvisioningContext ctx, ContainerDelta<ShadowAssociationType> itemDelta, 
			Collection<Operation> operations) throws SchemaException {
		Map<QName, PropertyDelta<?>> deltaMap = new HashMap<QName, PropertyDelta<?>>();
		
		collectEntitlementToAttrsDelta(ctx, deltaMap, itemDelta.getValuesToAdd(), ModificationType.ADD);
		collectEntitlementToAttrsDelta(ctx, deltaMap, itemDelta.getValuesToDelete(), ModificationType.DELETE);
		collectEntitlementToAttrsDelta(ctx, deltaMap, itemDelta.getValuesToReplace(), ModificationType.REPLACE);

		for(PropertyDelta<?> attrDelta: deltaMap.values()) {
			PropertyModificationOperation attributeModification = new PropertyModificationOperation(attrDelta);
			operations.add(attributeModification);
		}
	}
	
	public <T> void collectEntitlementsAsObjectOperation(ProvisioningContext ctx, Map<ResourceObjectDiscriminator, ResourceObjectOperations> roMap,
			ContainerDelta<ShadowAssociationType> containerDelta,
			PrismObject<ShadowType> subjectShadowBefore, PrismObject<ShadowType> subjectShadowAfter, 
			OperationResult result)
					throws SchemaException, ObjectNotFoundException, CommunicationException, SecurityViolationException, ConfigurationException {
		collectEntitlementsAsObjectOperation(ctx, roMap, containerDelta.getValuesToAdd(), 
				subjectShadowBefore, subjectShadowAfter, ModificationType.ADD, result);
		collectEntitlementsAsObjectOperation(ctx, roMap, containerDelta.getValuesToDelete(),
                subjectShadowBefore, subjectShadowAfter, ModificationType.DELETE, result);
		collectEntitlementsAsObjectOperation(ctx, roMap, containerDelta.getValuesToReplace(),
                subjectShadowBefore, subjectShadowAfter, ModificationType.REPLACE, result);
	}
	
	/////////
	// DELETE
	/////////
	
	/**
	 * This is somehow different that all the other methods. We are not following the content of a shadow or delta. We are following
	 * the definitions. This is to avoid the need to read the object that is going to be deleted. In fact, the object should not be there
	 * any more, but we still want to clean up entitlement membership based on the information from the shadow.  
	 */
	public <T> void collectEntitlementsAsObjectOperationDelete(ProvisioningContext ctx, final Map<ResourceObjectDiscriminator, ResourceObjectOperations> roMap,
			PrismObject<ShadowType> shadow, OperationResult parentResult) 
					throws SchemaException, CommunicationException, ObjectNotFoundException, ConfigurationException, SecurityViolationException {

		Collection<RefinedAssociationDefinition> entitlementAssociationDefs = ctx.getObjectClassDefinition().getEntitlementAssociations();
		if (entitlementAssociationDefs == null || entitlementAssociationDefs.isEmpty()) {
			// Nothing to do
			LOGGER.trace("No associations in deleted shadow");
			return;
		}
		ResourceAttributeContainer attributesContainer = ShadowUtil.getAttributesContainer(shadow);
		for (RefinedAssociationDefinition assocDefType: ctx.getObjectClassDefinition().getEntitlementAssociations()) {
			if (assocDefType.getResourceObjectAssociationType().getDirection() != ResourceObjectAssociationDirectionType.OBJECT_TO_SUBJECT) {
				// We can ignore these. They will die together with the object. No need to explicitly delete them.
				LOGGER.trace("Ignoring subject-to-object association in deleted shadow");
				continue;
			}
			if (!assocDefType.requiresExplicitReferentialIntegrity()) {
				// Referential integrity not required for this one
				LOGGER.trace("Ignoring association in deleted shadow because it does not require explicit referential integrity assurance");
				continue;
			}
			QName associationName = assocDefType.getName();
			if (associationName == null) {
				throw new SchemaException("No name in entitlement association "+assocDefType+" in "+ctx.getResource());
			}
			ProvisioningContext entitlementCtx = ctx.spawn(ShadowKindType.ENTITLEMENT, assocDefType.getIntents());
			final RefinedObjectClassDefinition entitlementOcDef = entitlementCtx.getObjectClassDefinition();
			if (entitlementOcDef == null) {
				throw new SchemaException("No definition for entitlement intent(s) '"+assocDefType.getIntents()+"' defined in entitlement association "+associationName+" in "+ctx.getResource());
			}
			
			final QName assocAttrName = assocDefType.getResourceObjectAssociationType().getAssociationAttribute();
			if (assocAttrName == null) {
				throw new SchemaException("No association attribute defined in entitlement association '"+associationName+"' in "+ctx.getResource());
			}
			final RefinedAttributeDefinition assocAttrDef = entitlementOcDef.findAttributeDefinition(assocAttrName);
			if (assocAttrDef == null) {
				throw new SchemaException("Association attribute '"+assocAttrName+"'defined in entitlement association '"+associationName+"' was not found in schema for "+ctx.getResource());
			}
			
			QName valueAttrName = assocDefType.getResourceObjectAssociationType().getValueAttribute();
			if (valueAttrName == null) {
				throw new SchemaException("No value attribute defined in entitlement association '"+associationName+"' in "+ctx.getResource());
			}
			final ResourceAttribute<T> valueAttr = attributesContainer.findAttribute(valueAttrName);
			if (valueAttr == null || valueAttr.isEmpty()) {
				throw new SchemaException("Value attribute "+valueAttrName+" has no value; attribute defined in entitlement association '"+associationName+"' in "+ctx.getResource());
			}
			if (valueAttr.size() > 1) {
				throw new SchemaException("Value attribute "+valueAttrName+" has no more than one value; attribute defined in entitlement association '"+associationName+"' in "+ctx.getResource());
			}
			
			ObjectQuery query = createQuery(assocDefType, assocAttrDef, valueAttr);
			
//			ObjectFilter filter = EqualsFilter.createEqual(new ItemPath(ShadowType.F_ATTRIBUTES), assocAttrDef, valueAttr.getValue());
//			ObjectFilter filter = InFilter.createIn(new ItemPath(ShadowType.F_ATTRIBUTES, assocAttrDef.getName()), assocAttrDef, valueAttr.getValue());
//			ObjectFilter filter = EqualsFilter.createEqual(new ItemPath(ShadowType.F_ATTRIBUTES, assocAttrDef.getName()), assocAttrDef, valueAttr.getValue());
//			ObjectQuery query = ObjectQuery.createObjectQuery(filter); 
//					new ObjectQuery();
//			query.setFilter(filter);
			
			AttributesToReturn attributesToReturn = ProvisioningUtil.createAttributesToReturn(entitlementCtx);
			
			SearchHierarchyConstraints searchHierarchyConstraints = null;
			ResourceObjectReferenceType baseContextRef = entitlementOcDef.getBaseContext();
			if (baseContextRef != null) {
				PrismObject<ShadowType> baseContextShadow = resourceObjectReferenceResolver.resolve(baseContextRef, "base context specification in "+entitlementOcDef, parentResult);
				RefinedObjectClassDefinition baseContextObjectClassDefinition = ctx.getRefinedSchema().determineCompositeObjectClassDefinition(baseContextShadow);
				ResourceObjectIdentification baseContextIdentification = new ResourceObjectIdentification(baseContextObjectClassDefinition, ShadowUtil.getIdentifiers(baseContextShadow));
				searchHierarchyConstraints = new SearchHierarchyConstraints(baseContextIdentification, null);
			}
			
			ResultHandler<ShadowType> handler = new ResultHandler<ShadowType>() {
				@Override
				public boolean handle(PrismObject<ShadowType> entitlementShadow) {
					Collection<? extends ResourceAttribute<?>> identifiers = ShadowUtil.getIdentifiers(entitlementShadow);
					ResourceObjectDiscriminator disc = new ResourceObjectDiscriminator(entitlementOcDef.getTypeName(), identifiers);
					ResourceObjectOperations operations = roMap.get(disc);
					if (operations == null) {
						operations = new ResourceObjectOperations();
						roMap.put(disc, operations);
						operations.setObjectClassDefinition(entitlementOcDef);
					}
					
					PropertyDelta<T> attributeDelta = null;
					for(Operation operation: operations.getOperations()) {
						if (operation instanceof PropertyModificationOperation) {
							PropertyModificationOperation propOp = (PropertyModificationOperation)operation;
							if (propOp.getPropertyDelta().getElementName().equals(assocAttrName)) {
								attributeDelta = propOp.getPropertyDelta();
							}
						}
					}
					if (attributeDelta == null) {
						attributeDelta = assocAttrDef.createEmptyDelta(new ItemPath(ShadowType.F_ATTRIBUTES, assocAttrName));
						PropertyModificationOperation attributeModification = new PropertyModificationOperation(attributeDelta);
						operations.getOperations().add(attributeModification);
					}
					
					attributeDelta.addValuesToDelete(valueAttr.getClonedValues());
					LOGGER.trace("Association in deleted shadow delta: {}", attributeDelta);

					return true;
				}
			};
			try {
				LOGGER.trace("Searching for associations in deleted shadow, query: {}", query);
				ctx.getConnector().search(entitlementOcDef, query, handler, attributesToReturn, null, searchHierarchyConstraints, parentResult);
			} catch (TunnelException e) {
				throw (SchemaException)e.getCause();
			} catch (GenericFrameworkException e) {
				throw new GenericConnectorException(e.getMessage(), e);
			}
			
		}
		
	}
	
	/////////
	// common
	/////////
	
	private <T> void collectEntitlementToAttrsDelta(ProvisioningContext ctx, Map<QName, PropertyDelta<?>> deltaMap, 
			Collection<PrismContainerValue<ShadowAssociationType>> set, ModificationType modificationType) throws SchemaException {
		if (set == null) {
			return;
		}
		for (PrismContainerValue<ShadowAssociationType> associationCVal: set) {
			collectEntitlementToAttrDelta(ctx, deltaMap, associationCVal, modificationType);
		}
	}
	
	/**
	 *  Collects entitlement changes from the shadow to entitlement section into attribute operations.
	 *  Collects a single value.
	 *  NOTE: only collects  SUBJECT_TO_ENTITLEMENT entitlement direction.
	 */
	private <T> void collectEntitlementToAttrDelta(ProvisioningContext ctx, Map<QName, PropertyDelta<?>> deltaMap, 
			PrismContainerValue<ShadowAssociationType> associationCVal, ModificationType modificationType) throws SchemaException {
		RefinedObjectClassDefinition objectClassDefinition = ctx.getObjectClassDefinition();
		
		ShadowAssociationType associationType = associationCVal.asContainerable();
		QName associationName = associationType.getName();
		if (associationName == null) {
			throw new SchemaException("No name in entitlement association "+associationCVal);
		}
		RefinedAssociationDefinition assocDefType = objectClassDefinition.findEntitlementAssociation(associationName);
		if (assocDefType == null) {
			throw new SchemaException("No entitlement association with name "+associationName+" in schema of "+ctx.getResource());
		}
		
		ResourceObjectAssociationDirectionType direction = assocDefType.getResourceObjectAssociationType().getDirection();
		if (direction != ResourceObjectAssociationDirectionType.SUBJECT_TO_OBJECT) {
			// Process just this one direction. The other direction means modification of another object and
			// therefore will be processed later
			return;
		}
		
		QName assocAttrName = assocDefType.getResourceObjectAssociationType().getAssociationAttribute();
		if (assocAttrName == null) {
			throw new SchemaException("No association attribute definied in entitlement association '"+associationName+"' in "+ctx.getResource());
		}
		RefinedAttributeDefinition assocAttrDef = objectClassDefinition.findAttributeDefinition(assocAttrName);
		if (assocAttrDef == null) {
			throw new SchemaException("Association attribute '"+assocAttrName+"'definied in entitlement association '"+associationName+"' was not found in schema for "+ctx.getResource());
		}
		PropertyDelta<T> attributeDelta = (PropertyDelta<T>) deltaMap.get(assocAttrName);
		if (attributeDelta == null) {
			attributeDelta = assocAttrDef.createEmptyDelta(new ItemPath(ShadowType.F_ATTRIBUTES, assocAttrName));
			deltaMap.put(assocAttrName, attributeDelta);
		}
		
		QName valueAttrName = assocDefType.getResourceObjectAssociationType().getValueAttribute();
		if (valueAttrName == null) {
			throw new SchemaException("No value attribute definied in entitlement association '"+associationName+"' in "+ctx.getResource());
		}
		ResourceAttributeContainer identifiersContainer = 
				ShadowUtil.getAttributesContainer(associationCVal, ShadowAssociationType.F_IDENTIFIERS);
		PrismProperty<T> valueAttr = identifiersContainer.findProperty(valueAttrName);
		if (valueAttr == null) {
			throw new SchemaException("No value attribute "+valueAttrName+" present in entitlement association '"+associationName+"' in shadow for "+ctx.getResource());
		}
		
		if (modificationType == ModificationType.ADD) {
			attributeDelta.addValuesToAdd(valueAttr.getClonedValues());
		} else if (modificationType == ModificationType.DELETE) {
			attributeDelta.addValuesToDelete(valueAttr.getClonedValues());
		} else if (modificationType == ModificationType.REPLACE) {
			// TODO: check if already exists
			attributeDelta.setValuesToReplace(valueAttr.getClonedValues());
		}
	}
	
	private <T> void collectEntitlementsAsObjectOperation(ProvisioningContext ctx, Map<ResourceObjectDiscriminator, ResourceObjectOperations> roMap,
			Collection<PrismContainerValue<ShadowAssociationType>> set,
			PrismObject<ShadowType> subjectShadowBefore, PrismObject<ShadowType> subjectShadowAfter, 
			ModificationType modificationType, OperationResult result)
					throws SchemaException, ObjectNotFoundException, CommunicationException, SecurityViolationException, ConfigurationException {
		if (set == null) {
			return;
		}
		for (PrismContainerValue<ShadowAssociationType> associationCVal: set) {
			collectEntitlementAsObjectOperation(ctx, roMap, associationCVal, subjectShadowBefore, subjectShadowAfter,
					modificationType, result);
		}
	}
	
	private <TV,TA> void collectEntitlementAsObjectOperation(ProvisioningContext ctx, Map<ResourceObjectDiscriminator, ResourceObjectOperations> roMap,
			PrismContainerValue<ShadowAssociationType> associationCVal,
			PrismObject<ShadowType> subjectShadowBefore, PrismObject<ShadowType> subjectShadowAfter, 
			ModificationType modificationType, OperationResult result)
					throws SchemaException, ObjectNotFoundException, CommunicationException, SecurityViolationException, ConfigurationException {
		ResourceType resource = ctx.getResource();
		ShadowAssociationType associationType = associationCVal.asContainerable();
		QName associationName = associationType.getName();
		if (associationName == null) {
			throw new SchemaException("No name in entitlement association "+associationCVal);
		}
		RefinedAssociationDefinition assocDefType = ctx.getObjectClassDefinition().findEntitlementAssociation(associationName);
		if (assocDefType == null) {
			throw new SchemaException("No entitlement association with name "+assocDefType+" in schema of "+resource);
		}
		
		ResourceObjectAssociationDirectionType direction = assocDefType.getResourceObjectAssociationType().getDirection();
		if (direction != ResourceObjectAssociationDirectionType.OBJECT_TO_SUBJECT) {
			// Process just this one direction. The other direction was processed before
			return;
		}
		
		Collection<String> entitlementIntents = assocDefType.getIntents();
		if (entitlementIntents == null || entitlementIntents.isEmpty()) {
			throw new SchemaException("No entitlement intent specified in association "+associationCVal+" in "+resource);
		}
		ProvisioningContext entitlementCtx = ctx.spawn(ShadowKindType.ENTITLEMENT, entitlementIntents);
		RefinedObjectClassDefinition entitlementOcDef = entitlementCtx.getObjectClassDefinition();
		if (entitlementOcDef == null) {
			throw new SchemaException("No definition of entitlement intent(s) '"+entitlementIntents+"' specified in association "+associationCVal+" in "+resource);
		}
		
		QName assocAttrName = assocDefType.getResourceObjectAssociationType().getAssociationAttribute();
		if (assocAttrName == null) {
			throw new SchemaException("No association attribute defined in entitlement association in "+resource);
		}
		
		RefinedAttributeDefinition assocAttrDef = entitlementOcDef.findAttributeDefinition(assocAttrName);
		if (assocAttrDef == null) {
			throw new SchemaException("Association attribute '"+assocAttrName+"'defined in entitlement association was not found in entitlement intent(s) '"+entitlementIntents+"' in schema for "+resource);
		}
		
		ResourceAttributeContainer identifiersContainer = 
				ShadowUtil.getAttributesContainer(associationCVal, ShadowAssociationType.F_IDENTIFIERS);
		Collection<ResourceAttribute<?>> identifiers = identifiersContainer.getAttributes();
		
		ResourceObjectDiscriminator disc = new ResourceObjectDiscriminator(entitlementOcDef.getTypeName(), identifiers);
		ResourceObjectOperations operations = roMap.get(disc);
		if (operations == null) {
			operations = new ResourceObjectOperations();
			operations.setObjectClassDefinition(entitlementOcDef);
			roMap.put(disc, operations);
		}
		
		QName valueAttrName = assocDefType.getResourceObjectAssociationType().getValueAttribute();
		if (valueAttrName == null) {
			throw new SchemaException("No value attribute defined in entitlement association in "+resource);
		}

        // Which shadow would we use - shadowBefore or shadowAfter?
        //
        // If the operation is ADD or REPLACE, we use current version of the shadow (shadowAfter), because we want
        // to ensure that we add most-recent data to the subject.
        //
        // If the operation is DELETE, we have two possibilities:
        //  - if the resource provides referential integrity, the subject has already
        //    new data (because the object operation was already carried out), so we use shadowAfter
        //  - if the resource does not provide referential integrity, the subject has OLD data
        //    so we use shadowBefore
        PrismObject<ShadowType> shadow;
        if (modificationType != ModificationType.DELETE) {
            shadow = subjectShadowAfter;
        } else {
            if (assocDefType.requiresExplicitReferentialIntegrity()) {
				// we must ensure the referential integrity
				shadow = subjectShadowBefore;
            } else {
				// i.e. resource has ref integrity assured by itself
				shadow = subjectShadowAfter;
            }
        }

		ResourceAttribute<TV> valueAttr = ShadowUtil.getAttribute(shadow, valueAttrName);
		if (valueAttr == null) {
			// TODO: check schema and try to fetch full shadow if necessary
			throw new SchemaException("No value attribute "+valueAttrName+" in shadow");
		}

		PropertyDelta<TA> attributeDelta = null;
		for(Operation operation: operations.getOperations()) {
			if (operation instanceof PropertyModificationOperation) {
				PropertyModificationOperation propOp = (PropertyModificationOperation)operation;
				if (propOp.getPropertyDelta().getElementName().equals(assocAttrName)) {
					attributeDelta = propOp.getPropertyDelta();
				}
			}
		}
		if (attributeDelta == null) {
			attributeDelta = assocAttrDef.createEmptyDelta(new ItemPath(ShadowType.F_ATTRIBUTES, assocAttrName));
		}
		
		PrismProperty<TA> changedAssocAttr = PrismUtil.convertProperty(valueAttr, assocAttrDef);
		
		if (modificationType == ModificationType.ADD) {
			attributeDelta.addValuesToAdd(changedAssocAttr.getClonedValues());
		} else if (modificationType == ModificationType.DELETE) {
			attributeDelta.addValuesToDelete(changedAssocAttr.getClonedValues());
		} else if (modificationType == ModificationType.REPLACE) {
			// TODO: check if already exists
			attributeDelta.setValuesToReplace(changedAssocAttr.getClonedValues());
		}
		
		if (ResourceTypeUtil.isAvoidDuplicateValues(resource)) {
			PrismObject<ShadowType> currentObjectShadow = operations.getCurrentShadow();
			if (currentObjectShadow == null) {
				currentObjectShadow = resourceObjectReferenceResolver.fetchResourceObject(entitlementCtx, identifiers, null, result);
				operations.setCurrentShadow(currentObjectShadow);
			}
			LOGGER.info("Delta before narrow:\n{}", attributeDelta.debugDump());
			LOGGER.info("currentObjectShadow:\n{}", currentObjectShadow==null?"null":currentObjectShadow.debugDump());
			attributeDelta = ProvisioningUtil.narrowPropertyDelta(attributeDelta, currentObjectShadow, matchingRuleRegistry);
			LOGGER.info("Delta after narrow:\n{}", attributeDelta.debugDump());
		}
		
		if (attributeDelta != null && !attributeDelta.isEmpty()) {
			PropertyModificationOperation attributeModification = new PropertyModificationOperation(attributeDelta);
			operations.getOperations().add(attributeModification);
		}
	}

	
}
