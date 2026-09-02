package com.example.pharmasync;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_suppliers")
public class SupplierEntity {
    @PrimaryKey
    @NonNull
    public String supplierId;
    public String name;
    public String contact;
    public String email;
    public String address;

    public SupplierEntity(@NonNull String supplierId, String name, String contact, String email, String address) {
        this.supplierId = supplierId;
        this.name = name;
        this.contact = contact;
        this.email = email;
        this.address = address;
    }
}
