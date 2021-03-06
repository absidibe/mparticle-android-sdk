package com.mparticle.internal;

import com.mparticle.MPEvent;
import com.mparticle.MParticle;
import com.mparticle.commerce.CommerceEvent;
import com.mparticle.commerce.Impression;
import com.mparticle.commerce.Product;
import com.mparticle.commerce.Promotion;
import com.mparticle.commerce.TransactionAttributes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CommerceEventUtil {

    private static final String PLUSONE_NAME = "eCommerce - %s - Total";
    private static final String ITEM_NAME = "eCommerce - %s - Item";
    private static final String IMPRESSION_NAME = "eCommerce - Impression - Item";

    public static List<MPEvent> expand(CommerceEvent event) {
        List<MPEvent> eventList = expandProductAction(event);
        eventList.addAll(expandPromotionAction(event));
        eventList.addAll(expandProductImpression(event));
        return eventList;
    }

    public static List<MPEvent> expandProductAction(CommerceEvent event) {
        List<MPEvent> events = new LinkedList<MPEvent>();
        String productAction = event.getProductAction();
        if (productAction == null) {
            return events;
        }
        if (productAction.equalsIgnoreCase(Product.PURCHASE) || productAction.equalsIgnoreCase(Product.REFUND)) {
            MPEvent.Builder plusOne = new MPEvent.Builder(String.format(PLUSONE_NAME, event.getProductAction()), MParticle.EventType.Transaction);
            //set all product action fields to attributes
            Map<String, String> attributes = new HashMap<String, String>();
            //start with the custom attributes then overwrite with action fields
            if (event.getCustomAttributes() != null) {
                attributes.putAll(event.getCustomAttributes());
            }
            extractActionAttributes(event, attributes);
            events.add(plusOne.info(attributes).build());
        }
        List<Product> products = event.getProducts();
        if (products != null) {
            for (int i = 0; i < products.size(); i++) {
                MPEvent.Builder itemEvent = new MPEvent.Builder(String.format(ITEM_NAME, productAction), MParticle.EventType.Transaction);
                Map<String, String> attributes = new HashMap<String, String>();
                extractProductAttributes(products.get(i), attributes);
                extractTransactionId(event, attributes);
                events.add(itemEvent.info(attributes).build());
            }
        }
        return events;
    }

    public static void extractProductFields(Product product, Map<String, String> attributes) {
        if (product != null) {
            if (!MPUtility.isEmpty(product.getCouponCode())) {
                attributes.put(Constants.Commerce.ATT_PRODUCT_COUPON_CODE, product.getCouponCode());
            }
            if (!MPUtility.isEmpty(product.getBrand())) {
                attributes.put(Constants.Commerce.ATT_PRODUCT_BRAND, product.getBrand());
            }
            if (!MPUtility.isEmpty(product.getCategory())) {
                attributes.put(Constants.Commerce.ATT_PRODUCT_CATEGORY, product.getCategory());
            }
            if (!MPUtility.isEmpty(product.getName())) {
                attributes.put(Constants.Commerce.ATT_PRODUCT_NAME, product.getName());
            }
            if (!MPUtility.isEmpty(product.getSku())) {
                attributes.put(Constants.Commerce.ATT_PRODUCT_ID, product.getSku());
            }
            if (!MPUtility.isEmpty(product.getVariant())) {
                attributes.put(Constants.Commerce.ATT_PRODUCT_VARIANT, product.getVariant());
            }
            if (product.getPosition() != null) {
                attributes.put(Constants.Commerce.ATT_PRODUCT_POSITION, Integer.toString(product.getPosition()));
            }
            attributes.put(Constants.Commerce.ATT_PRODUCT_PRICE, Double.toString(product.getUnitPrice()));
            attributes.put(Constants.Commerce.ATT_PRODUCT_QUANTITY, Double.toString(product.getQuantity()));
            attributes.put(Constants.Commerce.ATT_PRODUCT_TOTAL_AMOUNT, Double.toString(product.getTotalAmount()));
        }
    }

    public static void extractProductAttributes(Product product, Map<String, String> attributes) {
        if (product != null) {
            if (product.getCustomAttributes() != null) {
                attributes.putAll(product.getCustomAttributes());
            }
        }
    }

    private static void extractTransactionId(CommerceEvent event, Map<String, String> attributes) {
        if (event != null && event.getTransactionAttributes() != null && !MPUtility.isEmpty(event.getTransactionAttributes().getId())) {
            attributes.put(Constants.Commerce.ATT_TRANSACTION_ID, event.getTransactionAttributes().getId());
        }
    }

    public static void extractActionAttributes(CommerceEvent event, Map<String, String> attributes) {
        extractTransactionAttributes(event, attributes);
        extractTransactionId(event, attributes);
        String currency = event.getCurrency();
        if (MPUtility.isEmpty(currency)) {
            currency = Constants.Commerce.DEFAULT_CURRENCY_CODE;
        }
        attributes.put(Constants.Commerce.ATT_ACTION_CURRENCY_CODE, currency);
        String checkoutOptions = event.getCheckoutOptions();
        if (!MPUtility.isEmpty(checkoutOptions)) {
            attributes.put(Constants.Commerce.ATT_ACTION_CHECKOUT_OPTIONS, checkoutOptions);
        }
        if (event.getCheckoutStep() != null) {
            attributes.put(Constants.Commerce.ATT_ACTION_CHECKOUT_STEP, Integer.toString(event.getCheckoutStep()));
        }
        if (!MPUtility.isEmpty(event.getProductListSource())) {
            attributes.put(Constants.Commerce.ATT_ACTION_PRODUCT_LIST_SOURCE, event.getProductListSource());
        }
        if (!MPUtility.isEmpty(event.getProductListName())) {
            attributes.put(Constants.Commerce.ATT_ACTION_PRODUCT_ACTION_LIST, event.getProductListName());
        }
    }

    public static Map<String, String> extractTransactionAttributes(CommerceEvent event, Map<String, String> attributes) {
        if (event == null || event.getTransactionAttributes() == null) {
            return attributes;
        }
        TransactionAttributes transactionAttributes = event.getTransactionAttributes();
        extractTransactionId(event, attributes);
        if (!MPUtility.isEmpty(transactionAttributes.getAffiliation())) {
            attributes.put(Constants.Commerce.ATT_AFFILIATION, transactionAttributes.getAffiliation());
        }
        if (!MPUtility.isEmpty(transactionAttributes.getCouponCode())) {
            attributes.put(Constants.Commerce.ATT_TRANSACTION_COUPON_CODE, transactionAttributes.getCouponCode());
        }
        if (transactionAttributes.getRevenue() != null) {
            attributes.put(Constants.Commerce.ATT_TOTAL, Double.toString(transactionAttributes.getRevenue()));
        }
        if (transactionAttributes.getShipping() != null) {
            attributes.put(Constants.Commerce.ATT_SHIPPING, Double.toString(transactionAttributes.getShipping()));
        }
        if (transactionAttributes.getTax() != null) {
            attributes.put(Constants.Commerce.ATT_TAX, Double.toString(transactionAttributes.getTax()));
        }

        return attributes;
    }

    public static List<MPEvent> expandPromotionAction(CommerceEvent event) {
        List<MPEvent> events = new LinkedList<MPEvent>();
        String promotionAction = event.getPromotionAction();
        if (promotionAction == null) {
            return events;
        }
        List<Promotion> promotions = event.getPromotions();
        if (promotions != null) {
            for (int i = 0; i < promotions.size(); i++) {
                MPEvent.Builder itemEvent = new MPEvent.Builder(String.format(ITEM_NAME, promotionAction), MParticle.EventType.Transaction);
                Map<String, String> attributes = new HashMap<String, String>();
                if (event.getCustomAttributes() != null) {
                    attributes.putAll(event.getCustomAttributes());
                }
                extractPromotionAttributes(promotions.get(i), attributes);
                events.add(itemEvent.info(attributes).build());
            }
        }
        return events;
    }

    public static void extractPromotionAttributes(Promotion promotion, Map<String, String> attributes) {
        if (promotion != null) {
            if (!MPUtility.isEmpty(promotion.getId())) {
                attributes.put(Constants.Commerce.ATT_PROMOTION_ID, promotion.getId());
            }
            if (!MPUtility.isEmpty(promotion.getPosition())) {
                attributes.put(Constants.Commerce.ATT_PROMOTION_POSITION, promotion.getPosition());
            }
            if (!MPUtility.isEmpty(promotion.getName())) {
                attributes.put(Constants.Commerce.ATT_PROMOTION_NAME, promotion.getName());
            }
            if (!MPUtility.isEmpty(promotion.getCreative())) {
                attributes.put(Constants.Commerce.ATT_PROMOTION_CREATIVE, promotion.getCreative());
            }
        }
    }

    public static List<MPEvent> expandProductImpression(CommerceEvent event) {
        List<Impression> impressions = event.getImpressions();
        List<MPEvent> events = new LinkedList<MPEvent>();
        if (impressions == null) {
            return events;
        }
        for (int i = 0; i < impressions.size(); i++) {
            List<Product> products = impressions.get(i).getProducts();
            if (products != null) {
                for (int j = 0; j < products.size(); j++) {
                    MPEvent.Builder itemEvent = new MPEvent.Builder(IMPRESSION_NAME, MParticle.EventType.Transaction);
                    Map<String, String> attributes = new HashMap<String, String>();
                    if (event.getCustomAttributes() != null) {
                        attributes.putAll(event.getCustomAttributes());
                    }
                    extractProductAttributes(products.get(i), attributes);
                    extractProductFields(products.get(i), attributes);
                    extractImpressionAttributes(impressions.get(i), attributes);
                    events.add(itemEvent.info(attributes).build());
                }
            }
        }
        return events;
    }

    private static void extractImpressionAttributes(Impression impression, Map<String, String> attributes) {
        if (impression != null) {

            if (!MPUtility.isEmpty(impression.getListName())) {
                attributes.put("Product Impression List", impression.getListName());
            }
        }
    }

    public static void addCommerceEventInfo(MPMessage message, CommerceEvent event) {
        try {

            if (event.getScreen() != null) {
                message.put(Constants.Commerce.SCREEN_NAME, event.getScreen());
            }
            if (event.getNonInteraction() != null) {
                message.put(Constants.Commerce.NON_INTERACTION, event.getNonInteraction().booleanValue());
            }
            if (event.getCurrency() != null) {
                message.put(Constants.Commerce.CURRENCY, event.getCurrency());
            }
            if (event.getCustomAttributes() != null) {
                JSONObject attrs = new JSONObject();
                for (Map.Entry<String, String> entry : event.getCustomAttributes().entrySet()) {
                    attrs.put(entry.getKey(), entry.getValue());
                }
                message.put(Constants.Commerce.ATTRIBUTES, attrs);
            }
            if (event.getProductAction() != null) {
                JSONObject productAction = new JSONObject();
                message.put(Constants.Commerce.PRODUCT_ACTION_OBJECT, productAction);
                productAction.put(Constants.Commerce.PRODUCT_ACTION, event.getProductAction());
                if (event.getCheckoutStep() != null) {
                    productAction.put(Constants.Commerce.CHECKOUT_STEP, event.getCheckoutStep());
                }
                if (event.getCheckoutOptions() != null) {
                    productAction.put(Constants.Commerce.CHECKOUT_OPTIONS, event.getCheckoutOptions());
                }
                if (event.getProductListName() != null) {
                    productAction.put(Constants.Commerce.PRODUCT_LIST_NAME, event.getProductListName());
                }
                if (event.getProductListSource() != null) {
                    productAction.put(Constants.Commerce.PRODUCT_LIST_SOURCE, event.getProductListSource());
                }
                if (event.getTransactionAttributes() != null) {
                    TransactionAttributes transactionAttributes = event.getTransactionAttributes();
                    if (transactionAttributes.getId() != null) {
                        productAction.put(Constants.Commerce.TRANSACTION_ID, transactionAttributes.getId());
                    }
                    if (transactionAttributes.getAffiliation() != null) {
                        productAction.put(Constants.Commerce.TRANSACTION_AFFILIATION, transactionAttributes.getAffiliation());
                    }
                    if (transactionAttributes.getRevenue() != null) {
                        productAction.put(Constants.Commerce.TRANSACTION_REVENUE, transactionAttributes.getRevenue());
                    }
                    if (transactionAttributes.getTax() != null) {
                        productAction.put(Constants.Commerce.TRANSACTION_TAX, transactionAttributes.getTax());
                    }
                    if (transactionAttributes.getShipping() != null) {
                        productAction.put(Constants.Commerce.TRANSACTION_SHIPPING, transactionAttributes.getShipping());
                    }
                    if (transactionAttributes.getCouponCode() != null) {
                        productAction.put(Constants.Commerce.TRANSACTION_COUPON_CODE, transactionAttributes.getCouponCode());
                    }
                }
                if (event.getProducts() != null && event.getProducts().size() > 0) {
                    JSONArray products = new JSONArray();
                    for (int i = 0; i < event.getProducts().size(); i++) {
                        products.put(new JSONObject(event.getProducts().get(i).toString()));
                    }
                    productAction.put(Constants.Commerce.PRODUCT_LIST, products);
                }
            }
            if (event.getPromotionAction() != null) {
                JSONObject promotionAction = new JSONObject();
                message.put(Constants.Commerce.PROMOTION_ACTION_OBJECT, promotionAction);
                promotionAction.put(Constants.Commerce.PROMOTION_ACTION, event.getPromotionAction());
                if (event.getPromotions() != null && event.getPromotions().size() > 0) {
                    JSONArray promotions = new JSONArray();
                    for (int i = 0; i < event.getPromotions().size(); i++) {
                        promotions.put(getPromotionJson(event.getPromotions().get(i)));
                    }
                    promotionAction.put(Constants.Commerce.PROMOTION_LIST, promotions);
                }
            }
            if (event.getImpressions() != null && event.getImpressions().size() > 0) {
                JSONArray impressions = new JSONArray();
                for (Impression impression : event.getImpressions()) {
                    JSONObject impressionJson = new JSONObject();
                    if (impression.getListName() != null) {
                        impressionJson.put(Constants.Commerce.IMPRESSION_LOCATION, impression.getListName());
                    }
                    if (impression.getProducts() != null && impression.getProducts().size() > 0) {
                        JSONArray productsJson = new JSONArray();
                        impressionJson.put(Constants.Commerce.IMPRESSION_PRODUCT_LIST, productsJson);
                        for (Product product : impression.getProducts()) {
                            productsJson.put(new JSONObject(product.toString()));
                        }
                    }
                    if (impressionJson.length() > 0) {
                        impressions.put(impressionJson);
                    }
                }
                if (impressions.length() > 0) {
                    message.put(Constants.Commerce.IMPRESSION_OBJECT, impressions);
                }
            }
            JSONObject cartJsonObject = new JSONObject(MParticle.getInstance().Commerce().cart().toString());
            if (cartJsonObject.length() > 0) {
                message.put("sc", cartJsonObject);
            }

        } catch (Exception jse) {

        }
    }

    private static JSONObject getPromotionJson(Promotion promotion) {
        JSONObject json = new JSONObject();
        try {
            if (!MPUtility.isEmpty(promotion.getId())) {
                json.put("id", promotion.getId());
            }
            if (!MPUtility.isEmpty(promotion.getName())) {
                json.put("nm", promotion.getName());
            }
            if (!MPUtility.isEmpty(promotion.getCreative())) {
                json.put("cr", promotion.getCreative());
            }
            if (!MPUtility.isEmpty(promotion.getPosition())) {
                json.put("ps", promotion.getPosition());
            }
        } catch (JSONException jse) {

        }
        return json;
    }

    public static String getEventTypeString(CommerceEvent filteredEvent) {
        int eventType = getEventType(filteredEvent);
        switch (eventType) {
            case Constants.Commerce.EVENT_TYPE_ADD_TO_CART:
                return Constants.Commerce.EVENT_TYPE_STRING_ADD_TO_CART;
            case Constants.Commerce.EVENT_TYPE_REMOVE_FROM_CART:
                return Constants.Commerce.EVENT_TYPE_STRING_REMOVE_FROM_CART;
            case Constants.Commerce.EVENT_TYPE_CHECKOUT:
                return Constants.Commerce.EVENT_TYPE_STRING_CHECKOUT;
            case Constants.Commerce.EVENT_TYPE_CHECKOUT_OPTION:
                return Constants.Commerce.EVENT_TYPE_STRING_CHECKOUT_OPTION;
            case Constants.Commerce.EVENT_TYPE_CLICK:
                return Constants.Commerce.EVENT_TYPE_STRING_CLICK;
            case Constants.Commerce.EVENT_TYPE_VIEW_DETAIL:
                return Constants.Commerce.EVENT_TYPE_STRING_VIEW_DETAIL;
            case Constants.Commerce.EVENT_TYPE_PURCHASE:
                return Constants.Commerce.EVENT_TYPE_STRING_PURCHASE;
            case Constants.Commerce.EVENT_TYPE_REFUND:
                return Constants.Commerce.EVENT_TYPE_STRING_REFUND;
            case Constants.Commerce.EVENT_TYPE_ADD_TO_WISHLIST:
                return Constants.Commerce.EVENT_TYPE_STRING_ADD_TO_WISHLIST;
            case Constants.Commerce.EVENT_TYPE_REMOVE_FROM_WISHLIST:
                return Constants.Commerce.EVENT_TYPE_STRING_REMOVE_FROM_WISHLIST;
            case Constants.Commerce.EVENT_TYPE_PROMOTION_VIEW:
                return Constants.Commerce.EVENT_TYPE_STRING_PROMOTION_VIEW;
            case Constants.Commerce.EVENT_TYPE_PROMOTION_CLICK:
                return Constants.Commerce.EVENT_TYPE_STRING_PROMOTION_CLICK;
            case Constants.Commerce.EVENT_TYPE_IMPRESSION:
                return Constants.Commerce.EVENT_TYPE_STRING_IMPRESSION;

        }
        return Constants.Commerce.EVENT_TYPE_STRING_UNKNOWN;
    }

    public static int getEventType(CommerceEvent filteredEvent) {
        if (!MPUtility.isEmpty(filteredEvent.getProductAction())) {
            String action = filteredEvent.getProductAction();
            if (action.equalsIgnoreCase(Product.ADD_TO_CART)) {
                return Constants.Commerce.EVENT_TYPE_ADD_TO_CART;
            } else if (action.equalsIgnoreCase(Product.REMOVE_FROM_CART)) {
                return Constants.Commerce.EVENT_TYPE_REMOVE_FROM_CART;
            } else if (action.equalsIgnoreCase(Product.CHECKOUT)) {
                return Constants.Commerce.EVENT_TYPE_CHECKOUT;
            } else if (action.equalsIgnoreCase(Product.CHECKOUT_OPTION)) {
                return Constants.Commerce.EVENT_TYPE_CHECKOUT_OPTION;
            } else if (action.equalsIgnoreCase(Product.CLICK)) {
                return Constants.Commerce.EVENT_TYPE_CLICK;
            } else if (action.equalsIgnoreCase(Product.DETAIL)) {
                return Constants.Commerce.EVENT_TYPE_VIEW_DETAIL;
            } else if (action.equalsIgnoreCase(Product.PURCHASE)) {
                return Constants.Commerce.EVENT_TYPE_PURCHASE;
            } else if (action.equalsIgnoreCase(Product.REFUND)) {
                return Constants.Commerce.EVENT_TYPE_REFUND;
            } else if (action.equalsIgnoreCase(Product.ADD_TO_WISHLIST)) {
                return Constants.Commerce.EVENT_TYPE_ADD_TO_WISHLIST;
            } else if (action.equalsIgnoreCase(Product.REMOVE_FROM_WISHLIST)) {
                return Constants.Commerce.EVENT_TYPE_REMOVE_FROM_WISHLIST;
            }
        }
        if (!MPUtility.isEmpty(filteredEvent.getPromotionAction())) {
            String action = filteredEvent.getPromotionAction();
            if (action.equalsIgnoreCase(Promotion.VIEW)) {
                return Constants.Commerce.EVENT_TYPE_PROMOTION_VIEW;
            } else if (action.equalsIgnoreCase(Promotion.CLICK)) {
                return Constants.Commerce.EVENT_TYPE_PROMOTION_CLICK;
            }
        }
        return Constants.Commerce.EVENT_TYPE_IMPRESSION;
    }
}
