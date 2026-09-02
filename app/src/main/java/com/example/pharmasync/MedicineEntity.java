package com.example.pharmasync;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_medicines")
public class MedicineEntity {
    @PrimaryKey
    @NonNull
    public String medicineId;
    public String medicineName;
    public String description;
    public String pricePerUnit;
    public String stock;
    public String category;
    public String lowStock;
    public String userId;

    public MedicineEntity(@NonNull String medicineId, String medicineName, String description, String pricePerUnit, String stock, String category, String lowStock, String userId) {
        this.medicineId = medicineId;
        this.medicineName = medicineName;
        this.description = description;
        this.pricePerUnit = pricePerUnit;
        this.stock = stock;
        this.category = category;
        this.lowStock = lowStock;
        this.userId = userId;
    }
}
