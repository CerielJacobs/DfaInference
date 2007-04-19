package DfaInference;

final class CountsMap {

    private static final int MIN_BUCKETS = 2;

    /**
     * Choose this value between 50 and 200.
     * It determines the amount of chaining
     * before the hashmap is resized. Lower value means less chaining, but
     * larger hashtable.
     */
    private static final int RESIZE_PERCENTAGE = 100;

    /** Maps handle to state. */
    private State[] dataBucket;

    private int[][] counts;

    /** Maps handle to next with same hash value. */
    private int[] nextBucket;

    /** Maps hash value to handle. */
    private int[] map;

    /** When to grow ... */
    private int sizeThreshold;

    /** Number of entries. */
    private int present;

    private static int[] hashCodes;

    private static int numdim1;
    private static int numdim2;

    public CountsMap() {
        sizeThreshold = (MIN_BUCKETS * RESIZE_PERCENTAGE) / 100;
        map = new int[MIN_BUCKETS];
        for (int i = 0; i < MIN_BUCKETS; i++) {
            map[i] = map.length;
        }
        nextBucket = new int[MIN_BUCKETS];
        counts = new int[MIN_BUCKETS][];
        dataBucket = new State[MIN_BUCKETS];
        present = 0;
    }

    public void clear() {
        /*
        if (dataBucket.length > 8 * MIN_BUCKETS) {
            sizeThreshold = (MIN_BUCKETS * RESIZE_PERCENTAGE) / 100;
            counts = null;
            map = null;
            nextBucket = null;
            dataBucket = null;

            map = new int[MIN_BUCKETS];
            for (int i = 0; i < MIN_BUCKETS; i++) {
                map[i] = map.length;
            }
            nextBucket = new int[MIN_BUCKETS];
            counts = new int[MIN_BUCKETS][];
            dataBucket = new State[MIN_BUCKETS];
        } else {
        */
            for (int i = 0; i < map.length; i++) {
                map[i] = map.length;
            }
        /*
        }
        */
        present = 0;
    }

    public static void initHash(int numStates, int nd2) {
        if (hashCodes == null) {
            hashCodes = new int[numStates];
            for (int i = 0; i < numStates; i++) {
                hashCodes[i] = getHashCode(i);
            }
            numdim1 = numStates;
            numdim2 = nd2;
        }
    }

    /**
     * We know size is a power of two. Make mod cheap.
     */
    private static final int mod(int x, int size) {
        return (x & (size - 1));
    }

    private static final int getHashCode(int h) {

        h += ~(h << 9);
        h ^=  (h >>> 14);
        h +=  (h << 4);
        h ^=  (h >>> 10);

        return h;
    }

    private final int getIndex(State ref) {
        int h = mod(hashCodes[ref.id], map.length);
        for (int i = map[h]; i != map.length; i = nextBucket[i]) {
            if (dataBucket[i] == ref) {
                return i;
            }
        }
        return -1;
    }

    public final int get(State ref, int len) {
        int i = getIndex(ref);
        if (i == -1) {
            return 0;
        }
        return counts[i][len];
    }

    private final void growMap() {

        map = new int[(map.length << 1)];
        for (int i = 0; i < map.length; i++) {
            map[i] = map.length;
        }

        sizeThreshold = (map.length * RESIZE_PERCENTAGE) / 100;

        for (int i = 0; i < present; i++) {
            int h;
            h = mod(hashCodes[dataBucket[i].id], map.length);

            nextBucket[i] = map[h];
            map[h] = i;
        }
    }

    private void growBuckets() {
        int newsize = (present << 1);
        int[] newNextBucket = new int[newsize];
        System.arraycopy(nextBucket, 0, newNextBucket, 0, present);
        nextBucket = newNextBucket;
        State[] newDataBucket = new State[newsize];
        System.arraycopy(dataBucket, 0, newDataBucket, 0, present);
        dataBucket = newDataBucket;
        int[][] newCounts = new int[newsize][];
        System.arraycopy(counts, 0, newCounts, 0, present);
        counts = newCounts;
    }
    
    private int newEntry(State ref) {
        if (present >= sizeThreshold) {
            growMap();
        }
        if (present >= dataBucket.length) {
            growBuckets();
        }
        int h = mod(hashCodes[ref.id], map.length);
        nextBucket[present] = map[h];
        map[h] = present;
        dataBucket[present] = ref;
        if (counts[present] == null) {
            counts[present] = new int[numdim2];
        } else {
            for (int j = 0; j < numdim2; j++) {
                counts[present][j] = 0;
            }
        }
        return present++;
    }

    public void put(State ref, int len, int val) {
        int i = getIndex(ref);
        if (i < 0) {
            i = newEntry(ref);
        }
        counts[i][len] = val;
    }

    public void add(State ref, int len, int val) {
        int i = getIndex(ref);
        if (i < 0) {
            i = newEntry(ref);
        }
        counts[i][len] += val;
    }

    public int size() {
        return present;
    }

    public State getState(int index) {
        return dataBucket[index];
    }

    public int getCount(int index, int len) {
        return counts[index][len];
    }
}
