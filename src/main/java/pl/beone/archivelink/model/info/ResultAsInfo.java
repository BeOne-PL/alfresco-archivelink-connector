package pl.beone.archivelink.model.info;

public enum ResultAsInfo {
    ASCII("ascii"),
    HTML("html");

    private final String resultAsString;

    ResultAsInfo(String resultAsString) {
        this.resultAsString = resultAsString;
    }

    @Override
    public String toString() {
        return this.resultAsString;
    }

    public static ResultAsInfo fromString(String text) {
        if (text == null) {
            return ASCII;
        }

        for (var b : ResultAsInfo.values()) {
            if (b.resultAsString.equalsIgnoreCase(text)) {
                return b;
            }
        }

        throw new IllegalArgumentException("No constant with text " + text + " found");
    }
}
