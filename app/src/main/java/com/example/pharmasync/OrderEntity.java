package com.example.pharmasync;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_orders")
public class OrderEntity {
    @PrimaryKey
    @NonNull
    public String orderId;
    public String pharmacistId;
    public String pharmacistName;
    public String supplierId;
    public String medicineName;
    public String quantity;
    public String status;

    public OrderEntity(@NonNull String orderId, String pharmacistId, String pharmacistName, String supplierId, String medicineName, String quantity, String status) {
        this.orderId = orderId;
        this.pharmacistId = pharmacistId;
        this.pharmacistName = pharmacistName;
        this.supplierId = supplierId;
        this.medicineName = medicineName;
        this.quantity = quantity;
        this.status = status;
    }
}
