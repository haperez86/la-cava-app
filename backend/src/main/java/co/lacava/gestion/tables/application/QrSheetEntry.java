package co.lacava.gestion.tables.application;

/** Una mesa en la hoja de QR imprimible (CA2): número, contenido del QR y PIN. Solo ADMIN. */
public record QrSheetEntry(int number, String code, String pin) {

    /** No expone el código ni el PIN. */
    @Override
    public String toString() {
        return "QrSheetEntry[number=" + number + "]";
    }
}
