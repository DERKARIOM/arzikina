package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arzikina.ne.domain.model.AccountIcon

/**
 * Représentation Room d'un compte. Reste dans la couche data : le domaine
 * manipule uniquement [com.arzikina.ne.domain.model.Account] (voir
 * `data/mapper/AccountMapper`).
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val icon: AccountIcon,
    val colorArgb: Long,
    val currencyCode: String,
    val initialBalanceMinor: Long,
    val createdAt: Long
)
