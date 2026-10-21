package com.naniger.arzikina.domain.issuedreceipt

/** PDF d'un reçu prêt à être ouvert ou partagé : [uri] est une URI `content://` (FileProvider). */
data class IssuedReceiptPdfFile(val fileName: String, val uri: String)
