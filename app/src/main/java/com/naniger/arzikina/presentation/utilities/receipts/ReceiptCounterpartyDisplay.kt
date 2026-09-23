package com.naniger.arzikina.presentation.utilities.receipts

import android.content.Context
import com.naniger.arzikina.R
import com.naniger.arzikina.util.ReceiptCounterparty

/**
 * Description proposée pour une transaction créée depuis un reçu (« Transfert vers Ari Aoua »,
 * « Reçu de Abdoul Kader Bachir »), dans la langue COURANTE de l'interface.
 *
 * Construite ici, et non par [com.naniger.arzikina.util.ReceiptTransactionInfoParser] (Kotlin pur,
 * sans ressources) : le parseur ne fournit que la donnée structurée ([ReceiptCounterparty]).
 * Une fois la transaction enregistrée, la description devient une donnée utilisateur : elle n'est
 * plus jamais retraduite si la langue change ensuite.
 */
fun Context.receiptDescription(counterparty: ReceiptCounterparty): String = when (counterparty) {
    is ReceiptCounterparty.Recipient -> getString(R.string.receipt_description_transfer_to, counterparty.name)
    is ReceiptCounterparty.Sender -> getString(R.string.receipt_description_received_from, counterparty.name)
}
