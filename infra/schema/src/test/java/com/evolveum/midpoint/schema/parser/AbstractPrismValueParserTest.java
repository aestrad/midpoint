/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.schema.parser;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.equivalence.EquivalenceStrategy;
import com.evolveum.midpoint.prism.path.*;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.xnode.XNode;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

import javax.xml.namespace.QName;
import java.util.Collection;

import static org.testng.AssertJUnit.*;

public abstract class AbstractPrismValueParserTest<T extends PrismValue> extends AbstractParserTest {

    protected void assertPropertyDefinition(PrismContainer<?> container, String propName, QName xsdType, int minOccurs,
            int maxOccurs) {
        ItemName propItemName = new ItemName(SchemaConstantsGenerated.NS_COMMON, propName);
        PrismAsserts.assertPropertyDefinition(container, propItemName, xsdType, minOccurs, maxOccurs);
    }

    protected void assertPropertyValue(PrismContainer<?> container, String propName, Object propValue) {
        ItemName propItemName = new ItemName(SchemaConstantsGenerated.NS_COMMON, propName);
        PrismAsserts.assertPropertyValue(container, propItemName, propValue);
    }

    protected <T> void assertPropertyValues(PrismContainer<?> container, String propName, T... expectedValues) {
        ItemName propItemName = new ItemName(SchemaConstantsGenerated.NS_COMMON, propName);
        PrismAsserts.assertPropertyValue(container, propItemName, expectedValues);
    }

    protected void assertContainerDefinition(PrismContainer container, String contName, QName xsdType, int minOccurs,
            int maxOccurs) {
        ItemName qName = new ItemName(SchemaConstantsGenerated.NS_COMMON, contName);
        PrismAsserts.assertDefinition(container.getDefinition(), qName, xsdType, minOccurs, maxOccurs);
    }

    // partly covers the same functionality as item.assertDefinitions (TODO clean this)
    protected void assertDefinitions(Visitable value) {
        value.accept(v -> {
            if (v instanceof Item) {
                Item item = (Item) v;
                String label = item.getPath() + ": " + v;
                //System.out.println("Checking " + label);
                if (item.getDefinition() == null) {
                    assertTrue("No definition in " + label, isDynamic(item.getPath()));
                } else {
                    assertNotNull("No prism context in definition of " + label, item.getDefinition().getPrismContext());
                }
            } else if (v instanceof PrismContainerValue) {
                PrismContainerValue pcv = (PrismContainerValue) v;
                String label = pcv.getPath() + ": " + v;
                //System.out.println("Checking " + label);
                if (pcv.getComplexTypeDefinition() == null) {
                    fail("No complex type definition in " + label);
                } else {
                    assertNotNull("No prism context in definition of " + label, pcv.getComplexTypeDefinition().getPrismContext());
                }
            }
        });
    }

    protected void assertResolvableRawValues(Visitable value) {
        value.accept(v -> {
            // TODO in RawTypes in beans?
            if (v instanceof PrismPropertyValue) {
                PrismPropertyValue ppv = (PrismPropertyValue) v;
                XNode raw = ppv.getRawElement();
                if (raw != null && raw.getTypeQName() != null) {
                    String label = ppv.getPath() + ": " + v;
                    fail("Resolvable raw value of " + raw + " in " + label + " (type: " + raw.getTypeQName() + ")");
                }
            }
        });
    }

    protected void assertPrismContext(Visitable value) {
        value.accept(v -> {
            if (v instanceof Item) {
                Item item = (Item) v;
                String label = item.getPath() + ": " + v;
                assertNotNull("No prism context in " + label, item.getPrismContextLocal());
            } else if (v instanceof PrismContainerValue) {
                PrismContainerValue pcv = (PrismContainerValue) v;
                String label = pcv.getPath() + ": " + v;
                assertNotNull("No prism context in " + label, pcv.getPrismContextLocal());
            }
        });
    }

    private boolean isDynamic(ItemPath path) {
        for (Object segment : path.getSegments()) {
            if (ItemPath.isName(segment)) {
                QName name = ItemPath.toName(segment);
                if (QNameUtil.match(name, ShadowType.F_ATTRIBUTES) || QNameUtil.match(name, ObjectType.F_EXTENSION)) {
                    return true;
                }
            }
        }
        return false;
    }

    @FunctionalInterface
    public interface ParsingFunction<V> {
        V apply(PrismParser prismParser) throws Exception;
    }

    @FunctionalInterface
    public interface SerializingFunction<V> {
        String apply(V value) throws Exception;
    }

    protected void process(String desc, ParsingFunction<T> parser, SerializingFunction<T> serializer, String serId) throws Exception {
        PrismContext prismContext = getPrismContext();

        System.out.println("================== Starting test for '" + desc + "' (serializer: " + serId + ") ==================");

        try {

        T value = parser.apply(prismContext.parserFor(getFile()));
        assertResolvableRawValues(value);        // should be right here, before any getValue is called (TODO reconsider)

        System.out.println("Parsed value: " + desc);
        System.out.println(value.debugDump());

        assertPrismValue(value);

        if (serializer != null) {

            String serialized = serializer.apply(value);
            System.out.println("Serialized:\n" + serialized);

            T reparsed = parser.apply(prismContext.parserFor(serialized));
            assertResolvableRawValues(reparsed);        // should be right here, before any getValue is called (TODO reconsider)

            System.out.println("Reparsed: " + desc);
            System.out.println(reparsed.debugDump());

            assertPrismValue(reparsed);

            Collection<? extends ItemDelta> deltas = value.diff(reparsed, EquivalenceStrategy.DATA);
            assertTrue("Deltas not empty", deltas.isEmpty());

            assertTrue("Values not equal", value.equals(reparsed, EquivalenceStrategy.DATA));
        }

        } catch (SchemaException e) {
            throw new SchemaException("Error processing file "+getFile().getPath()+": " + e.getMessage(), e);
        }
    }

    protected abstract void assertPrismValue(T value) throws SchemaException;

    protected boolean isContainer() {
        return false;
    }

}
