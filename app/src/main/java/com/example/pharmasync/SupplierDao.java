package com.example.pharmasync;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SupplierDao {
    @Query("SELECT * FROM local_suppliers")
    List<SupplierEntity> getAllSuppliers();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<SupplierEntity> suppliers);

    @Query("DELETE FROM local_suppliers")
    void clearAll();
}
