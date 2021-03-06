package com.mparticle.kits;

import com.mparticle.MPEvent;
import com.mparticle.MParticle;
import com.mparticle.commerce.CommerceEvent;
import com.mparticle.commerce.Product;
import com.mparticle.commerce.Promotion;
import com.mparticle.internal.CommerceEventUtil;
import com.mparticle.internal.Constants;
import com.mparticle.internal.MPUtility;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Projection {
    static final String MATCH_TYPE_STRING = "S";
    static final String MATCH_TYPE_HASH = "H";
    static final String MATCH_TYPE_FIELD = "F";
    static final String MATCH_TYPE_STATIC = "Sta";
    final int mID;
    final int mMappingId;
    final int mModuleMappingId;
    final int mMessageType;
    final String mMatchType;
    final String mEventName;
    final String mAttributeKey;
    final String mAttributeValue;
    final int mMaxCustomParams;
    final boolean mAppendUnmappedAsIs;
    final boolean mIsDefault;
    final String mProjectedEventName;
    //final List<AttributeProjection> mAttributeProjectionList;
    final List<AttributeMap> mStaticAttributeMapList;
    final List<AttributeMap> mRequiredAttributeMapList;
    final int mEventHash;
    private final boolean isSelectorLast;
    AttributeMap nameFieldProjection = null;
    String commerceMatchProperty = null;
    String commerceMatchPropertyName = null;
    String commerceMatchPropertyValue = null;
    final int mOutboundMessageType;
    public final static String PROPERTY_LOCATION_EVENT_FIELD = "EventField";
    public final static String PROPERTY_LOCATION_EVENT_ATTRIBUTE = "EventAttribute";
    public final static String PROPERTY_LOCATION_PRODUCT_FIELD = "ProductField";
    public final static String PROPERTY_LOCATION_PRODUCT_ATTRIBUTE = "ProductAttribute";
    public final static String PROPERTY_LOCATION_PROMOTION_FIELD = "PromotionField";

    public Projection(JSONObject projectionJson) throws JSONException {
        mID = projectionJson.getInt("id");
        mMappingId = projectionJson.optInt("pmid");
        mModuleMappingId = projectionJson.optInt("pmmid");
        if (projectionJson.has("match")) {
            JSONObject match = projectionJson.getJSONObject("match");
            mMessageType = match.optInt("message_type");
            mMatchType = match.optString("event_match_type", "String");
            commerceMatchProperty = match.optString("property", PROPERTY_LOCATION_EVENT_ATTRIBUTE);
            commerceMatchPropertyName = match.optString("property_name", null);
            commerceMatchPropertyValue = match.optString("property_value", null);

            if (mMatchType.startsWith(MATCH_TYPE_HASH)) {
                mEventHash = Integer.parseInt(match.optString("event"));
                mAttributeKey = null;
                mAttributeValue = null;
                mEventName = null;
            } else {
                mEventHash = 0;
                mEventName = match.optString("event");
                mAttributeKey = match.optString("attribute_key");
                mAttributeValue = match.optString("attribute_value");
            }

        } else {
            mEventHash = 0;
            mMessageType = -1;
            mMatchType = "String";
            mEventName = null;
            mAttributeKey = null;
            mAttributeValue = null;
        }
        if (projectionJson.has("behavior")) {
            JSONObject behaviors = projectionJson.getJSONObject("behavior");
            mMaxCustomParams = behaviors.optInt("max_custom_params", Integer.MAX_VALUE);
            mAppendUnmappedAsIs = behaviors.optBoolean("append_unmapped_as_is");
            mIsDefault = behaviors.optBoolean("is_default");
            isSelectorLast = behaviors.optString("selector", "foreach").equalsIgnoreCase("last");
        } else {
            mMaxCustomParams = Integer.MAX_VALUE;
            mAppendUnmappedAsIs = false;
            mIsDefault = false;
            isSelectorLast = false;
        }

        if (projectionJson.has("action")) {
            JSONObject action = projectionJson.getJSONObject("action");
            mOutboundMessageType = action.optInt("outbound_message_type", 4);
            mProjectedEventName = action.optString("projected_event_name");
            if (action.has("attribute_maps")) {
                mRequiredAttributeMapList = new LinkedList<AttributeMap>();
                mStaticAttributeMapList = new LinkedList<AttributeMap>();
                JSONArray attributeMapList = action.getJSONArray("attribute_maps");

                for (int i = 0; i < attributeMapList.length(); i++) {
                    AttributeMap attProjection = new AttributeMap(attributeMapList.getJSONObject(i));
                    if (attProjection.mMatchType.startsWith(MATCH_TYPE_STATIC)) {
                        mStaticAttributeMapList.add(attProjection);
                    } else if (attProjection.mMatchType.startsWith(MATCH_TYPE_FIELD)) {
                        nameFieldProjection = attProjection;
                    } else {
                        mRequiredAttributeMapList.add(attProjection);
                    }
                }
                Collections.sort(mRequiredAttributeMapList, new Comparator<AttributeMap>() {
                    @Override
                    public int compare(AttributeMap lhs, AttributeMap rhs) {
                        if (lhs.mIsRequired == rhs.mIsRequired) {
                            return 0;
                        }else if (lhs.mIsRequired && !rhs.mIsRequired) {
                            return -1;
                        }else {
                            return 1;
                        }
                    }
                });
            } else {
                mRequiredAttributeMapList = null;
                mStaticAttributeMapList = null;
            }
        } else {
            mRequiredAttributeMapList = null;
            mStaticAttributeMapList = null;
            mProjectedEventName = null;
            mOutboundMessageType = 4;
        }
    }

    public boolean isDefault() {
        return mIsDefault;
    }

    /**
     * This is an optimization - check the basic stuff to see if we have a match before actually trying to do the projection
     */
    public boolean isMatch(EventWrapper eventWrapper) {
        if (eventWrapper.getMessageType() != mMessageType) {
            return false;
        }

        if (mIsDefault) {
            return true;
        }
        if (eventWrapper instanceof EventWrapper.MPEventWrapper) {
            if (matchAppEvent((EventWrapper.MPEventWrapper)eventWrapper)) {
                return true;
            }
        }else{
            CommerceEvent commerceEvent = matchCommerceEvent((EventWrapper.CommerceEventWrapper)eventWrapper);
            if (commerceEvent != null) {
                ((EventWrapper.CommerceEventWrapper) eventWrapper).setEvent(commerceEvent);
                return true;
            }
        }
        return false;
    }
    private CommerceEvent matchCommerceEvent(EventWrapper.CommerceEventWrapper eventWrapper) {
        CommerceEvent commerceEvent = eventWrapper.getEvent();
        if (commerceEvent == null) {
            return null;
        }
        if (commerceMatchProperty != null && commerceMatchPropertyName != null) {
            if (commerceMatchProperty.equalsIgnoreCase(PROPERTY_LOCATION_EVENT_FIELD)) {
                 if (matchCommerceFields(commerceEvent)) {
                     return commerceEvent;
                 }else {
                     return null;
                 }
            } else if (commerceMatchProperty.equalsIgnoreCase(PROPERTY_LOCATION_EVENT_ATTRIBUTE)) {
                 if (matchCommerceAttributes(commerceEvent)) {
                     return commerceEvent;
                 }else{
                     return null;
                 }
            } else if (commerceMatchProperty.equalsIgnoreCase(PROPERTY_LOCATION_PRODUCT_FIELD)) {
                return matchProductFields(commerceEvent);
            } else if (commerceMatchProperty.equalsIgnoreCase(PROPERTY_LOCATION_PRODUCT_ATTRIBUTE)) {
                return matchProductAttributes(commerceEvent);
            } else if (commerceMatchProperty.equalsIgnoreCase(PROPERTY_LOCATION_PROMOTION_FIELD)) {
                return matchPromotionFields(commerceEvent);
            }
        }
        if (mMatchType.startsWith(MATCH_TYPE_HASH) && eventWrapper.getEventHash() == mEventHash) {
            return commerceEvent;
        }
        return null;
    }
    private boolean matchAppEvent(EventWrapper.MPEventWrapper eventWrapper) {
        MPEvent event = eventWrapper.getEvent();
        if (event == null) {
            return false;
        }
        if (nameFieldProjection != null && nameFieldProjection.mIsRequired) {
            if (!nameFieldProjection.matchesDataType(event.getEventName())) {
                return false;
            }
        }
        if (mMatchType.startsWith(MATCH_TYPE_HASH) && eventWrapper.getEventHash() == mEventHash) {
            return true;
        }else if (mMatchType.startsWith(MATCH_TYPE_STRING) &&
                event.getEventName().equalsIgnoreCase(mEventName) &&
                event.getInfo() != null &&
                mAttributeValue.equalsIgnoreCase(event.getInfo().get(mAttributeKey))) {
            return true;
        }else {
            return false;
        }
    }

    private boolean matchCommerceFields(CommerceEvent event) {
        int hash = Integer.parseInt(commerceMatchPropertyName);
        Map<String, String> fields = new HashMap<String, String>();
        CommerceEventUtil.extractActionAttributes(event, fields);
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            int fieldHash = MPUtility.mpHash(CommerceEventUtil.getEventType(event) + entry.getKey());
            if (fieldHash == hash) {
                return entry.getValue().equalsIgnoreCase(commerceMatchPropertyValue);
            }

        }
        return false;
    }

    private CommerceEvent matchPromotionFields(CommerceEvent event) {
        int hash = Integer.parseInt(commerceMatchPropertyName);
        List<Promotion> promotionList = event.getPromotions();
        if (promotionList == null || promotionList.size() == 0) {
            return null;
        }
        List<Promotion> matchedPromotions = new LinkedList<Promotion>();
        Map<String, String> promotionFields = new HashMap<String, String>();
        for (Promotion promotion : promotionList) {
            promotionFields.clear();
            CommerceEventUtil.extractPromotionAttributes(promotion, promotionFields);
            if (promotionFields != null) {
                for (Map.Entry<String, String> entry : promotionFields.entrySet()) {
                    int attributeHash = MPUtility.mpHash(CommerceEventUtil.getEventType(event) + entry.getKey());
                    if (attributeHash == hash) {
                        if (entry.getValue().equalsIgnoreCase(commerceMatchPropertyValue)) {
                            matchedPromotions.add(promotion);
                        }
                    }
                }
            }
        }
        if (matchedPromotions.size() == 0) {
            return null;
        } else if (matchedPromotions.size() != promotionList.size()) {
            return new CommerceEvent.Builder(event).promotions(matchedPromotions).build();
        } else {
            return event;
        }
    }

    private CommerceEvent matchProductFields(CommerceEvent event) {
        int hash = Integer.parseInt(commerceMatchPropertyName);
        int type = CommerceEventUtil.getEventType(event);
        List<Product> productList = event.getProducts();
        if (productList == null || productList.size() == 0) {
            return null;
        }
        List<Product> matchedProducts = new LinkedList<Product>();
        Map<String, String> productFields = new HashMap<String, String>();
        for (Product product : productList) {
            productFields.clear();
            CommerceEventUtil.extractProductFields(product, productFields);
            if (productFields != null) {
                for (Map.Entry<String, String> entry : productFields.entrySet()) {
                    int attributeHash = MPUtility.mpHash(type + entry.getKey());
                    if (attributeHash == hash) {
                        if (entry.getValue().equalsIgnoreCase(commerceMatchPropertyValue)) {
                            matchedProducts.add(product);
                        }
                    }
                }
            }
        }
        if (matchedProducts.size() == 0) {
            return null;
        } else if (matchedProducts.size() != productList.size()) {
            return new CommerceEvent.Builder(event).products(matchedProducts).build();
        } else {
            return event;
        }
    }

    private CommerceEvent matchProductAttributes(CommerceEvent event) {
        int hash = Integer.parseInt(commerceMatchPropertyName);
        List<Product> productList = event.getProducts();
        if (productList == null || productList.size() == 0) {
            return null;
        }
        List<Product> matchedProducts = new LinkedList<Product>();
        for (Product product : productList) {
            Map<String, String> attributes = product.getCustomAttributes();
            if (attributes != null) {
                for (Map.Entry<String, String> entry : attributes.entrySet()) {
                    int attributeHash = MPUtility.mpHash(CommerceEventUtil.getEventType(event) + entry.getKey());
                    if (attributeHash == hash) {
                        if (entry.getValue().equalsIgnoreCase(commerceMatchPropertyValue)) {
                            matchedProducts.add(product);
                        }
                    }
                }
            }
        }
        if (matchedProducts.size() == 0) {
            return null;
        } else if (matchedProducts.size() != productList.size()) {
            return new CommerceEvent.Builder(event).products(matchedProducts).build();
        } else {
            return event;
        }
    }

    private boolean matchCommerceAttributes(CommerceEvent event) {
        Map<String, String> attributes = event.getCustomAttributes();
        if (attributes == null || attributes.size() < 1) {
            return false;
        }
        int hash = Integer.parseInt(commerceMatchPropertyName);
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            int attributeHash = MPUtility.mpHash(CommerceEventUtil.getEventType(event) + entry.getKey());
            if (attributeHash == hash) {
                return entry.getValue().equalsIgnoreCase(commerceMatchPropertyValue);
            }
        }
        return false;
    }

    private ProjectionResult projectMPEvent(MPEvent event) {
        EventWrapper.MPEventWrapper eventWrapper = new EventWrapper.MPEventWrapper(event);
        String eventName = MPUtility.isEmpty(mProjectedEventName) ? event.getEventName() : mProjectedEventName;
        MPEvent.Builder builder = new MPEvent.Builder(event);
        builder.eventName(eventName);
        builder.info(null);

        Map<String, String> newAttributes = new HashMap<String, String>();
        Set<String> usedAttributes = new HashSet<String>();
        if (!mapAttributes(mRequiredAttributeMapList, eventWrapper, newAttributes, usedAttributes, null, null)) {
            return null;
        }
        if (mStaticAttributeMapList != null) {
            for (int i = 0; i < mStaticAttributeMapList.size(); i++) {
                AttributeMap attProjection = mStaticAttributeMapList.get(i);
                newAttributes.put(attProjection.mProjectedAttributeName, attProjection.mValue);
                usedAttributes.add(attProjection.mValue);
            }
        }
        if (nameFieldProjection != null) {
            newAttributes.put(nameFieldProjection.mProjectedAttributeName, event.getEventName());
        }
        if (mAppendUnmappedAsIs && mMaxCustomParams > 0 && newAttributes.size() < mMaxCustomParams) {
            Map<String, String> originalAttributes;
            if (event.getInfo() != null) {
                originalAttributes = new HashMap<String, String>(event.getInfo());
            } else {
                originalAttributes = new HashMap<String, String>();
            }
            List<String> sortedKeys = new ArrayList(originalAttributes.keySet());
            Collections.sort(sortedKeys);
            for (int i = 0; (i < sortedKeys.size() && newAttributes.size() < mMaxCustomParams); i++) {
                String key = sortedKeys.get(i);
                if (!usedAttributes.contains(key) && !newAttributes.containsKey(key)) {
                    newAttributes.put(key, originalAttributes.get(key));
                }
            }
        }
        builder.info(newAttributes);
        return new ProjectionResult(builder.build(), mID);
    }
    public List<ProjectionResult> project(EventWrapper.CommerceEventWrapper commerceEventWrapper) {
        List<ProjectionResult> projectionResults = new LinkedList<ProjectionResult>();
        CommerceEvent commerceEvent = commerceEventWrapper.getEvent();
        int eventType = CommerceEventUtil.getEventType(commerceEvent);
        //TODO Impression projections are not supported for now
        if (eventType == Constants.Commerce.EVENT_TYPE_IMPRESSION) {
            return null;
        }else if (eventType == Constants.Commerce.EVENT_TYPE_PROMOTION_CLICK || eventType == Constants.Commerce.EVENT_TYPE_PROMOTION_VIEW) {
            List<Promotion> promotions = commerceEvent.getPromotions();
            if (promotions == null || promotions.size() == 0){
                ProjectionResult projectionResult = projectCommerceEvent(commerceEventWrapper, null, null);
                if (projectionResult != null) {
                    projectionResults.add(projectionResult);
                }
            }else{
                if (isSelectorLast) {
                    Promotion promotion = promotions.get(promotions.size() - 1);
                    ProjectionResult projectionResult = projectCommerceEvent(commerceEventWrapper, null, promotion);
                    if (projectionResult != null) {
                        projectionResults.add(projectionResult);
                    }
                }else{
                    for (int i = 0; i < promotions.size(); i++) {
                        ProjectionResult projectionResult = projectCommerceEvent(commerceEventWrapper, null, promotions.get(i));
                        if (projectionResult != null) {
                            if (projectionResult.getCommerceEvent() != null) {
                                CommerceEvent foreachCommerceEvent = new CommerceEvent.Builder(projectionResult.getCommerceEvent())
                                        .promotions(null)
                                        .addPromotion(promotions.get(i))
                                        .build();
                                projectionResult.mCommerceEvent = foreachCommerceEvent;
                            }
                            projectionResults.add(projectionResult);
                        }
                    }

                }
            }
        }else {
            List<Product> products = commerceEvent.getProducts();
            if (isSelectorLast){
                Product product = null;
                if (products != null && products.size() > 0){
                    product = products.get(products.size() - 1);
                }
                ProjectionResult projectionResult = projectCommerceEvent(commerceEventWrapper, product, null);
                if (projectionResult != null) {
                    projectionResults.add(projectionResult);
                }
            }else {
                if (products != null) {
                    for (int i = 0; i < products.size(); i++) {
                        ProjectionResult projectionResult = projectCommerceEvent(commerceEventWrapper, products.get(i), null);
                        if (projectionResult != null) {
                            if (projectionResult.getCommerceEvent() != null) {
                                CommerceEvent foreachCommerceEvent = new CommerceEvent.Builder(projectionResult.getCommerceEvent())
                                        .products(null)
                                        .addProduct(products.get(i))
                                        .build();

                                projectionResult.mCommerceEvent = foreachCommerceEvent;
                            }
                            projectionResults.add(projectionResult);
                        }
                    }
                }
            }
        }


        if (projectionResults.size() > 0) {
            return projectionResults;
        }else{
            return null;
        }
    }
    public List<ProjectionResult> project(EventWrapper.MPEventWrapper event) {
        List<ProjectionResult> projectionResults = new LinkedList<ProjectionResult>();

        ProjectionResult projectionResult = projectMPEvent(event.getEvent());
        if (projectionResult != null) {
            projectionResults.add(projectionResult);
        }

        if (projectionResults.size() > 0) {
            return projectionResults;
        }else{
            return null;
        }
    }

    private boolean mapAttributes(List<AttributeMap> projectionList, EventWrapper eventWrapper, Map<String, String> mappedAttributes, Set<String> usedAttributes, Product product, Promotion promotion) {
        if (projectionList != null) {
            for (int i = 0; i < projectionList.size(); i++) {
                AttributeMap attProjection = projectionList.get(i);
                Map.Entry<String, String> entry = null;
                if (attProjection.mMatchType.startsWith(MATCH_TYPE_STRING)) {
                    entry = eventWrapper.findAttribute(attProjection.mLocation, attProjection.mValue, product, promotion);
                } else if (attProjection.mMatchType.startsWith(MATCH_TYPE_HASH)) {
                    entry = eventWrapper.findAttribute(attProjection.mLocation, Integer.parseInt(attProjection.mValue), product, promotion);
                }
                if (entry == null || !attProjection.matchesDataType(entry.getValue())) {
                    if (attProjection.mIsRequired) {
                        return false;
                    }else {
                        continue;
                    }
                }

                String key = entry.getKey();
                if (!MPUtility.isEmpty(attProjection.mProjectedAttributeName)) {
                    key = attProjection.mProjectedAttributeName;
                }
                mappedAttributes.put(key, entry.getValue());
                usedAttributes.add(entry.getKey());
            }
        }
        return true;
    }

    private ProjectionResult projectCommerceEvent(EventWrapper.CommerceEventWrapper eventWrapper, Product product, Promotion promotion) {
        Map<String, String> mappedAttributes = new HashMap<String, String>();
        Set<String> usedAttributes = new HashSet<String>();
        if (!mapAttributes(mRequiredAttributeMapList, eventWrapper, mappedAttributes, usedAttributes, product, promotion)) {
            return null;
        }
        if (mStaticAttributeMapList != null) {
            for (int i = 0; i < mStaticAttributeMapList.size(); i++) {
                AttributeMap attProjection = mStaticAttributeMapList.get(i);
                mappedAttributes.put(attProjection.mProjectedAttributeName, attProjection.mValue);
                usedAttributes.add(attProjection.mValue);
            }
        }
        if (mAppendUnmappedAsIs && mMaxCustomParams > 0 && mappedAttributes.size() < mMaxCustomParams) {
            CommerceEvent event = eventWrapper.getEvent();
            Map<String, String> originalAttributes;
            if (event.getCustomAttributes() != null) {
                originalAttributes = new HashMap<String, String>(event.getCustomAttributes());
            } else {
                originalAttributes = new HashMap<String, String>();
            }
            List<String> sortedKeys = new ArrayList(originalAttributes.keySet());
            Collections.sort(sortedKeys);
            for (int i = 0; (i < sortedKeys.size() && mappedAttributes.size() < mMaxCustomParams); i++) {
                String key = sortedKeys.get(i);
                if (!usedAttributes.contains(key) && !mappedAttributes.containsKey(key)) {
                    mappedAttributes.put(key, originalAttributes.get(key));
                }
            }
        }
        if (mOutboundMessageType == 16){
            return new ProjectionResult(
                    new CommerceEvent.Builder(eventWrapper.getEvent())
                            .internalEventName(mProjectedEventName)
                            .customAttributes(mappedAttributes)
                            .build(),
                    mID
            );
        }else {
            return new ProjectionResult(
                    new MPEvent.Builder(mProjectedEventName, MParticle.EventType.Transaction)
                            .info(mappedAttributes)
                            .build(),
                    mID
            );
        }
    }

    public int getMessageType() {
        return mMessageType;
    }

    static class AttributeMap {
        final String mProjectedAttributeName;
        final String mValue;
        final int mDataType;
        final String mMatchType;
        final boolean mIsRequired;
        final String mLocation;

        public AttributeMap(JSONObject attributeMapJson) {
            mProjectedAttributeName = attributeMapJson.optString("projected_attribute_name");
            mMatchType = attributeMapJson.optString("match_type", "String");
            mValue = attributeMapJson.optString("value");
            mDataType = attributeMapJson.optInt("data_type", 1);
            mIsRequired = attributeMapJson.optBoolean("is_required");
            mLocation = attributeMapJson.optString("property", PROPERTY_LOCATION_EVENT_ATTRIBUTE);
        }

        @Override
        public boolean equals(Object o) {
            return super.equals(o) || this.toString().equals(o.toString());
        }

        @Override
        public String toString() {
            return "projected_attribute_name: " + mProjectedAttributeName + "\n" +
                    "match_type: " + mMatchType + "\n" +
                    "value: " + mValue + "\n" +
                    "data_type: " + mDataType + "\n" +
                    "is_required: " + mIsRequired;
        }

        public boolean matchesDataType(String value) {
            switch (mDataType) {
                case 1:
                    return true;
                case 2:
                    try {
                        Integer.parseInt(value);
                        return true;
                    } catch (NumberFormatException nfe) {
                        return false;
                    }
                case 3:
                    return Boolean.parseBoolean(value) || "false".equalsIgnoreCase(value);
                case 4:
                    try {
                        Double.parseDouble(value);
                        return true;
                    } catch (NumberFormatException nfe) {
                        return false;
                    }
                default:
                    return false;
            }
        }
    }

    public static class ProjectionResult {
        private final MPEvent mEvent;
        private final int mProjectionId;
        private CommerceEvent mCommerceEvent;

        public ProjectionResult(MPEvent event, int projectionId) {
            mEvent = event;
            mCommerceEvent = null;
            mProjectionId = projectionId;
        }

        public ProjectionResult(CommerceEvent commerceEvent, int projectionId) {
            mCommerceEvent = commerceEvent;
            mEvent = null;
            mProjectionId = projectionId;
        }

        public int getProjectionId() {
            return mProjectionId;
        }

        public MPEvent getMPEvent() {
            return mEvent;
        }

        public CommerceEvent getCommerceEvent() {
            return mCommerceEvent;
        }
    }
}
