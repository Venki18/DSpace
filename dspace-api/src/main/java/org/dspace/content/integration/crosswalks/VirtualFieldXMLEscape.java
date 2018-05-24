/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringEscapeUtils;
import org.dspace.content.IMetadataValue;
import org.dspace.content.Item;

/**
 * Implementazione del virtual field processing per effettuare l'escape xml dei
 * caratteri speciali
 *
 * @author bollini
 */
public class VirtualFieldXMLEscape implements VirtualFieldDisseminator,
                                              VirtualFieldIngester {
    public String[] getMetadata(Item item, Map<String, String> fieldCache,
                                String fieldName) {
        // Check to see if the virtual field is already in the cache
        // - processing is quite intensive, so we generate all the values on
        // first request
        if (fieldCache.containsKey(fieldName)) {
            return fieldCache.get(fieldName).split("\\|");
        }

        String[] virtualFieldName = fieldName.split("\\.", 3);
        List<IMetadataValue> dcvs = item.getMetadataValueInDCFormat(virtualFieldName[2]);
        StringBuffer out = null;
        if (dcvs != null && dcvs.size() > 0) {
            out = new StringBuffer();
            for (IMetadataValue dc : dcvs) {
                out.append(StringEscapeUtils.escapeXml(dc.getValue())).append("|");
            }
            if (out != null && out.length() > 0) {
                String result = out.substring(0, out.length() - 1);
                fieldCache.put(fieldName, result);
                return result.split("\\|");
            }
        }
        return null;
    }

    public boolean addMetadata(Item item, Map<String, String> fieldCache,
                               String fieldName, String value) {
        // NOOP - we won't add any metadata yet, we'll pick it up when we
        // finalise the item
        return true;
    }

    public boolean finalizeItem(Item item, Map<String, String> fieldCache) {
        return false;
    }
}
