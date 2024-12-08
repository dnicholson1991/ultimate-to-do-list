package com.customsolutions.android.utl;

/**
 * This represents a single in-app item available for purchase.
 */
public class InAppItem
{
    public String sku;
    public String title;
    public String description;
    public String long_description;
    public String price;  // Includes currency symbol.
    public boolean is_purchased;
}
