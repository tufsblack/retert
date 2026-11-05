package nl.beneluxpvp;

/** Een stuk werk dat in kleine stapjes wordt uitgevoerd zodat de server niet hapert. */
interface Job {
    String name();

    /** Werkt door tot de tijd (System.nanoTime) om is. Geeft true terug als alles klaar is. */
    boolean step(long deadlineNanos) throws Exception;

    /** Wordt aangeroepen als de job klaar is of is mislukt. */
    void finish(boolean ok);
}
