package com.example.pharmasync;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MedicineDao {
    @Query("SELECT * FROM local_medicines WHERE userId = :userId")
    List<MedicineEntity> getMedicinesForUser(String userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MedicineEntity> medicines);

    @Query("DELETE FROM local_medicines WHERE userId = :userId")
    void clearForUser(String userId);
}
