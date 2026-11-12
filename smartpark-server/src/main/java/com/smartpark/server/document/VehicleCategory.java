package com.smartpark.server.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.EnumSet;
import java.util.Set;

/**
 * A vehicle category such as {@code CAR} or {@code MOTORCYCLE}.
 * <p>
 * {@code categoryName} is stored normalised (upper case, words joined by '_') and is
 * unique. {@code compatibleSlotTypes} lists the kinds of slot this category may use.
 */
@Document(collection = VehicleCategory.COLLECTION)
public class VehicleCategory {

    public static final String COLLECTION = "vehicleCategories";
    public static final String F_CATEGORY_NAME = "categoryName";

    @Id
    private String id;
    private String categoryName;
    private String description;
    private Set<SlotType> compatibleSlotTypes = EnumSet.noneOf(SlotType.class);

    public VehicleCategory() {
    }

    public VehicleCategory(String categoryName, String description, Set<SlotType> compatibleSlotTypes) {
        this.categoryName = categoryName;
        this.description = description;
        this.compatibleSlotTypes = compatibleSlotTypes.isEmpty()
                ? EnumSet.noneOf(SlotType.class) : EnumSet.copyOf(compatibleSlotTypes);
    }

    public boolean isCompatibleWith(SlotType slotType) {
        return compatibleSlotTypes != null && compatibleSlotTypes.contains(slotType);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Set<SlotType> getCompatibleSlotTypes() { return compatibleSlotTypes; }
    public void setCompatibleSlotTypes(Set<SlotType> compatibleSlotTypes) { this.compatibleSlotTypes = compatibleSlotTypes; }
}
