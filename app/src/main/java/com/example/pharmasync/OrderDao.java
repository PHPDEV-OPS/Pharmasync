package com.example.pharmasync;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface OrderDao {
    @Query("SELECT * FROM local_orders WHERE pharmacistId = :pharmacistId OR supplierId = :supplierId")
    List<OrderEntity> getOrdersForUser(String pharmacistId, String supplierId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<OrderEntity> orders);

    @Query("DELETE FROM local_orders")
    void clearAll();
}
