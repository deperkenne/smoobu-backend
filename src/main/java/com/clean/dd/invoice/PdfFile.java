package com.clean.dd.invoice;

/** Fichier PDF prêt à être téléchargé. */
public record PdfFile(String fileName, byte[] content) {
}
